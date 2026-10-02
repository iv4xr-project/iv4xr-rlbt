package eu.fbk.iv4xr.rlbt;

import eu.fbk.iv4xr.rlbt.minecraft.MineAgent;
import eu.fbk.iv4xr.rlbt.minecraft.MineAgentBaseline;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class RlbtLauncher {

	private static final String DEFAULT_GAME_CONFIG =
			System.getProperty("user.dir") + File.separator
			+ "src/test/resources/configurations/game.config";

	public static void main(String[] args) throws Exception {
		String gameName = "LabRecruits";
		String gameConfigFile = DEFAULT_GAME_CONFIG;

		for (int i = 0; i < args.length; i++) {
			if ("-game".equals(args[i]) && i + 1 < args.length) {
				gameName = args[++i];
			} else {
				gameConfigFile = args[i];
			}
		}

		Properties gameConfig = new Properties();
		try (InputStream in = new FileInputStream(gameConfigFile)) {
			gameConfig.load(in);
		}

		String modeFlag = toModeFlag(gameConfig.getProperty("game.mode", "training"));
		String burlapConfig = gameConfig.getProperty("game.burlapConfig");

		switch (gameName) {
			case "LabRecruits":
				System.out.println("Launching LabRecruits...");
				launchLabRecruits(gameConfig, modeFlag, burlapConfig);
				break;
			case "Minecraft":
				System.out.println("Launching Minecraft...");
				launchMineAgentMain(gameConfig, burlapConfig);
				break;
			default:
				throw new IllegalArgumentException("Unknown game: " + gameName + ". Use -game LabRecruits or -game Minecraft");
		}
	}

	private static void launchLabRecruits(Properties gameConfig, String modeFlag, String burlapConfig) throws Exception {
		boolean singleAgent = Boolean.parseBoolean(gameConfig.getProperty("game.lrSingleAgent", "true"));

		if (singleAgent) {
			String sutConfig = gameConfig.getProperty("game.lrSingleAgentSutConfig");
			RlbtMain.main(new String[] {
					"-" + modeFlag, "-burlapConfig", burlapConfig, "-sutConfig", sutConfig });
		} else {
			if (!modeFlag.equals("trainingMode")) {
				// RlbtMultiAgentMain only implements the multi-agent path for training
				// (-multiagentTrainingMode); testing/random are single-agent only there.
				throw new UnsupportedOperationException(
						"game.mode=" + modeFlag + " is not supported for multi-agent LabRecruits yet");
			}
			String sutConfig = gameConfig.getProperty("game.lrMultiAgentSutConfig");
			RlbtMultiAgentMain.main(new String[] {
					"-multiagentTrainingMode", "-burlapConfig", burlapConfig, "-sutConfig", sutConfig });
		}
	}

	private static void launchMineAgentMain(Properties gameConfig, String burlapConfig) throws Exception {
		// Configuration files
		boolean baselineFlag = baselineFlag(gameConfig.getProperty("game.mineAgentUseBaseline", "false"));
		String sutConfigPath = gameConfig.getProperty("game.mineAgentSutConfig");
		String mineBurlapConfig = gameConfig.getProperty("game.mineAgentBurlapConfig", burlapConfig);
		Properties mineConfig = new Properties();
		try (InputStream in = new FileInputStream(sutConfigPath)) {
			mineConfig.load(in);
		}

		// Choose training, random or test
		String modeFlag = toModeFlag(gameConfig.getProperty("game.mode", "training"));

		// Get level; the server address (mine.address) is read by the agent, which
		// asks the testbench to log its bot in
		String levelPath = new File(mineConfig.getProperty("mine.level")).getAbsolutePath();
		String testbenchUrl = mineConfig.getProperty("mine.testbenchUrl", "http://localhost:3000");

		// Start MineFlayer Testbench in server mode
		String npm = System.getProperty("os.name").toLowerCase().contains("win") ? "npm.cmd" : "npm";
		File workDir = new File("sut/minecraft/mineflayer-testbench");
		ProcessBuilder pb = new ProcessBuilder(List.of(npm, "run", "start"));
		pb.directory(workDir);
		pb.inheritIO();
		System.out.println("Starting mineflayer-testbench (server mode)");
		Process testbench = pb.start();

		if (baselineFlag) {
			System.out.println("[MODE] Selected baselineMode for MineAgent: this is a scripted version just for comparison.");
			try {
				waitForTestbench(testbenchUrl, 60);
				MineAgentBaseline.main(new String[] { testbenchUrl, levelPath, sutConfigPath });
			} finally {
				// npm spawns node as a child process: kill the whole tree
				testbench.descendants().forEach(ProcessHandle::destroy);
				testbench.destroy();
			}
		} else { // Agent (RL-DRL)
			System.out.println("[MODE] Selected " + modeFlag + " for MineAgent.");
			try {
				waitForTestbench(testbenchUrl, 60);
				MineAgent.main(new String[] { testbenchUrl, levelPath, modeFlag, mineBurlapConfig, sutConfigPath });
			} finally {
				testbench.descendants().forEach(ProcessHandle::destroy);
				testbench.destroy();
			}
		}
	}

	/**
	 * Poll the testbench until it answers or the timeout expires. It serves no
	 * route before a bot joins, so any HTTP answer below 500 (a 404 included)
	 * means the server is up.
	 */
	private static void waitForTestbench(String url, int timeoutSeconds) throws Exception {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url + "/"))
				.timeout(Duration.ofSeconds(2))
				.GET().build();

		long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
		while (System.currentTimeMillis() < deadline) {
			try {
				if (http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() < 500) {
					return;
				}
			} catch (Exception e) {
				// not up yet, retry
			}
			Thread.sleep(1000);
		}
		throw new IllegalStateException("MineflayerTestbench not reachable at " + url
				+ " after " + timeoutSeconds + "s");
	}

	private static boolean baselineFlag(String baseline) throws IllegalArgumentException{
		if (baseline.equalsIgnoreCase("true"))
			return true;
		else if (baseline.equalsIgnoreCase("false"))
			return false;
		throw new IllegalArgumentException("Invalid value for game.baseline: " + baseline);
	}


	private static String toModeFlag(String mode) {
		switch (mode) {
			case "training": return "trainingMode";
			case "testing": return "testingMode";
			case "random": return "randomMode";
			default: throw new IllegalArgumentException("Unknown game.mode in game.config: " + mode);
		}
	}
}
