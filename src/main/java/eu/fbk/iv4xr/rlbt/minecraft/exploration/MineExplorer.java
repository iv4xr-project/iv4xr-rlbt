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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import burlap.behavior.singleagent.Episode;
import burlap.mdp.singleagent.SADomain;
import eu.fbk.iv4xr.minecraftlib.MinecraftAgent;
import eu.fbk.iv4xr.minecraftlib.MinecraftEnv;
import eu.fbk.iv4xr.minecraftlib.MinecraftState;
import eu.fbk.iv4xr.minecraftlib.StatusToWorldModel;
import eu.fbk.iv4xr.rlbt.DeepQLearningRL;
import eu.fbk.iv4xr.rlbt.configuration.BurlapConfiguration;
import eu.fbk.iv4xr.rlbt.configuration.MinecraftConfiguration;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftSampleModel;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.mainConcepts.WorldModel;
import eu.iv4xr.framework.spatial.Vec3;

/** Exploring agent activated from (game.mineAgentSystem=exploration).
 *  Specialized in coverage of the environment based on the available actions
 *  on the level. */
public class MineExplorer {
	static String defaultTestbenchUrl = "http://localhost:3000";
	static String defaultLevelCsv = "src/test/resources/minecraft-levels/the_big_level.csv";
	static String defaultBlockFamilies = "src/test/resources/configurations/block_families.json";
	static String currentDir = System.getProperty("user.dir");
	static String outputDir = currentDir + File.separator + "rlbt-files" + File.separator + "minecraft-results";
	public static long systemtime = System.nanoTime();

	static MinecraftConfiguration mineConfiguration = new MinecraftConfiguration();
	static BurlapConfiguration burlapConfiguration = new BurlapConfiguration();

	static final String AGENT_ID = "Bot";
	public static final String REWARD_TYPE = "CoverageOriented";

	/** Actions of MinecraftGoalLib whose only parameter is a target tag. Reaching the target is
	 *  not one of them: it is the first step of each. Mining is left out on purpose: a broken
	 *  target is lost for the rest of the episode. */
	static final List<String> TARGET_ACTIONS = List.of(
			"clicked", "placed", "placedOn", "attacked", "usedOnEntity");

	static final String SELECT = "select";
	static final String EMPTY_HAND = "empty";

	/** Items that are not in the starting inventory but appear during an episode, to select too. */
	static final List<String> EXTRA_ITEMS = List.of("bucket");

	/** A tag with this prefix marks the block to place on: its target is the cell above it. */
	static final String PLACE_ON_PREFIX = "place_";

	/** Block state properties fixed when the block is placed: no action changes them, so
	 *  they are left out of the coverage domain. */
	static final Set<String> FIXED_PROPERTIES = Set.of(
			"facing", "half", "hinge", "face", "axis", "rotation", "shape", "type", "ominous");

	/** Entity properties that only identify the entity: left out of its observed state. */
	static final Set<String> ENTITY_ID_PROPERTIES = Set.of("name", "uuid", "mcEntityId");

	/** Horizontal speed, in blocks per tick, above which an entity counts as moving. */
	static final double MOVING_SPEED = 0.01;

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

		// Targets: every tag of the level, blocks and entities
		TreeSet<String> targets = new TreeSet<>(tagPositions.keySet());
		targets.addAll(env.tagUuids.keySet());
		System.out.println("Targets found (" + targets.size() + "): " + targets);
		WorldModel start = env.observe(AGENT_ID);
		Map<String, TargetDomain> domains = targetDomains(env, start, tagPositions,
				loadBlockFamilies(defaultBlockFamilies));
		printTargetDomains(domains);

		// The action list, in a fixed order: every (action, target) pair, incompatible ones
		// included, then one select per item and one for the empty hand
		List<ExplorerAction> actions = new ArrayList<>();
		for (String action : TARGET_ACTIONS)
			for (String target : targets)
				actions.add(new ExplorerAction(action, target));
		TreeSet<String> items = new TreeSet<>(EXTRA_ITEMS);
		Object inventory = start.elements.get(AGENT_ID).properties.get(StatusToWorldModel.INVENTORY_PROP);
		if (inventory instanceof Map)
			((Map<?, ?>) inventory).keySet().forEach(item -> items.add(String.valueOf(item)));
		for (String item : items)
			actions.add(new ExplorerAction(SELECT, item));
		actions.add(new ExplorerAction(SELECT, EMPTY_HAND));
		System.out.println("Actions generated: " + actions.size() + " = " + TARGET_ACTIONS.size() + " actions x "
				+ targets.size() + " targets + " + (items.size() + 1) + " " + SELECT + " " + items);

		// minecraft-results/<level>/exploration/<systemtime>
		File sessionDir = new File(outputDir);
		if (!sessionDir.exists() && !sessionDir.mkdirs())
			throw new RuntimeException("Unable to create output directory: " + sessionDir);
		writeActions(new File(sessionDir, "actions.csv"), actions, tagPositions);
		ExplorerEnvironment environment = new ExplorerEnvironment(env, agent, tagPositions, domains, mineConfiguration,
				new File(sessionDir, "transitions.csv"));

		ExplorerState seen = environment.observe(start);
		ExplorerStateEncoder encoder = new ExplorerStateEncoder(domains, new ArrayList<>(items), seen, tagPositions);
		System.out.println("State vector: " + encoder.describe() + "\n  features per label: " + encoder.sizes());
		printLearner(encoder.inputSize(), actions.size());
		DeepQLearningRL learner = createLearner(encoder, actions);

		System.out.println("[observation]"
				+ "\n  sees:   " + seen.describe()
				+ "\n  vector: " + encoder.active(encoder.features(seen)));

		train(learner, environment, sessionDir);
		env.quit(agent);
	}

	/** The training loop. An episode is run by the learner: it chooses an action
	 *  (epsilon-greedy), has the environment execute it, stores the transition with its reward
	 *  and trains the network on a batch of past ones, until the budget of actions is spent.
	 *  Then the level is rebuilt. Every episode adds a row to episodeSummary.csv, refreshes the
	 *  session coverage (coverage.csv, summary.txt) and saves the network, so an interrupted
	 *  session keeps what it learned and measured. */
	private static void train(DeepQLearningRL learner, ExplorerEnvironment environment, File sessionDir) {
		int episodes = (int) burlapConfiguration.getParameterValue("burlap.num_of_episodes");
		String networkFile = sessionDir + File.separator + "qnetwork.ser";
		try (PrintStream summary = new PrintStream(new File(sessionDir, "episodeSummary.csv"))) {
			summary.println("episode,actions,total_reward,null_actions,states_covered,session_states_covered,"
					+ "states_total,epsilon,loss,time_ms");
			int actions = 0;
			long sessionTime = 0;
			for (int i = 1; i <= episodes; i++) {
				System.out.println("---------------- Episode " + i + "/" + episodes + " ----------------");
				double epsilon = learner.getEpsilongr();   // the learner lowers it at the end of the episode
				long startTime = System.currentTimeMillis();
				Episode episode = learner.runLearningEpisode(environment, environment.maxActionsPerEpisode());
				long elapsed = System.currentTimeMillis() - startTime;

				// read before the reset, which starts the next episode and clears them
				String row = i + "," + episode.actionSequence.size() + "," + environment.episodeReward() + ","
						+ environment.episodeNullActions() + "," + environment.episodeCoverage() + ","
						+ environment.sessionCoverage() + "," + environment.totalStates() + "," + epsilon + ","
						+ learner.getLastTrainingLoss() + "," + elapsed;
				summary.println(row);
				summary.flush();
				System.out.println("Episode " + i + ": reward " + environment.episodeReward()
						+ ", null actions " + environment.episodeNullActions() + "/" + episode.actionSequence.size()
						+ ", states covered " + environment.episodeCoverage() + "/" + environment.totalStates()
						+ " (session " + environment.sessionCoverage() + ")"
						+ ", epsilon " + epsilon + ", time " + elapsed + " ms");
				actions += episode.actionSequence.size();
				sessionTime += elapsed;
				environment.writeCoverage(new File(sessionDir, "coverage.csv"));
				try (PrintStream text = new PrintStream(new File(sessionDir, "summary.txt"))) {
					text.println("Episodes run: " + i + "/" + episodes + ", actions executed: " + actions
							+ ", time: " + sessionTime / 1000 + " s");
					text.println(environment.coverageSummary());
				}
				learner.serializeModel(networkFile);
				environment.resetEnvironment();
			}
			learner.printNetworkSummary(new PrintStream(networkFile + ".txt"));
		} catch (IOException e) {
			throw new RuntimeException("Unable to write the session to " + sessionDir, e);
		}
		System.out.println(environment.coverageSummary());
		System.out.println("Session written to: " + sessionDir);
	}

	/** The block observed for a tag, null when the cell is empty: the one at the tag position,
	 *  or the one above it for a tag that marks the block to place on. */
	static WorldEntity targetBlock(WorldModel wom, String tag, Vec3 p) {
		int y = Math.round(p.y) + (tag.startsWith(PLACE_ON_PREFIX) ? 1 : 0);
		return wom.elements.get(StatusToWorldModel.BLOCK_ID_PREFIX + Math.round(p.x) + "_" + y + "_" + Math.round(p.z));
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
	 *  without the fixed ones, plus air, since a block may disappear (eaten, blown up). An
	 *  empty cell takes the family of air: the blocks that can be placed in it. */
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
			for (String name : families.getOrDefault(current, List.of(current))) {
				if (name.equals(TargetDomain.AIR))
					continue;
				Map<String, List<Object>> properties = new LinkedHashMap<>(
						propertiesByBlock.computeIfAbsent(name, block -> env.getBlockProperties(AGENT_ID, block)));
				properties.keySet().removeAll(FIXED_PROPERTIES);
				domain.names.put(name, properties);
			}
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

	/** Print the learner the BURLAP config asks for: the network, from the state vector to one
	 *  output per action, and its parameters. */
	private static void printLearner(int inputs, int outputs) {
		String algorithm = (String) burlapConfiguration.getParameterValue("burlap.algorithm");
		if (!algorithm.equalsIgnoreCase("DeepQLearning"))
			throw new RuntimeException("Algorithm " + algorithm + " not supported by the exploration system yet");
		Object hidden = burlapConfiguration.getParameterValue("burlap.network.hidden_size");
		System.out.println("Learner: " + algorithm + ", network " + inputs + " -> " + hidden + " -> " + hidden
				+ " -> " + outputs
				+ "\n  episodes=" + burlapConfiguration.getParameterValue("burlap.num_of_episodes")
				+ " gamma=" + burlapConfiguration.getParameterValue("burlap.qlearning.gamma")
				+ " lr=" + burlapConfiguration.getParameterValue("burlap.qlearning.dqn_lr")
				+ " epsilon=" + burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval")
				+ " -> " + burlapConfiguration.getParameterValue("burlap.qlearning.epsilonmin")
				+ " by " + burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep") + " per episode"
				+ "\n  replay buffer=" + burlapConfiguration.getParameterValue("burlap.network.replay_buffer_capacity")
				+ " batch=" + burlapConfiguration.getParameterValue("burlap.network.batch_size")
				+ " min replay=" + burlapConfiguration.getParameterValue("burlap.network.min_replay_size")
				+ " target update=" + burlapConfiguration.getParameterValue("burlap.network.target_update_frequency"));
	}

	/** The DQN of the BURLAP config. Its outputs follow the order of the action list; the
	 *  domain only carries the actions, since the transitions come from the live server. */
	private static DeepQLearningRL createLearner(ExplorerStateEncoder encoder, List<ExplorerAction> actions) {
		SADomain domain = new SADomain();
		domain.addActionType(new ExplorerActionType(actions));
		domain.setModel(new MinecraftSampleModel());
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
				(int) mineConfiguration.getParameterValue("mine.max_actions_per_episode"),
				(double) burlapConfiguration.getParameterValue("burlap.qlearning.epsilonmin"),
				(int) burlapConfiguration.getParameterValue("burlap.network.hidden_size"));
		learner.setReplayBufferCapacity((int) burlapConfiguration.getParameterValue("burlap.network.replay_buffer_capacity"));
		learner.setBatchSize((int) burlapConfiguration.getParameterValue("burlap.network.batch_size"));
		learner.setMinReplaySize((int) burlapConfiguration.getParameterValue("burlap.network.min_replay_size"));
		learner.setTargetUpdateFrequency((int) burlapConfiguration.getParameterValue("burlap.network.target_update_frequency"));
		return learner;
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
	 *             [2] = mode (training, testing, random; not used yet),
	 *             [3] = BURLAP config file (the algorithm and its parameters),
	 *             [4] = Minecraft SUT config file, i.e. mineAgent.config
	 */
	public static void main(String[] args) {
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

		// outputDir = rlbt-files/minecraft-results/<level>/exploration/<systemtime>
		String levelName = new File(levelCsv).getName().replaceFirst("\\.csv$", "");
		outputDir = outputDir + File.separator + levelName + File.separator + "exploration"
				+ File.separator + systemtime;

		new MineExplorer().explore(testbenchUrl, levelCsv);
	}
}
