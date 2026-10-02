package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import eu.fbk.iv4xr.minecraftlib.MinecraftAgent;
import eu.fbk.iv4xr.minecraftlib.MinecraftEnv;
import eu.fbk.iv4xr.minecraftlib.MinecraftState;
import eu.fbk.iv4xr.rlbt.configuration.MinecraftConfiguration;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.spatial.Vec3;

/** Exploring agent activated from (game.mineAgentSystem=exploration).
 *  Specialized in coverage of the environment based on the available actions
 *  on the level. */
public class MineExplorer {
	static String defaultTestbenchUrl = "http://localhost:3000";
	static String defaultLevelCsv = "src/test/resources/minecraft-levels/the_big_level.csv";
	static String currentDir = System.getProperty("user.dir");
	static String outputDir = currentDir + File.separator + "rlbt-files" + File.separator + "minecraft-results";
	public static long systemtime = System.nanoTime();

	static MinecraftConfiguration mineConfiguration = new MinecraftConfiguration();

	private static final String AGENT_ID = "Bot";
	public static final String REWARD_TYPE = "CoverageOriented";

	/** Actions of MinecraftGoalLib whose only parameter is a target tag. */
	static final List<String> TARGET_ACTIONS = List.of(
			"tagReached", "clicked", "mined", "placed", "placedOn", "attacked", "usedOnEntity");

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

		List<String[]> pairs = new ArrayList<>();
		for (String action : TARGET_ACTIONS)
			for (String target : targets)
				pairs.add(new String[] { action, target });
		System.out.println("(action, target) pairs generated: " + pairs.size()
				+ " = " + TARGET_ACTIONS.size() + " actions x " + targets.size() + " targets");

		// minecraft-results/<level>/exploration/<systemtime>
		File sessionDir = new File(outputDir);
		if (!sessionDir.exists() && !sessionDir.mkdirs())
			throw new RuntimeException("Unable to create output directory: " + sessionDir);
		writePairs(new File(sessionDir, "pairs.csv"), pairs, tagPositions);
	}

	private static void writePairs(File file, List<String[]> pairs, Map<String, Vec3> tagPositions) {
		String nl = System.lineSeparator();
		try (FileWriter w = new FileWriter(file)) {
			w.write("action,target,position" + nl);
			for (String[] p : pairs) {
				Vec3 pos = tagPositions.get(p[1]);
				w.write(p[0] + "," + p[1] + "," + (pos == null ? "" : "\"" + pos + "\"") + nl);
			}
			System.out.println("Pairs written to: " + file);
		} catch (IOException e) {
			throw new RuntimeException("Unable to write " + file, e);
		}
	}

	/**
	 * @param args [0] = testbench URL (default localhost:3000),
	 *             [1] = level csv path (default the big level),
	 *             [2] = mode (training, testing, random; not used yet),
	 *             [3] = BURLAP config file (not used yet: it will choose the algorithm),
	 *             [4] = Minecraft SUT config file, i.e. mineAgent.config
	 */
	public static void main(String[] args) {
		String testbenchUrl = args.length > 0 ? args[0] : defaultTestbenchUrl;
		String levelCsv = args.length > 1 ? args[1] : defaultLevelCsv;

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
