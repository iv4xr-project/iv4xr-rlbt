package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import burlap.behavior.singleagent.Episode;
import burlap.behavior.singleagent.learning.LearningAgent;
import burlap.behavior.singleagent.learning.tdmethods.QLearningStateNode;
import burlap.behavior.valuefunction.QValue;
import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.environment.EnvironmentOutcome;
import burlap.mdp.singleagent.SADomain;
import eu.fbk.iv4xr.minecraftlib.MinecraftAgent;
import eu.fbk.iv4xr.minecraftlib.MinecraftEnv;
import eu.fbk.iv4xr.minecraftlib.MinecraftState;
import eu.fbk.iv4xr.minecraftlib.StatusToWorldModel;
import eu.fbk.iv4xr.rlbt.DeepQLearningRL;
import eu.fbk.iv4xr.rlbt.QLearningRL;
import eu.fbk.iv4xr.rlbt.RlbtMain.BurlapAlgorithm;
import eu.fbk.iv4xr.rlbt.configuration.BurlapConfiguration;
import eu.fbk.iv4xr.rlbt.configuration.MinecraftConfiguration;
import eu.fbk.iv4xr.rlbt.distance.NoStateSimilarity;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftSampleModel;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.mainConcepts.WorldModel;
import eu.iv4xr.framework.spatial.Vec3;

/** Exploring agent activated from (game.mineAgentSystem=exploration).
 *  Specialized in coverage of the environment based on the available actions
 *  on the level. */
public class MineExplorer {

	static final String AGENT_ID = "Bot";
	public static final String REWARD_TYPE = "CoverageOriented";
	static final double MOVING_SPEED = 0.01;

	static MinecraftConfiguration mineConfiguration = new MinecraftConfiguration();
	static BurlapConfiguration burlapConfiguration = new BurlapConfiguration();
	static String defaultTestbenchUrl = "http://localhost:3000";
	static String defaultLevelCsv = "src/test/resources/minecraft-levels/the_big_level.csv";
	static String defaultBlockFamilies = "src/test/resources/configurations/block_families.json";
	static String currentDir = System.getProperty("user.dir");
	static String outputDir = currentDir + File.separator + "rlbt-files" + File.separator + "minecraft-results";
	public static long systemtime = System.nanoTime();
	static BurlapAlgorithm algorithm;
	static boolean testing;   // game.mode=testing: the learned Q-values are used, not updated
	static final Random RANDOM = new Random();

	// Target actions taken from the available in minecraftlib
	static final List<String> TARGET_ACTIONS = List.of("clicked", "placed", "placedOn", "attacked", "usedOnEntity");
	static final Set<String> CONSUMABLE_BLOCKS = Set.of("cake");
	static final List<String> EXTRA_ITEMS = List.of("bucket"); 	// Items that are not in the starting inventory but appear during an episode
	static final Set<String> FIXED_PROPERTIES = Set.of("facing", "half", "hinge", "face", "axis", "rotation", "shape", "type", "ominous"); 	// Block state properties fixed when the block is placed: left out of the coverage domain
	static final Set<String> ENTITY_ID_PROPERTIES = Set.of("name", "uuid", "mcEntityId");
	static final String SELECT = "select";
	static final String EMPTY_HAND = "empty";
	static final String PLACE_ON_PREFIX = "place_";
	static final String PLACE_ON_ACTION = "placedOn";
	static final long BUILD_POLL_MS = 500;   	// Wait for the built level: one observation every 500 ms, for 20 s at most
	static final long BUILD_TIMEOUT_MS = 20_000;

	public void explore(String testbenchUrl, String levelCsv) {
		System.out.println("-------------------------- Starting exploration on Minecraft ---------------------");
		System.out.println("Reward type: " + REWARD_TYPE + " (fixed for the exploration system)");

		MinecraftEnv env = new MinecraftEnv(testbenchUrl);
		MinecraftState state = new MinecraftState();
		MinecraftAgent agent = new MinecraftAgent(AGENT_ID, (String) mineConfiguration.getParameterValue("mine.address"));
		agent.setTestDataCollector(new TestDataCollector());
		agent.attachState(state).attachEnvironment(env); // Attaching the environment logs the bot into the server

		Map<String, Vec3> tagPositions;
		try {
			tagPositions = env.buildLevel(AGENT_ID, Files.readString(Path.of(levelCsv)), 0, 150, 0);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read the level " + levelCsv, e);
		}


		// Save all the targets of the level
		TreeSet<String> targets = new TreeSet<>(tagPositions.keySet()); // Every tag of the level (blocks and entities)
		targets.addAll(env.tagUuids.keySet());
		System.out.println("Targets found in the level (" + targets.size() + "): " + targets);

		// The domains and the select actions are built on this observation: level and inventory must be there
		WorldModel start = observeBuiltLevel(env, tagPositions, null);
		if (start == null)
			throw new RuntimeException("Level not ready " + BUILD_TIMEOUT_MS / 1000 + " s after building it (no block target"
					+ " or no item seen, or still changing): is the testbench running with the exploration config"
					+ " (scan radius of the whole level)?");
		Map<String, TargetDomain> domains = targetDomains(env, start, tagPositions,
				loadBlockFamilies(defaultBlockFamilies));
		printTargetDomains(domains);


		// The action list, in a fixed order
		List<ExplorerAction> actions = new ArrayList<>();
		for (String action : TARGET_ACTIONS)
			for (String target : targets)
				if (!target.startsWith(PLACE_ON_PREFIX) || action.equals(PLACE_ON_ACTION))
					actions.add(new ExplorerAction(action, target));
		int targetActions = actions.size();
		TreeSet<String> items = new TreeSet<>(EXTRA_ITEMS);
		Object inventory = start.elements.get(AGENT_ID).properties.get(StatusToWorldModel.INVENTORY_PROP);
		if (inventory instanceof Map)
			((Map<?, ?>) inventory).keySet().forEach(item -> items.add(String.valueOf(item)));
		for (String item : items)
			actions.add(new ExplorerAction(SELECT, item));
		actions.add(new ExplorerAction(SELECT, EMPTY_HAND));
		System.out.println("Actions generated: " + actions.size() + " = " + targetActions + " on the "
				+ targets.size() + " targets (" + TARGET_ACTIONS.size() + " actions each, only " + PLACE_ON_ACTION
				+ " on a " + PLACE_ON_PREFIX + " tag) + " + (items.size() + 1) + " " + SELECT + " " + items);


		// Write action list to outputDir
		File sessionDir = new File(outputDir);
		if (!sessionDir.exists() && !sessionDir.mkdirs())
			throw new RuntimeException("Unable to create output directory: " + sessionDir);
		writeActions(new File(sessionDir, "actions.csv"), actions, tagPositions);

		// Setup MineExplorer environment
		ExplorerEnvironment environment = new ExplorerEnvironment(env, agent, tagPositions, start, domains, mineConfiguration, new File(sessionDir, "transitions.csv"));
		ExplorerState seen = environment.observe(start);
		ExplorerStateEncoder encoder = new ExplorerStateEncoder(domains, new ArrayList<>(items), seen, tagPositions);
		System.out.println("State vector: " + encoder.describe() + "\n  features per label: " + encoder.sizes());
		printLearner(encoder.inputSize(), actions.size());
		System.out.println("[observation]" + "\n  sees:   " + seen.describe() + "\n  vector: " + encoder.active(encoder.features(seen)));

		// Create the learner (DQN or Q-table): trained here, or loaded for testing
		LearningAgent learner = createLearner(encoder, actions);
		if (testing)
			loadModel(learner, encoder, actions);
		run(learner, environment, encoder, actions, sessionDir);

		env.quit(agent);
	}

	/** The training (or testing) loop. An episode is run by the learner: it chooses an action
	 *  (epsilon-greedy), has the environment execute it and updates its Q-values (the DQN on a
	 *  batch of past transitions, the Q-table on the last one), until the budget of actions is spent.
	 *  Then the level is rebuilt. Every episode adds a row to episodeSummary.csv, refreshes the
	 *  session coverage (coverage.csv, summary.txt) and saves the network or the Q-table, so an
	 *  interrupted session keeps what it learned and measured. In testing nothing is learned
	 *  or saved: see testEpisode. */
	private static void run(LearningAgent learner, ExplorerEnvironment environment, ExplorerStateEncoder encoder,
			List<ExplorerAction> actionList, File sessionDir) {
		int episodes = (int) burlapConfiguration.getParameterValue(
				testing ? "burlap.testing.num_of_episodes" : "burlap.num_of_episodes");
		String networkFile = sessionDir + File.separator + "qnetwork.ser";
		DeepQLearningRL dqn = learner instanceof DeepQLearningRL ? (DeepQLearningRL) learner : null;
		QLearningRL qTable = learner instanceof QLearningRL ? (QLearningRL) learner : null;
		try (PrintStream summary = new PrintStream(new File(sessionDir, "episodeSummary.csv"))) {
			summary.println("episode,actions,total_reward,null_actions,states_covered,session_states_covered,"
					+ "states_total,epsilon,loss,time_ms");
			int executed = 0;
			long sessionTime = 0;
			for (int i = 1; i <= episodes; i++) {
				System.out.println("---------------- Episode " + i + "/" + episodes + " ----------------");
				double epsilon = testing ? (double) burlapConfiguration.getParameterValue("burlap.testing.epsilon")
						: dqn != null ? dqn.getEpsilongr() : qTable.getEpsilongr();   // the learner lowers it at the end of the episode
				long startTime = System.currentTimeMillis();
				Episode episode = testing ? testEpisode(learner, environment, encoder, actionList, epsilon)
						: learner.runLearningEpisode(environment, environment.maxActionsPerEpisode());
				long elapsed = System.currentTimeMillis() - startTime;

				// read before the reset, which starts the next episode and clears them
				String row = i + "," + episode.actionSequence.size() + "," + environment.episodeReward() + ","
						+ environment.episodeNullActions() + "," + environment.episodeCoverage() + ","
						+ environment.sessionCoverage() + "," + environment.totalStates() + "," + epsilon + ","
						+ (dqn != null && !testing ? dqn.getLastTrainingLoss() : "") + "," + elapsed;   // no loss for the Q-table
				summary.println(row);
				summary.flush();
				System.out.println("Episode " + i + ": reward " + environment.episodeReward()
						+ ", null actions " + environment.episodeNullActions() + "/" + episode.actionSequence.size()
						+ ", states covered " + environment.episodeCoverage() + "/" + environment.totalStates()
						+ " (session " + environment.sessionCoverage() + ")"
						+ ", epsilon " + epsilon + ", time " + elapsed + " ms"
						+ (qTable != null ? ", Q-table states " + qTable.getqFunction().size() : ""));
				executed += episode.actionSequence.size();
				sessionTime += elapsed;
				environment.writeCoverage(new File(sessionDir, "coverage.csv"));
				try (PrintStream text = new PrintStream(new File(sessionDir, "summary.txt"))) {
					text.println("Episodes run: " + i + "/" + episodes + ", actions executed: " + executed
							+ ", time: " + sessionTime / 1000 + " s");
					if (testing)
						text.println("Testing the model of " + burlapConfiguration.getParameterValue("burlap.testing.model")
								+ ", epsilon " + epsilon);
					text.println(environment.coverageSummary());
				}
				// Save what was learned (nothing in testing)
				if (!testing && dqn != null)
					dqn.serializeModel(networkFile);
				else if (!testing)
					writeQTable(qTable, encoder, new File(sessionDir, "qtable.csv"));
				environment.resetEnvironment();
			}
			if (dqn != null && !testing)
				dqn.printNetworkSummary(new PrintStream(networkFile + ".txt"));
		} catch (IOException e) {
			throw new RuntimeException("Unable to write the session to " + sessionDir, e);
		}
		System.out.println(environment.coverageSummary());
		System.out.println("Session written to: " + sessionDir);
	}

	/** One testing episode: the learned Q-values choose, nothing is updated. A random action
	 *  with probability epsilon or when the Q-table has never seen the state, otherwise the
	 *  best one (random among equals). */
	private static Episode testEpisode(LearningAgent learner, ExplorerEnvironment environment,
			ExplorerStateEncoder encoder, List<ExplorerAction> actions, double epsilon) {
		EncodedStateHashFactory hash = new EncodedStateHashFactory(encoder);
		State state = environment.currentObservation();
		Episode episode = new Episode(state);
		while (!environment.isInTerminalState()) {
			List<QValue> qs = null;
			if (learner instanceof DeepQLearningRL)
				qs = ((DeepQLearningRL) learner).qValues(state);
			else {
				QLearningStateNode node = ((QLearningRL) learner).getqFunction().get(hash.hashState(state));
				if (node != null)
					qs = node.qEntry;
			}

			Action action;
			if (qs == null || RANDOM.nextDouble() < epsilon)
				action = actions.get(RANDOM.nextInt(actions.size()));
			else {
				double max = Double.NEGATIVE_INFINITY;
				List<Action> best = new ArrayList<>();
				for (QValue q : qs) {
					if (q.q > max) {
						max = q.q;
						best.clear();
					}
					if (q.q == max)
						best.add(q.a);
				}
				action = best.get(RANDOM.nextInt(best.size()));
			}
			System.out.println("[testing] action: " + action.actionName() + (qs == null ? " (state not in the Q-table)" : ""));

			EnvironmentOutcome eo = environment.executeAction(action);
			episode.transition(action, eo.op, eo.r);
			state = eo.op;
		}
		return episode;
	}

	/** Load what a training session learned, from burlap.testing.model (the session directory). */
	private static void loadModel(LearningAgent learner, ExplorerStateEncoder encoder, List<ExplorerAction> actions) {
		String dir = (String) burlapConfiguration.getParameterValue("burlap.testing.model");
		File file = new File(dir, learner instanceof DeepQLearningRL ? "qnetwork.ser" : "qtable.csv");
		if (dir.isEmpty() || !file.isFile())
			throw new RuntimeException("Testing needs burlap.testing.model, the directory of a training session: "
					+ file + " not found");
		System.out.println("Loading the model: " + file);
		if (learner instanceof DeepQLearningRL)
			((DeepQLearningRL) learner).deserializeModel(file.getPath());
		else
			readQTable((QLearningRL) learner, encoder, actions, file);
	}

	/** The block observed for a tag, null when the cell is empty: the one at the tag position,
	 *  or the one above it for a tag that marks the block to place on. */
	static WorldEntity targetBlock(WorldModel wom, String tag, Vec3 p) {
		int y = Math.round(p.y) + (tag.startsWith(PLACE_ON_PREFIX) ? 1 : 0);
		return wom.elements.get(StatusToWorldModel.BLOCK_ID_PREFIX + Math.round(p.x) + "_" + y + "_" + Math.round(p.z));
	}

	/** Observe the level once it is built. The testbench answers a build or a reset after a fixed
	 *  wait, while its /setblock and /item commands may still be on their way: observe until at
	 *  least one block target is seen, the inventory is not empty (the select actions come from it)
	 *  and the level is the same in two observations in a row or, after a reset, the same as at the
	 *  start of the session (expected). Null if that does not happen within BUILD_TIMEOUT_MS. */
	static WorldModel observeBuiltLevel(MinecraftEnv env, Map<String, Vec3> tagPositions, Map<String, Object> expected) {
		long deadline = System.currentTimeMillis() + BUILD_TIMEOUT_MS;
		Map<String, Object> previous = null;
		while (true) {
			WorldModel wom = env.observe(AGENT_ID);
			Map<String, Object> snapshot = levelSnapshot(env, wom, tagPositions);
			if (snapshot != null && snapshot.equals(expected != null ? expected : previous))
				return wom;
			if (System.currentTimeMillis() > deadline)
				return null;
			previous = snapshot;
			try {
				Thread.sleep(BUILD_POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted while waiting for the level", e);
			}
		}
	}

	/** The block of each block target (name and dynamic properties) and the inventory counts;
	 *  null when no block target or no item is seen yet. */
	static Map<String, Object> levelSnapshot(MinecraftEnv env, WorldModel wom, Map<String, Vec3> tagPositions) {
		Map<String, Object> snapshot = new TreeMap<>();
		boolean seen = false;
		for (Map.Entry<String, Vec3> tag : tagPositions.entrySet())
			if (!env.tagUuids.containsKey(tag.getKey())) {
				WorldEntity we = targetBlock(wom, tag.getKey(), tag.getValue());
				if (we == null) {
					snapshot.put("block:" + tag.getKey(), TargetDomain.AIR);
					continue;
				}
				Map<String, Object> properties = new TreeMap<>(we.properties);
				properties.keySet().removeAll(FIXED_PROPERTIES);
				snapshot.put("block:" + tag.getKey(), we.type + properties);
				seen = true;
			}
		Object inventory = wom.elements.get(AGENT_ID).properties.get(StatusToWorldModel.INVENTORY_PROP);
		if (!seen || !(inventory instanceof Map) || ((Map<?, ?>) inventory).isEmpty())
			return null;
		((Map<?, ?>) inventory).forEach((item, count) -> snapshot.put("inv:" + item, count));
		return snapshot;
	}

	/** The block families file as block name -> the names of its family: the names a block can
	 *  turn into (a cauldron once filled, an anvil once damaged). Valid for every level. */
	static Map<String, List<String>> loadBlockFamilies(String file) {
		JsonObject json;
		try {
			json = JsonParser.parseString(Files.readString(Path.of(file))).getAsJsonObject();
		} catch (IOException e) {
			throw new RuntimeException("Unable to read the block families " + file, e);
		}
		Map<String, List<String>> families = new HashMap<>();
		for (String family : json.keySet()) {
			List<String> names = new ArrayList<>();
			for (JsonElement name : json.getAsJsonArray(family))
				names.add(name.getAsString());
			for (String name : names)
				if (families.put(name, names) != null)
					throw new RuntimeException("Block " + name + " is in more than one family of " + file);
		}
		return families;
	}

	/** The coverage domain of each block target: the names of the family of its block (the block
	 *  alone when it has no family), each with its state properties (asked to the testbench)
	 *  without the fixed ones. An empty cell takes the family of air: the blocks that can be
	 *  placed in it. Air itself is in the domain of an empty cell and of a consumable block
	 *  only: nothing else can disappear, the agent does not mine and mobs do not grief. */
	private static Map<String, TargetDomain> targetDomains(MinecraftEnv env, WorldModel wom,
			Map<String, Vec3> tagPositions, Map<String, List<String>> families) {
		Map<String, Map<String, List<Object>>> propertiesByBlock = new HashMap<>(); // one request per block type
		Map<String, TargetDomain> domains = new TreeMap<>();
		for (Map.Entry<String, Vec3> tag : tagPositions.entrySet()) {
			if (env.tagUuids.containsKey(tag.getKey()))
				continue; // an entity, not a block
			WorldEntity we = targetBlock(wom, tag.getKey(), tag.getValue());
			String current = we == null ? TargetDomain.AIR : we.type;
			TargetDomain domain = new TargetDomain(tag.getKey());
			List<String> family = families.getOrDefault(current, List.of(current));
			for (String name : family) {
				if (name.equals(TargetDomain.AIR))
					continue;
				Map<String, List<Object>> properties = new LinkedHashMap<>(
						propertiesByBlock.computeIfAbsent(name, block -> env.getBlockProperties(AGENT_ID, block)));
				properties.keySet().removeAll(FIXED_PROPERTIES);
				domain.names.put(name, properties);
			}
			if (we == null || family.stream().anyMatch(CONSUMABLE_BLOCKS::contains))
				domain.names.put(TargetDomain.AIR, Map.of());
			domains.put(tag.getKey(), domain);
		}
		return domains;
	}

	private static void printTargetDomains(Map<String, TargetDomain> domains) {
		System.out.println("Target domains (fixed properties left out: " + new TreeSet<>(FIXED_PROPERTIES) + "):");
		int total = 0;
		for (TargetDomain domain : domains.values()) {
			total += domain.states();
			System.out.println("  " + domain.tag + ": " + domain);
		}
		System.out.println("Total states over the " + domains.size() + " block targets: " + total);
	}

	/** Print the learner the BURLAP config asks for and its parameters. */
	private static void printLearner(int inputs, int outputs) {
		String learner = algorithm == BurlapAlgorithm.DeepQLearning
				? "network " + inputs + " -> " + burlapConfiguration.getParameterValue("burlap.network.hidden_size")
						+ " -> " + burlapConfiguration.getParameterValue("burlap.network.hidden_size") + " -> " + outputs
				: "Q-table on the " + inputs + " features, " + outputs + " actions per state";
		String parameters = algorithm == BurlapAlgorithm.DeepQLearning
				? " lr=" + burlapConfiguration.getParameterValue("burlap.qlearning.dqn_lr")
				: " lr=" + burlapConfiguration.getParameterValue("burlap.qlearning.lr")
						+ " qinit=" + burlapConfiguration.getParameterValue("burlap.qlearning.qinit");
		System.out.println("Learner: " + algorithm + ", " + learner
				+ "\n  episodes=" + burlapConfiguration.getParameterValue("burlap.num_of_episodes")
				+ " gamma=" + burlapConfiguration.getParameterValue("burlap.qlearning.gamma")
				+ parameters
				+ " epsilon=" + burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval")
				+ " -> " + burlapConfiguration.getParameterValue("burlap.qlearning.epsilonmin")
				+ " by " + burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep") + " per episode");
		if (algorithm == BurlapAlgorithm.DeepQLearning)
			System.out.println("  replay buffer=" + burlapConfiguration.getParameterValue("burlap.network.replay_buffer_capacity")
					+ " batch=" + burlapConfiguration.getParameterValue("burlap.network.batch_size")
					+ " min replay=" + burlapConfiguration.getParameterValue("burlap.network.min_replay_size")
					+ " target update=" + burlapConfiguration.getParameterValue("burlap.network.target_update_frequency"));
	}

	/** The learner of the BURLAP config. Its Q-values follow the order of the action list; the
	 *  domain only carries the actions, since the transitions come from the live server. */
	private static LearningAgent createLearner(ExplorerStateEncoder encoder, List<ExplorerAction> actions) {
		SADomain domain = new SADomain();
		domain.addActionType(new ExplorerActionType(actions));
		domain.setModel(new MinecraftSampleModel());
		int maxActions = (int) mineConfiguration.getParameterValue("mine.max_actions_per_episode");

		if (algorithm == BurlapAlgorithm.QLearning) {
			// Same encoding as the DQN: one row per distinct state vector
			QLearningRL learner = new QLearningRL(domain,
					(double) burlapConfiguration.getParameterValue("burlap.qlearning.gamma"),
					new EncodedStateHashFactory(encoder),
					(double) burlapConfiguration.getParameterValue("burlap.qlearning.qinit"),
					(double) burlapConfiguration.getParameterValue("burlap.qlearning.lr"),
					(double) burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval"),
					(double) burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep"),
					maxActions,
					new NoStateSimilarity());
			learner.setEpsilonMin((double) burlapConfiguration.getParameterValue("burlap.qlearning.epsilonmin"));
			return learner;
		}

		List<String> actionNames = new ArrayList<>();
		for (ExplorerAction action : actions)
			actionNames.add(action.actionName());
		DeepQLearningRL learner = new DeepQLearningRL(domain,
				(double) burlapConfiguration.getParameterValue("burlap.qlearning.gamma"),
				encoder,
				actionNames,
				(double) burlapConfiguration.getParameterValue("burlap.qlearning.dqn_lr"),
				(double) burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval"),
				(double) burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep"),
				maxActions,
				(double) burlapConfiguration.getParameterValue("burlap.qlearning.epsilonmin"),
				(int) burlapConfiguration.getParameterValue("burlap.network.hidden_size"));
		learner.setReplayBufferCapacity((int) burlapConfiguration.getParameterValue("burlap.network.replay_buffer_capacity"));
		learner.setBatchSize((int) burlapConfiguration.getParameterValue("burlap.network.batch_size"));
		learner.setMinReplaySize((int) burlapConfiguration.getParameterValue("burlap.network.min_replay_size"));
		learner.setTargetUpdateFrequency((int) burlapConfiguration.getParameterValue("burlap.network.target_update_frequency"));
		return learner;
	}

	/** One row per state of the Q-table: the non-zero features, then the Q-value of each action. */
	private static void writeQTable(QLearningRL learner, ExplorerStateEncoder encoder, File file) {
		try (PrintStream w = new PrintStream(file)) {
			boolean header = true;
			for (QLearningStateNode node : learner.getqFunction().values()) {
				if (header) {
					w.print("state");
					for (QValue q : node.qEntry)
						w.print("," + q.a.actionName());
					w.println();
					header = false;
				}
				w.print("\"" + encoder.active(((EncodedStateHashFactory.EncodedState) node.s).features) + "\"");
				for (QValue q : node.qEntry)
					w.print("," + q.q);
				w.println();
			}
		} catch (IOException e) {
			throw new RuntimeException("Unable to write " + file, e);
		}
	}

	/** The Q-table written by writeQTable. It must come from the same level: the states are
	 *  rebuilt from the feature labels of the encoder. */
	private static void readQTable(QLearningRL learner, ExplorerStateEncoder encoder, List<ExplorerAction> actions,
			File file) {
		Map<String, ExplorerAction> byName = new HashMap<>();
		for (ExplorerAction action : actions)
			byName.put(action.actionName(), action);
		try {
			List<String> lines = Files.readAllLines(file.toPath());
			String[] header = lines.get(0).split(",");
			for (String line : lines.subList(1, lines.size())) {
				int close = line.indexOf('"', 1);   // the state is the quoted first column
				String[] qs = line.substring(close + 2).split(",");
				EncodedStateHashFactory.EncodedState s = new EncodedStateHashFactory.EncodedState(null,
						encoder.vector(line.substring(1, close)));
				QLearningStateNode node = new QLearningStateNode(s);
				for (int i = 0; i < qs.length; i++) {
					ExplorerAction action = byName.get(header[i + 1]);
					if (action == null)
						throw new RuntimeException("Action " + header[i + 1] + " of " + file + " is not in this level");
					node.addQValue(action, Double.parseDouble(qs[i]));
				}
				learner.getqFunction().put(s, node);
			}
		} catch (IOException e) {
			throw new RuntimeException("Unable to read " + file, e);
		}
		System.out.println("Q-table loaded: " + learner.getqFunction().size() + " states");
	}

	/** One row per action, in the order of the list: the index is the one the learner will use.
	 *  The argument is a target tag, or an item for select; the position is the one of the tag. */
	private static void writeActions(File file, List<ExplorerAction> actions, Map<String, Vec3> tagPositions) {
		String nl = System.lineSeparator();
		try (FileWriter w = new FileWriter(file)) {
			w.write("index,name,action,argument,position" + nl);
			for (int i = 0; i < actions.size(); i++) {
				ExplorerAction a = actions.get(i);
				Vec3 pos = a.verb.equals(SELECT) ? null : tagPositions.get(a.argument);
				w.write(i + "," + a.actionName() + "," + a.verb + "," + a.argument + ","
						+ (pos == null ? "" : "\"" + pos + "\"") + nl);
			}
			System.out.println("Actions written to: " + file);
		} catch (IOException e) {
			throw new RuntimeException("Unable to write " + file, e);
		}
	}

	/**
	 * @param args [0] = testbench URL (default localhost:3000),
	 *             [1] = level csv path (default the big level),
	 *             [2] = mode (trainingMode or testingMode),
	 *             [3] = BURLAP config file (the algorithm and its parameters),
	 *             [4] = Minecraft SUT config file, i.e. mineAgent.config
	 */
	public static void main(String[] args) {

		// Save all passed arguments
		String testbenchUrl = args.length > 0 ? args[0] : defaultTestbenchUrl;
		String levelCsv = args.length > 1 ? args[1] : defaultLevelCsv;

		if (args.length > 3 && args[3] != null) {
			System.out.println("Loading BURLAP configuration: " + args[3]);
			if (!burlapConfiguration.updateParameters(args[3]))
				throw new RuntimeException("Cannot load BURLAP configuration " + args[3]);
		}
		if (args.length > 4 && args[4] != null) {
			System.out.println("Loading Minecraft configuration: " + args[4]);
			if (!mineConfiguration.updateParameters(args[4]))
				throw new RuntimeException("Cannot load Minecraft configuration " + args[4]);
		}

		String mode = args.length > 2 ? args[2] : "trainingMode";
		if (!mode.equals("trainingMode") && !mode.equals("testingMode"))
			throw new RuntimeException("Mode " + mode + " not supported by the exploration system");
		testing = mode.equals("testingMode");

		String name = (String) burlapConfiguration.getParameterValue("burlap.algorithm");
		for (BurlapAlgorithm a : BurlapAlgorithm.values())
			if (a.toString().equalsIgnoreCase(name))
				algorithm = a;
		if (algorithm == null)
			throw new RuntimeException("Algorithm " + name + " not supported by the exploration system");

		String levelName = new File(levelCsv).getName().replaceFirst("\\.csv$", "");
		outputDir = outputDir + File.separator + levelName + File.separator + "exploration" + File.separator
				+ algorithm + File.separator + (testing ? "testing" + File.separator : "") + systemtime;
		// outputDir: rlbt-files/minecraft-results/<level>/exploration/<algorithm>/[testing/]<systemtime>

		new MineExplorer().explore(testbenchUrl, levelCsv);
	}
}
