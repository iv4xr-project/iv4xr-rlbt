package eu.fbk.iv4xr.rlbt.minecraft;

import java.io.File;
import java.io.FileNotFoundException;
import eu.fbk.iv4xr.minecraftlib.MinecraftAgent;
import eu.fbk.iv4xr.minecraftlib.MinecraftEnv;
import eu.fbk.iv4xr.minecraftlib.MinecraftGoalLib;
import eu.fbk.iv4xr.minecraftlib.MinecraftState;
import eu.fbk.iv4xr.minecraftlib.StatusToWorldModel;
import eu.fbk.iv4xr.rlbt.configuration.MinecraftConfiguration;
import eu.iv4xr.framework.mainConcepts.TestAgent;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.mainConcepts.WorldModel;
import eu.iv4xr.framework.spatial.Vec3;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import nl.uu.cs.aplib.mainConcepts.ProgressStatus;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static nl.uu.cs.aplib.AplibEDSL.SEQ;
import static nl.uu.cs.aplib.AplibEDSL.SUCCESS;

public class MineAgentBaseline {

    // Predefined defaults
    static String defaultTestbenchUrl = "http://localhost:3000";
    static String defaultLevelCsv = "src/test/resources/minecraft-levels/arena.csv";
    static String currentDir = System.getProperty("user.dir");
    static String outputDir = currentDir + File.separator + "rlbt-files"+ File.separator + "minecraft-results";
    public static long systemtime = System.nanoTime();

    static MinecraftConfiguration mineConfiguration = new MinecraftConfiguration();

    private static final String AGENT_ID = "Bot";
    private static String mobTag;   // deve combaciare col tag nel CSV: @zombie^zombie

    private int maxTicks;
    private static final int MAX_ITERATIONS = 10;

    // damage taken, accumulated per-tick across all phases (regen-robust, unlike boundary sampling)
    private float totalDamageTaken;
    private Float prevOwnHp;

    private static void writeSummaryTxt(File sessionDir, int hits_landed, int hits_attempted, float hit_efficiency, float total_damage_dealt, float total_damage_taken, int ticks_to_kill, boolean killed) {
        File summary = new File(sessionDir, "summary.txt");   // alongside ticks.csv / actions.csv
        String nl = System.lineSeparator();
        try {
            FileWriter myWriter = new FileWriter(summary);
            myWriter.write("Hits attempted: " + hits_attempted + " | Hits landed: " + hits_landed + " | Efficiency: " + hit_efficiency + nl);
            myWriter.write("Total damage dealt: " + total_damage_dealt + " | Total damage taken: " + total_damage_taken + " | Killed: " + killed + nl);
            myWriter.write("Ticks to kill: " + ticks_to_kill + nl);
            myWriter.close();  // must close manually
            System.out.println("Successfully wrote summary to: " + summary);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void executeBaselineTest(String testbenchUrl, String levelCsv) {
        System.out.println("-------------------------- Starting Baseline on Minecraft ---------------------");
        maxTicks = (int) mineConfiguration.getParameterValue("mine.max_ticks_per_action");
        mobTag = (String) mineConfiguration.getParameterValue("mine.mob_tag");
        String weapon = (String) mineConfiguration.getParameterValue("mine.weapon");
        MinecraftEnv env = new MinecraftEnv(testbenchUrl);
        MinecraftState state = new MinecraftState();
        MinecraftGoalLib goalLib = new MinecraftGoalLib();

        MinecraftAgent agent = new MinecraftAgent(AGENT_ID, (String) mineConfiguration.getParameterValue("mine.address"));
        agent.setTestDataCollector(new TestDataCollector());
        // Connect state and agent environment: attaching the environment logs the bot into the server
        agent.attachState(state).attachEnvironment(env);

        try {
            env.buildLevel(AGENT_ID, Files.readString(Path.of(levelCsv)), 0, 65, 0);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the level " + levelCsv, e);
        }

        // Create the per-session output directory (minecraft-results/<level>/baseline/<systemtime>)
        File sessionDir = new File(outputDir);
        if (!sessionDir.exists() && !sessionDir.mkdirs())
            throw new RuntimeException("Unable to create output directory: " + sessionDir);

        System.out.println("Writing results to: " + sessionDir);

        final int episode = 1;            // baseline: a single episode per run
        state.updateState(AGENT_ID);      // so start-of-run own HP is observable
        logMobHealth(env, "start");

        try (EpisodeLogger log = new EpisodeLogger(sessionDir)) {
            int tick = 0;
            totalDamageTaken = 0f;
            prevOwnHp = state.getHealth();   // baseline for per-tick damage-taken accounting

            // equip the weapon + reach the mob ---
            Float mobBefore = env.getMobHealth(AGENT_ID, mobTag);
            Float ownBefore = state.getHealth();
            GoalStructure approach = SEQ(
                    goalLib.selected(weapon),
                    goalLib.tagReachedWithinDistance(mobTag, 2.0));
            tick = runGoal(agent, state, env, approach, log, episode, "approach", tick);
            log.logAction(episode, "MOVE_TO", mobTag, "2.0", approach.getStatus().toString(),
                    mobBefore, env.getMobHealth(AGENT_ID, mobTag), ownBefore, state.getHealth());


            // variables for summary.txt
            int hits_attempted = 0;
            int hits_landed = 0;
            float total_damage_dealt = 0f;
            int ticks_to_kill = -1;             // -1 = mob not killed within MAX_ITERATIONS
            boolean killed = false;
            int combatStartTick = tick;         // ticks accumulated up to the end of the approach phase


            // attack loop: hit until the mob dies (or max iterations) ---
            for (int i = 1; i < MAX_ITERATIONS; i++) {
                mobBefore = env.getMobHealth(AGENT_ID, mobTag);
                ownBefore = state.getHealth();

                GoalStructure hit = SEQ(
                        goalLib.attacked(mobTag),  // attack
                        goalLib.waited(20));   // wait ~1s
                tick = runGoal(agent, state, env, hit, log, episode, "hit_" + i, tick);

                Float mobAfter = env.getMobHealth(AGENT_ID, mobTag);
                Float ownAfter = state.getHealth();
                log.logAction(episode, "ATTACK", mobTag, "", hit.getStatus().toString(),
                        mobBefore, mobAfter, ownBefore, ownAfter);

                // damage dealt + whether the hit landed (i.e. it dealt damage).
                // A hit "lands" iff it removed HP — NOT iff the goal status is SUCCESS: the
                // attack+wait goal succeeds whenever the action completes, even on a miss.
                boolean landed = false;
                if (mobBefore != null) {
                    float mobAfterHp = (mobAfter == null) ? 0f : mobAfter;   // dead mob = 0 HP
                    if (mobBefore > mobAfterHp) {
                        total_damage_dealt += mobBefore - mobAfterHp;
                        landed = true;
                    }
                }
                // (damage taken is accumulated per-tick in runGoal, not here)

                hits_attempted++;
                if (landed)
                    hits_landed++;

                // print mob health after hit
                logMobHealth(env, "after hit " + i);
                if (mobAfter == null || mobAfter <= 0) {
                    System.out.println("Mob killed.");
                    killed = true;
                    ticks_to_kill = tick - combatStartTick;   // combat-only ticks (approach excluded)
                    break;
                }
            }

            float hit_efficiency = hits_attempted > 0
                    ? (float) hits_landed / hits_attempted
                    : 0f;


            // hits_attempted, hits_landed, efficiency, total_damage_dealt, total_damage_taken, ticks_to_kill, killed
            writeSummaryTxt(sessionDir, hits_landed, hits_attempted, hit_efficiency, total_damage_dealt, totalDamageTaken, ticks_to_kill, killed);
        }
    }

    /**
     * Execute a goal by advancing (tick) the agent until the goal is completed or maxTicks is reached.
     * On every tick it reads the current state and writes a row to {@code ticks.csv} (and prints a line).
     *
     * @param agent   the TestAgent
     * @param state   the MinecraftState
     * @param env     the MinecraftEnv
     * @param g       the GoalStructure to pursue
     * @param log     the per-session CSV logger
     * @param episode episode index (baseline uses a single episode)
     * @param phase   label of the current phase (e.g. "approach", "hit_3")
     * @param tick    the running tick counter at the start of this goal
     * @return the updated tick counter after this goal has finished
     */
    private int runGoal(TestAgent agent, MinecraftState state, MinecraftEnv env, GoalStructure g,
                        EpisodeLogger log, int episode, String phase, int tick) {
        agent.setGoal(g);
        state.updateState(AGENT_ID);
        int k = 0;
        while (g.getStatus().inProgress() && k < maxTicks) {
            agent.update();   // every update() the agent executes an action (blocking server-side)
            k++;
            tick++;

            Float ownHp = state.getHealth();

            // accumulate damage taken from every HP drop (only decreases count, so regen is ignored)
            if (prevOwnHp != null && ownHp != null && ownHp < prevOwnHp)
                totalDamageTaken += prevOwnHp - ownHp;
            if (ownHp != null)
                prevOwnHp = ownHp;

            Vec3 ownPos = state.getAgentPosition();
            Float mobHp = env.getMobHealth(AGENT_ID, mobTag);
            Vec3 mobPos = mobPosition(env, state, mobTag);
            Double dist = distance(ownPos, mobPos);

            System.out.println("[" + phase + " t" + tick + "] HP " + ownHp + " @" + ownPos
                    + " | Goal " + g.getStatus() + " | Mob HP " + mobHp + " @" + mobPos + " | dist " + dist);

            log.logTick(episode, tick, phase, g.getStatus().toString(), ownHp, ownPos, mobHp, mobPos, dist);
        }
        return tick;
    }

    /**
     * Euclidean distance between two positions, or null if either is unknown/unobserved.
     */
    private static Double distance(Vec3 a, Vec3 b) {
        return (a == null || b == null) ? null : (double) Vec3.dist(a, b);
    }

    /**
     * Read and print mob health via MinecraftEnv.getMobHealth
     * (calls route GET /:bot/tags/:uuid of the testbench)
     *
     * @param env  environment
     * @param when descriptive label of the moment (e.g. "start", "after hit 3")
     */
    private void logMobHealth(MinecraftEnv env, String when) {
        Float hp = env.getMobHealth(AGENT_ID, MineAgentBaseline.mobTag);
        System.out.println("HP of " + MineAgentBaseline.mobTag + " (" + when + "): " + (hp == null ? "dead/unreachable" : hp));
    }

    /**
     * Live position of a tagged mob, read from the agent's belief-state observation.
     *
     * @param env   environment (holds the tag -> uuid cache)
     * @param state the MinecraftState (its worldmodel must be up to date, i.e. after updateState)
     * @param tag   logical tag of the mob (e.g. "mob1")
     * @return the live position, or null if the tag is unknown or the mob is not currently observed
     *         (e.g. out of the testbench scan radius)
     */
    private Vec3 mobPosition(MinecraftEnv env, MinecraftState state, String tag) {
        String uuid = env.tagUuids.get(tag);
        if (uuid == null || state.worldmodel == null)
            return null;
        WorldEntity e = state.worldmodel.getElement(uuid);
        return e == null ? null : e.position;
    }

    /**
     * Connect to a running MineflayerTestbench server and build the arena level.
     * @param args [0] = testbench URL (default localhost:3000),
     *             [1] = level csv path (default the arena example),
     *             [2] = Minecraft SUT config file, i.e. mineAgent.config
     *                   (optional; without it the in-code defaults of
     *                   MinecraftConfiguration apply)
    */
    public static void main(String[] args) throws FileNotFoundException, InterruptedException {
        MineAgentBaseline main = new MineAgentBaseline();

        // Get the testbench URL and level CSV path from command line arguments or use defaults
        String testbenchUrl = args.length > 0 ? args[0] : defaultTestbenchUrl;
        String levelCsv = args.length > 1 ? args[1] : defaultLevelCsv;

        // Load the Minecraft SUT parameters (tick budget) from file
        if (args.length > 2 && args[2] != null) {
            System.out.println("Loading Minecraft configuration: " + args[2]);
            if (!mineConfiguration.updateParameters(args[2]))
                throw new RuntimeException("Cannot load Minecraft configuration " + args[2]);
        }

        // Generate new folder for each run, following Lab Recruits' level system
        String levelName = new File(levelCsv).getName().replaceFirst("\\.csv$", "");
        String path = levelName + File.separator + "baseline" + File.separator + systemtime;
        outputDir = outputDir + File.separator + path;


        main.executeBaselineTest(testbenchUrl, levelCsv);


        System.out.println("Baseline test completed successfully.");
    }
}
