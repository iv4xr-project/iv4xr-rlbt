package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.environment.Environment;
import burlap.mdp.singleagent.environment.EnvironmentOutcome;
import eu.fbk.iv4xr.minecraftlib.MinecraftAgent;
import eu.fbk.iv4xr.minecraftlib.MinecraftEnv;
import eu.fbk.iv4xr.minecraftlib.MinecraftGoalLib;
import eu.fbk.iv4xr.minecraftlib.StatusToWorldModel;
import eu.fbk.iv4xr.rlbt.configuration.MinecraftConfiguration;
import eu.iv4xr.framework.exception.Iv4xrError;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.mainConcepts.WorldModel;
import eu.iv4xr.framework.spatial.Vec3;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;

/** The level of the exploring agent as a BURLAP environment: it observes the live server,
 *  carries out an action on it and rebuilds the level between episodes. An episode ends when
 *  its budget of actions is spent. The reward pays for the coverage of the target states. */
class ExplorerEnvironment implements Environment {

	/** Reward of the first visit of a target state in an episode; the later ones are worth 0. */
	private static final double COVERAGE_BONUS = 1.0;

	/** Reward of an action that changes nothing: an incompatible one, a failed one, an error. */
	private static final double NULL_ACTION_REWARD = -0.1;

	/** The face a block is placed on: the top one, so it lands in the cell above the target. */
	private static final String PLACE_ON_FACE = "top";

	private final MinecraftEnv env;
	private final MinecraftAgent agent;
	private final MinecraftGoalLib goalLib = new MinecraftGoalLib();
	private final Map<String, Vec3> tagPositions;
	private final Map<String, String> uuidTags = new HashMap<>();
	private final TargetCoverage coverage;

	private final int maxTicksPerAction;
	private final int maxActionsPerEpisode;
	private final int waitTicksAfterAction;

	private final File transitions;
	private int episode = 1;
	private int steps;
	private double lastReward;
	private double episodeReward;
	private int episodeNullActions;

	/**
	 * @param agent        the agent, already attached to env and so logged into the server
	 * @param tagPositions the tagged positions of the level, already built
	 * @param domains      the domain of each block target: the states the reward pays for
	 * @param transitions  the CSV that gets one row per executed action
	 */
	ExplorerEnvironment(MinecraftEnv env, MinecraftAgent agent, Map<String, Vec3> tagPositions,
			Map<String, TargetDomain> domains, MinecraftConfiguration configuration, File transitions) {
		this.env = env;
		this.agent = agent;
		this.tagPositions = tagPositions;
		this.transitions = transitions;
		this.coverage = new TargetCoverage(domains);
		maxTicksPerAction = (int) configuration.getParameterValue("mine.max_ticks_per_action");
		maxActionsPerEpisode = (int) configuration.getParameterValue("mine.max_actions_per_episode");
		waitTicksAfterAction = (int) configuration.getParameterValue("mine.wait_ticks_after_action");
		env.tagUuids.forEach((tag, uuid) -> uuidTags.put(uuid, tag));
		write("episode,step,action,hand,approach,outcome,changed,reward,covered,changes", false);
	}

	int maxActionsPerEpisode() {
		return maxActionsPerEpisode;
	}

	@Override
	public State currentObservation() {
		return observe(env.observe(MineExplorer.AGENT_ID));
	}

	/** The observation reduced to what the exploration cares about: the agent (position, held
	 *  item, inventory), the block of each block target ("air" when there is none) without the
	 *  fixed properties, and the living entities. */
	ExplorerState observe(WorldModel wom) {
		WorldEntity self = wom.elements.get(MineExplorer.AGENT_ID);
		ExplorerState s = new ExplorerState(self.position,
				(String) self.properties.get(StatusToWorldModel.HELD_ITEM_PROP));
		Object inventory = self.properties.get(StatusToWorldModel.INVENTORY_PROP);
		if (inventory instanceof Map)
			((Map<?, ?>) inventory).forEach((item, count) ->
					s.inventory.put(String.valueOf(item), ((Number) count).floatValue()));

		for (Map.Entry<String, Vec3> tag : tagPositions.entrySet()) {
			if (uuidTags.containsValue(tag.getKey()))
				continue; // an entity, not a block
			WorldEntity we = MineExplorer.targetBlock(wom, tag.getKey(), tag.getValue());
			s.blockNames.put(tag.getKey(), we == null ? TargetDomain.AIR : we.type);
			s.blockProperties.put(tag.getKey(),
					we == null ? new TreeMap<>() : without(we.properties, MineExplorer.FIXED_PROPERTIES));
		}

		for (WorldEntity we : wom.elements.values()) {
			if (we.id.startsWith(StatusToWorldModel.BLOCK_ID_PREFIX) || we.id.equals(MineExplorer.AGENT_ID))
				continue;
			String tag = uuidTags.get(we.id);
			// untagged entities are kept only if they belong to the level (the minecart, a dropped
			// item): not other players, mobs dying from an earlier run or anything outside the level
			boolean dead = Float.valueOf(0f).equals(we.properties.get("health"));
			if (tag == null && (we.type.equals("player") || dead || !insideLevel(we.position)))
				continue;
			// horizontal speed only: an entity at rest still reports the pull of gravity
			boolean moving = we.velocity != null
					&& Math.hypot(we.velocity.x, we.velocity.z) > MineExplorer.MOVING_SPEED;
			s.entities.put(tag != null ? tag : we.type + "#" + we.properties.get("mcEntityId"),
					new ExplorerState.Entity(tag, we.type, we.position, moving,
							without(we.properties, MineExplorer.ENTITY_ID_PROPERTIES)));
		}
		// a tagged entity that is not observed any more (killed, or out of the entity radius)
		for (String tag : uuidTags.values())
			s.entities.putIfAbsent(tag, null);
		return s;
	}

	/** Whether a position is within the box of the tagged blocks, with a margin of 2 blocks
	 *  (4 upwards, for what stands or flies just above the level). */
	private boolean insideLevel(Vec3 p) {
		if (tagPositions.isEmpty())
			return true;
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (Vec3 t : tagPositions.values()) {
			minX = Math.min(minX, t.x); maxX = Math.max(maxX, t.x);
			minY = Math.min(minY, t.y); maxY = Math.max(maxY, t.y);
			minZ = Math.min(minZ, t.z); maxZ = Math.max(maxZ, t.z);
		}
		return p.x >= minX - 2 && p.x <= maxX + 3 && p.y >= minY - 2 && p.y <= maxY + 4
				&& p.z >= minZ - 2 && p.z <= maxZ + 3;
	}

	/** The properties, sorted and without the hidden ones. */
	private static Map<String, Object> without(Map<String, ?> properties, Set<String> hidden) {
		Map<String, Object> shown = new TreeMap<>(properties);
		shown.keySet().removeAll(hidden);
		return shown;
	}

	/** Carry out an action: observe, reach the target and act on it (a select has no target to
	 *  reach), wait for the effects that are not immediate, observe again. The reaching and
	 *  the action are tried one after the other even if the first fails. */
	@Override
	public EnvironmentOutcome executeAction(Action a) {
		ExplorerAction action = (ExplorerAction) a;
		ExplorerState before = (ExplorerState) currentObservation();
		if (steps == 0)
			coverage.startEpisode(before);

		String approach = "", outcome;
		if (action.verb.equals(MineExplorer.SELECT)) {
			outcome = run(goalLib.selected(action.argument.equals(MineExplorer.EMPTY_HAND) ? null : action.argument));
		} else {
			approach = run(goalLib.tagReached(action.argument));
			outcome = run(toGoal(action));
		}
		if (waitTicksAfterAction > 0)
			run(goalLib.waited(waitTicksAfterAction));

		ExplorerState after = (ExplorerState) currentObservation();
		List<String> changes = before.changes(after);
		steps++;
		StringBuilder visits = new StringBuilder();
		lastReward = reward(before, after, changes, visits);
		episodeReward += lastReward;

		System.out.println("[episode " + episode + " step " + steps + "] " + action.actionName()
				+ (approach.isEmpty() ? "" : " | approach " + approach) + " | action " + outcome
				+ " | reward " + lastReward + visits
				+ " | " + (changes.isEmpty() ? "no change" : "changes: " + String.join("; ", changes)));
		// the item held when the action started: what a change of a target has to be linked to
		String hand = before.heldItem == null ? MineExplorer.EMPTY_HAND : before.heldItem;
		write(episode + "," + steps + "," + action.actionName() + "," + hand + "," + approach + "," + quoted(outcome) + ","
				+ hasEffect(changes) + "," + lastReward + "," + coverage.covered() + ","
				+ quoted(String.join("; ", changes)), true);

		return new EnvironmentOutcome(before, action, after, lastReward, isInTerminalState());
	}

	/** The coverage reward of an action. Nothing changed (the position of the agent and the
	 *  untagged entities do not count): the null action reward. Otherwise, the bonus for every block target that
	 *  reached a state for the first time in the episode; a change of the
	 *  held item, the inventory or an entity alone is worth 0.
	 *  @param visits gets, for the print, the visit number of each state reached */
	private double reward(ExplorerState before, ExplorerState after, List<String> changes, StringBuilder visits) {
		if (!hasEffect(changes)) {
			episodeNullActions++;
			return NULL_ACTION_REWARD;
		}
		double reward = 0;
		for (String tag : after.blockNames.keySet()) {
			if (after.blockState(tag).equals(before.blockState(tag)))
				continue;
			int visit = coverage.visit(tag, after);
			if (visit == 1)
				reward += COVERAGE_BONUS;
			visits.append(" [").append(tag)
					.append(visit == 1 ? " new" : visit > 1 ? " visit " + visit : " outside the domain").append("]");
		}
		return reward;
	}

	/** Whether any of the changes can be an effect of the action. An untagged entity is left
	 *  out: it also changes on its own (a rolling minecart, a fading cloud), which would make
	 *  every action look effective while it lasts. */
	private boolean hasEffect(List<String> changes) {
		for (String change : changes) {
			String name = change.substring(0, change.indexOf(": "));
			if (!name.startsWith("entity:") || uuidTags.containsValue(name.substring("entity:".length())))
				return true;
		}
		return false;
	}

	/** The reward collected so far in the episode. */
	double episodeReward() {
		return episodeReward;
	}

	/** The actions without effect so far in the episode. */
	int episodeNullActions() {
		return episodeNullActions;
	}

	/** States covered so far in the episode, the ones it started in included. */
	int episodeCoverage() {
		return coverage.covered();
	}

	/** States covered so far in the session, by any episode. */
	int sessionCoverage() {
		return coverage.sessionCovered();
	}

	/** States of all the domains. */
	int totalStates() {
		return coverage.total();
	}

	/** Write the state of every domain with whether the session covered it. */
	void writeCoverage(File file) throws IOException {
		coverage.writeStates(file);
	}

	/** The session coverage in words, target by target. */
	String coverageSummary() {
		return coverage.summary();
	}

	/** Translate an action on a target into the goal that carries it out. */
	private GoalStructure toGoal(ExplorerAction action) {
		String target = action.argument;
		switch (action.verb) {
			case "clicked": return goalLib.clicked(target);
			case "placed": return goalLib.placed(target);
			case "placedOn": return goalLib.placedOn(target, PLACE_ON_FACE);
			case "attacked": return goalLib.attacked(target);
			case "usedOnEntity": return goalLib.usedOnEntity(target);
			default: throw new RuntimeException("Unknown action: " + action.actionName());
		}
	}

	/** Pursue a goal within the tick budget of an action.
	 *  @return the status of the goal, or the error of an action the testbench could not run:
	 *          for the agent it is one more action without effect */
	private String run(GoalStructure goal) {
		try {
			agent.setGoal(goal);
			int ticks = 0;
			while (goal.getStatus().inProgress() && ticks++ < maxTicksPerAction)
				agent.update();   // every update() performs an action, blocking server-side
			return goal.getStatus().success() ? "SUCCESS" : goal.getStatus().failed() ? "FAILED" : "INPROGRESS";
		} catch (Iv4xrError e) {
			return "ERROR " + e.getMessage();
		}
	}

	@Override
	public double lastReward() {
		return lastReward;
	}

	@Override
	public boolean isInTerminalState() {
		return steps >= maxActionsPerEpisode;
	}

	/** Start a new episode by rebuilding the level through the testbench. */
	@Override
	public void resetEnvironment() {
		env.resetAgent(MineExplorer.AGENT_ID);
		// the reset summons the entities again: they get new UUIDs
		uuidTags.clear();
		env.tagUuids.forEach((tag, uuid) -> uuidTags.put(uuid, tag));
		episode++;
		steps = 0;
		lastReward = 0;
		episodeReward = 0;
		episodeNullActions = 0;
	}

	private static String quoted(String text) {
		return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
	}

	private void write(String row, boolean append) {
		try (FileWriter w = new FileWriter(transitions, append)) {
			w.write(row + System.lineSeparator());
		} catch (IOException e) {
			throw new RuntimeException("Unable to write " + transitions, e);
		}
	}
}
