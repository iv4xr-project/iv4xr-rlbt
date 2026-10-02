package eu.fbk.iv4xr.rlbt.minecraft;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import burlap.behavior.singleagent.Episode;
import burlap.behavior.singleagent.learning.LearningAgent;
import burlap.mdp.auxiliary.DomainGenerator;
import burlap.mdp.singleagent.SADomain;
import burlap.statehashing.simple.SimpleHashableStateFactory;
import eu.fbk.iv4xr.minecraftlib.MinecraftEnv;
import eu.fbk.iv4xr.rlbt.DeepQLearningRL;
import eu.fbk.iv4xr.rlbt.QLearningRL;
import eu.fbk.iv4xr.rlbt.RlbtMain;
import eu.fbk.iv4xr.rlbt.configuration.BurlapConfiguration;
import eu.fbk.iv4xr.rlbt.configuration.MinecraftConfiguration;
import eu.fbk.iv4xr.rlbt.distance.NoStateSimilarity;
import eu.fbk.iv4xr.rlbt.utils.SerializationUtil;
import eu.iv4xr.framework.spatial.Vec3;

public class MineAgent {
	// Predefined defaults
	static String defaultTestbenchUrl = "http://localhost:3000";
	static String defaultLevelCsv = "src/test/resources/minecraft-levels/arena.csv";
	static String defaultGameMode = "training";

	static BurlapConfiguration burlapConfiguration = new BurlapConfiguration();
	static MinecraftConfiguration mineConfiguration = new MinecraftConfiguration();

	static String currentDir = System.getProperty("user.dir");
	static String outputDir = currentDir + File.separator + "rlbt-files" + File.separator + "minecraft-results";
	public static long systemtime = System.nanoTime();

	/**
	 * DQN training. Differs from the tabular run only in the learner and in what it
	 * writes to disk: environment, episode loop, budgets and output files are the same,
	 * so a DQN session and a QLearning session on the same level can be diffed field by field.
	 */
	private static List<Episode> executeDeepQLearningTrainingOnMinecraft(String testbenchUrl, String levelCsv) throws InterruptedException, FileNotFoundException {
		MinecraftEnv minecraftEnv = new MinecraftEnv(testbenchUrl);
		DomainGenerator mcDomainGenerator = new MinecraftDomainGenerator();
		final SADomain domain = (SADomain) mcDomainGenerator.generateDomain();

		// the environment logs the bot in, and the level can only be built through it
		MinecraftRLEnvironment mcRlEnvironment = new MinecraftRLEnvironment(minecraftEnv, mineConfiguration);
		initializeLevel(testbenchUrl, levelCsv, minecraftEnv);
		File sessionDir = prepareSessionDir();

		int numEpisodes = (int)burlapConfiguration.getParameterValue("burlap.num_of_episodes");
		System.out.println("Episodes: " + numEpisodes);
		double epsilonval = spreadEpsilonDecayOver(numEpisodes);

		/* The action names go in the order of the network output neurons, and
		 * Command.values() is exactly the order MinecraftActionType builds its fixed
		 * action list in. */
		List<String> actionNames = new ArrayList<String>();
		for (MinecraftAction.Command command : MinecraftAction.Command.values())
			actionNames.add(command.name());

		final DeepQLearningRL agent = new DeepQLearningRL(domain,
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.gamma"),
				new MinecraftStateEncoder(),
				actionNames,
				// dqn_lr, not qlearning.lr: Adam needs a far smaller step than the tabular
				// Bellman update (see BurlapConfiguration)
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.dqn_lr"),
				epsilonval,
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep"),
				mcRlEnvironment.maxActionsPerEpisode(),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.epsilonmin"),
				(int)burlapConfiguration.getParameterValue("burlap.network.hidden_size"));

		/* Replay buffer and target network are sized on the episode length, and Minecraft
		 * episodes are ~7x shorter than LabRecruits ones (30 actions against 200, fewer
		 * still when the agent dies early). Left at the defaults, the warm-up alone
		 * outlasts a short run and the network never trains. */
		agent.setReplayBufferCapacity((int)burlapConfiguration.getParameterValue("burlap.network.replay_buffer_capacity"));
		agent.setBatchSize((int)burlapConfiguration.getParameterValue("burlap.network.batch_size"));
		agent.setMinReplaySize((int)burlapConfiguration.getParameterValue("burlap.network.min_replay_size"));
		agent.setTargetUpdateFrequency((int)burlapConfiguration.getParameterValue("burlap.network.target_update_frequency"));

		Session session = runEpisodes(agent, mcRlEnvironment, numEpisodes, sessionDir);

		saveSession(session, mcRlEnvironment, sessionDir, dir -> {
			agent.printNetworkSummary(System.out);
			// .ser to reload the weights, .txt to read the architecture back
			String networkFile = dir + File.separator + "qnetwork.ser";
			agent.serializeModel(networkFile);
			agent.printNetworkSummary(new PrintStream(networkFile + ".txt"));
		});
		return session.episodes;
	}

	private static List<Episode> executeQLearningTrainingOnMinecraft(String testbenchUrl, String levelCsv) throws InterruptedException, FileNotFoundException {
		MinecraftEnv minecraftEnv = new MinecraftEnv(testbenchUrl);
		DomainGenerator mcDomainGenerator = new MinecraftDomainGenerator();
		final SADomain domain = (SADomain) mcDomainGenerator.generateDomain();

		// the environment logs the bot in, and the level can only be built through it
		MinecraftRLEnvironment mcRlEnvironment = new MinecraftRLEnvironment(minecraftEnv, mineConfiguration);
		initializeLevel(testbenchUrl, levelCsv, minecraftEnv);
		File sessionDir = prepareSessionDir();

		// Get number of episodes from burlap_minecraft.config
		int numEpisodes = (int)burlapConfiguration.getParameterValue("burlap.num_of_episodes");
		System.out.println("Episodes: " + numEpisodes);
		double epsilonval = spreadEpsilonDecayOver(numEpisodes);

		final QLearningRL agent = new QLearningRL(domain,
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.gamma"),
				new SimpleHashableStateFactory(),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.qinit"),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.lr"),
				epsilonval,
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep"),
				numEpisodes,
				new NoStateSimilarity());

		Session session = runEpisodes(agent, mcRlEnvironment, numEpisodes, sessionDir);

		saveSession(session, mcRlEnvironment, sessionDir, dir -> {
			agent.printFinalQtable(System.out);
			// .ser to reload it (testing mode, or resuming), .txt to read it
			String qtableFile = dir + File.separator + "qtable.ser";
			agent.serializeQTable(qtableFile);
			agent.printFinalQtable(new PrintStream(qtableFile + ".txt"));
		});
		return session.episodes;
	}


	private static File prepareSessionDir() {
		File sessionDir = new File(outputDir);
		if (!sessionDir.exists() && !sessionDir.mkdirs()) {
			throw new RuntimeException("Unable to create output directory: " + sessionDir);
		}
		System.out.println("Writing results to: " + sessionDir);
		return sessionDir;
	}


	/** Spreads the epsilon decay over the episode budget and stores it back into the
	 * configuration, so the value the agent is built with matches the run length.
	 * @return the initial epsilon (the decay goes into burlap.qlearning.decayedepsilonstep) */
	private static double spreadEpsilonDecayOver(int numEpisodes) {
		// The reward type is always CuriosityDriven (not implemented Sparse)
		double epsilonval = (double)burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval");
		double calculatedDecayVal = (double)(epsilonval/numEpisodes);
		calculatedDecayVal=calculatedDecayVal/2;
		burlapConfiguration.setParameterValue("burlap.qlearning.decayedepsilonstep", Double.toString(calculatedDecayVal));  // set calculated decayed value according to number of episodes
		System.out.println("epsilon val ="+epsilonval+ "  decay = "+calculatedDecayVal);
		return epsilonval;
	}


	/** The training loop, identical for every learner: the LearningAgent interface of
	 * BURLAP is all it needs, so tabular and DQN go through the very same code path. */
	private static Session runEpisodes(LearningAgent agent, MinecraftRLEnvironment mcRlEnvironment,
			int numEpisodes, File sessionDir) throws FileNotFoundException {

		Session session = new Session(numEpisodes);
		int maxActionsPerEpisode = mcRlEnvironment.maxActionsPerEpisode();

		/*------------Training - start running episodes------------------------*/
		try (EpisodeLogger log = new EpisodeLogger(sessionDir)) {
			mcRlEnvironment.setLogger(log);

			mcRlEnvironment.startAgentEnvironment();
			for (int i = 0; i < numEpisodes; i++) {
				System.out.println("---------------- Episode " + (i + 1) + "/" + numEpisodes + " ----------------");
				long startTime = System.currentTimeMillis();
				session.episodes.add(agent.runLearningEpisode(mcRlEnvironment, maxActionsPerEpisode));
				long elapsed = System.currentTimeMillis() - startTime;
				session.episodeTime.add(elapsed);
				System.out.println("Time for this episode: " + elapsed + " ms");

				/* Read the episode coverage here and not after the loop: the
				 * reset below starts the next episode, which clears it. */
				session.episodeCoverage.add(mcRlEnvironment.episodeDistanceCoverage() + ","
						+ mcRlEnvironment.episodeOwnHpCoverage() + ","
						+ mcRlEnvironment.episodeMobHpCoverage() + ","
						+ mcRlEnvironment.episodeActionsCoverage() + ","
						+ mcRlEnvironment.cumulativeStateActionCoverage());

				// Rebuilds the arena through the testbench and re-equips the sword.
				long resetStart = System.currentTimeMillis();
				mcRlEnvironment.resetEnvironment();
				System.out.println("Time for the reset: " + (System.currentTimeMillis() - resetStart) + " ms");
			}
		}
		return session;
	}


	/** What one training session produced, before any of it reaches disk. */
	private static class Session {
		final List<Episode> episodes;
		final List<Long> episodeTime;
		/** the already-formatted coverage cells of each episode, see runEpisodes() */
		final List<String> episodeCoverage;

		Session(int numEpisodes) {
			episodes = new ArrayList<Episode>(numEpisodes);
			episodeTime = new ArrayList<Long>(numEpisodes);
			episodeCoverage = new ArrayList<String>(numEpisodes);
		}
	}


	/** How a learner writes itself out: a Q-table for the tabular agent, network weights
	 * for the DQN. The only part of saveSession() that depends on the algorithm. */
	private interface AgentWriter {
		void write(File sessionDir) throws FileNotFoundException;
	}


	/** Write everything the session produced next to the CSVs
	 *  the environment has already been filling in. */
	private static void saveSession(Session session, MinecraftRLEnvironment env, File sessionDir,
			AgentWriter agentWriter) throws FileNotFoundException {

		agentWriter.write(sessionDir);

		SerializationUtil.serializeEpisodes(session.episodes, sessionDir + File.separator + "episode");

		writeEpisodeSummary(session.episodes, session.episodeTime, session.episodeCoverage,
				new File(sessionDir, "episodeSummary.csv"));
		writeSummaryTxt(env, session.episodes.size(), new File(sessionDir, "summary.txt"));

		System.out.println("Session written to: " + sessionDir);
	}


	/** Per-episode figures: actions taken, total reward, wall-clock time, the four simple
	 * coverage metrics and the state-action coverage reached up to that episode */
	private static void writeEpisodeSummary(List<Episode> episodes, List<Long> episodeTime,
			List<String> episodeCoverage, File outFile) throws FileNotFoundException {
		try (PrintStream ps = new PrintStream(outFile)) {
			ps.println("episode,actions,total_reward,time_ms,"
					+ "dist_cov,own_hp_cov,mob_hp_cov,actions_cov,state_action_cov_cumulative");
			for (int i = 0; i < episodes.size(); i++) {
				Episode e = episodes.get(i);
				double totalReward = 0;
				for (Double r : e.rewardSequence)
					totalReward += r;
				ps.println((i + 1) + "," + e.actionSequence.size() + "," + totalReward + ","
						+ episodeTime.get(i) + "," + episodeCoverage.get(i));
			}
		}
	}

	/** The combat summary, in the same shape as MineAgentBaseline's summary.txt so
	 * the two runs can be compared field by field */
	private static void writeSummaryTxt(MinecraftRLEnvironment env, int numEpisodes, File outFile)
			throws FileNotFoundException {
		try (PrintStream ps = new PrintStream(outFile)) {
			ps.print(env.summary());
			// the count comes from here, not from the environment: see the note on
			// MinecraftRLEnvironment.summary()
			ps.println("Episodes: " + numEpisodes);
		}
		System.out.println("Successfully wrote summary to: " + outFile);
	}


	private void executeTraining(String testbenchUrl, String levelCsv) throws FileNotFoundException, InterruptedException {
		System.out.println("-------------------------- Starting Training on Minecraft ---------------------");
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");

		if (alg.equalsIgnoreCase(RlbtMain.BurlapAlgorithm.QLearning.toString()))
			executeQLearningTrainingOnMinecraft(testbenchUrl, levelCsv);
		else if (alg.equalsIgnoreCase(RlbtMain.BurlapAlgorithm.DeepQLearning.toString()))
			executeDeepQLearningTrainingOnMinecraft(testbenchUrl, levelCsv);
		else
			throw new RuntimeException("Algorithm "+alg+" not supported");
	}


	public void executeTesting(String testbenchUrl, String levelCsv) {
		System.out.println("-------------------------- !TESTING HAS NOT BEEN IMPLEMENTED YET! --------------------------");
	}

	/**
	 * Build the level through the bot, which must have already joined the server
	 * @param testbenchUrl url of the testbench
	 * @param levelCsv path of the level
	 */
	private static void initializeLevel(String testbenchUrl, String levelCsv, MinecraftEnv env) {
		String levelPath = new File(levelCsv).getAbsolutePath();

		System.out.println("Connected to MineflayerTestbench at " + testbenchUrl);
		System.out.println("Building level: " + levelPath);

		String levelData;
		try {
			levelData = Files.readString(Path.of(levelCsv));
		} catch (IOException e) {
			throw new RuntimeException("Unable to read the level " + levelPath, e);
		}
		Map<String, Vec3> tags = env.buildLevel(MinecraftRLEnvironment.AGENT_ID, levelData, 0, 150, 0);
		System.out.println("Arena built. Tags: " + tags);
	}

	private static String normalizeMode(String mode) {
		return mode.endsWith("Mode") ? mode.substring(0, mode.length() - "Mode".length()) : mode;
	}

	/**
	 * Connect to a running MineflayerTestbench server and build the arena level.
	 * @param args [0] = testbench URL (default localhost:3000),
	 *             [1] = level csv path (default the arena example),
	 *             [2] = mode (training, testing, random; the "...Mode" spelling
	 *                   used by RlbtLauncher is accepted too),
	 *             [3] = BURLAP config file (optional; without it the in-code
	 *                   defaults of BurlapConfiguration apply)
	 *             [4] = Minecraft SUT config file, i.e. mineAgent.config
	 *                   (optional; without it the in-code defaults of
	 *                   MinecraftConfiguration apply)
	 */
	public static void main(String[] args) throws FileNotFoundException, InterruptedException {
		MineAgent main = new MineAgent();

		// Get the testbench URL and level CSV path from command line arguments or use defaults
		String testbenchUrl = args.length > 0 ? args[0] : defaultTestbenchUrl;
		String levelCsv = args.length > 1 ? args[1] : defaultLevelCsv;
		String mode = normalizeMode(args.length > 2 ? args[2] : defaultGameMode);

		// outputDir = rlbt-files/minecraft-results/<level>/rlbt/<systemtime>
		String levelName = new File(levelCsv).getName().replaceFirst("\\.csv$", "");
		outputDir = outputDir + File.separator + levelName + File.separator + "rlbt"
				+ File.separator + systemtime;

		// Load the BURLAP parameters from file
		if (args.length > 3 && args[3] != null) {
			System.out.println("Loading BURLAP configuration: " + args[3]);
			if (!burlapConfiguration.updateParameters(args[3]))
				throw new RuntimeException("Cannot load BURLAP configuration " + args[3]);
		}

		// Load the Minecraft SUT parameters (episode budgets) from file
		if (args.length > 4 && args[4] != null) {
			System.out.println("Loading Minecraft configuration: " + args[4]);
			if (!mineConfiguration.updateParameters(args[4]))
				throw new RuntimeException("Cannot load Minecraft configuration " + args[4]);
		}

		switch(mode) {
			case "training":
				main.executeTraining(testbenchUrl, levelCsv);
				break;
			case "testing":
				main.executeTesting(testbenchUrl, levelCsv);
				break;
			default:
				// random: 0 = training, 1 = testing
				int randomMode = new java.util.Random().nextInt(2);
                if (randomMode == 0)
                    main.executeTraining(testbenchUrl, levelCsv);
                else
                    main.executeTesting(testbenchUrl, levelCsv);
                break;
		}
	}
}
