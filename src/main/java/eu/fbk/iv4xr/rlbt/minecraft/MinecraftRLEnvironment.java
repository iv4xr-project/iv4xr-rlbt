package eu.fbk.iv4xr.rlbt.minecraft;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.environment.Environment;
import burlap.mdp.singleagent.environment.EnvironmentOutcome;
import eu.fbk.iv4xr.minecraftlib.MinecraftEnv;
import eu.fbk.iv4xr.minecraftlib.MinecraftGoalLib;
import eu.fbk.iv4xr.minecraftlib.MinecraftState;
import eu.fbk.iv4xr.rlbt.configuration.MinecraftConfiguration;
import eu.iv4xr.framework.mainConcepts.TestAgent;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.spatial.Vec3;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.DistanceBucket;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.HPBucket;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import org.datavec.api.transform.Distance;

import java.util.EnumSet;
import static nl.uu.cs.aplib.AplibEDSL.SEQ;

/** The BURLAP environment of the Minecraft combat scenario */
public class MinecraftRLEnvironment implements Environment {

    enum RewardType {
        COMBAT_ORIENTED,  // aims to kill the mob
        COVERAGE_ORIENTED // aims to cover the whole state-action domain
    }

    private final MinecraftEnv minecraftEnv;      // HTTP client, only one instance
    private final MinecraftState state;           // belief-state iv4xr
    private final TestAgent testAgent;            // minecraft agent
    private final MinecraftGoalLib goalLib;
    private final MinecraftBurlapState currentState;
    private double lastReward;
    private int updateCycles;   // budget of episode actions
    private RewardType rewardType;

    /** Episode Metrics */
    private Float lastMobHp;
    private Float lastOwnHp;
    private Integer deathsAtEpisodeStart;
    private boolean agentDied; // changed through the death event
    private int hitsAttempted;
    private int hitsLanded;
    private float totalDamageDealt;
    private float totalDamageTaken; // accumulated per TICK, not per action
    private int mobKills;
    private int agentDeaths;
    private boolean mobKilledThisEpisode;
    private float prevOwnHp;    // per-tick reference for totalDamageTaken


    /** Global coverage metrics to be reported in summary.txt. These count every state
        observed, whether an action was chosen from it or it merely followed one. */
    private final EnumSet<DistanceBucket> distanceCoverage = EnumSet.noneOf(DistanceBucket.class);
    private final EnumSet<HPBucket> ownHPCoverage = EnumSet.noneOf(HPBucket.class);
    private final EnumSet<HPBucket> mobHPCoverage = EnumSet.noneOf(HPBucket.class);
    private final EnumSet<MinecraftAction.Command> actionsCoverage = EnumSet.noneOf(MinecraftAction.Command.class);

    private final EnumSet<DistanceBucket> episodeDistance = EnumSet.noneOf(DistanceBucket.class);
    private final EnumSet<HPBucket> episodeOwnHP = EnumSet.noneOf(HPBucket.class);
    private final EnumSet<HPBucket> episodeMobHP = EnumSet.noneOf(HPBucket.class);
    private final EnumSet<MinecraftAction.Command> episodeActions = EnumSet.noneOf(MinecraftAction.Command.class);


    /** State-action coverage, recorded on the state each action was chosen in. */
    private final StateActionCoverage stateActionCoverage = new StateActionCoverage();


    /** Per-session CSV writer (null when the run is not being logged) */
    private EpisodeLogger logger;
    private int episodeNumber;
    private int tickCounter;

    private final String AGENT_ID = "Bot";
    private final String mobTag;
    private final String weapon;

    /** Distance that APPROACH and RETREAT aim to reach */
    private static final double APPROACH_DISTANCE = 6.0;
    private static final double RETREAT_DISTANCE = MinecraftBurlapState.RETREAT_RANGE;

    private static final double RETREAT_TOLERANCE = 1.5;
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    
    /** Weight of the coverage exploration bonus: what a state-action tuple is worth
        on its first visit of an episode */
    private static final double COVERAGE_BONUS = 1.0;

    private final int maxTicksPerAction;
    private final int maxActionsPerEpisode;

    public int maxActionsPerEpisode() {
        return maxActionsPerEpisode;
    }



    public MinecraftRLEnvironment(MinecraftEnv env, MinecraftConfiguration mineConfiguration) {
        maxTicksPerAction = (int) mineConfiguration.getParameterValue("mine.max_ticks_per_action");
        maxActionsPerEpisode = (int) mineConfiguration.getParameterValue("mine.max_actions_per_episode");
        mobTag = (String) mineConfiguration.getParameterValue("mine.mob_tag");
        weapon = (String) mineConfiguration.getParameterValue("mine.weapon");

        // The reward type is set in the configuration file
        rewardType = mineConfiguration.getParameterValue("mine.reward_type").equals("CoverageOriented") ?
                RewardType.COVERAGE_ORIENTED :
                RewardType.COMBAT_ORIENTED;

        System.out.println("[REWARD] Set Reward Type to " + rewardType.toString());

        minecraftEnv = env;
        state = new MinecraftState();
        goalLib = new MinecraftGoalLib();

        testAgent = new TestAgent(AGENT_ID, "tester");
        testAgent.setTestDataCollector(new TestDataCollector());

        // attaching the environment is what lets state.updateState() observe:
        // MinecraftState.updateState calls env().observe(agentId)
        testAgent.attachState(state).attachEnvironment(env);

        currentState = new MinecraftBurlapState();
        lastReward = 0;
        updateCycles = 0;
    }

    @Override
    public State currentObservation() {
        state.updateState(AGENT_ID);

        WorldEntity mob = mobEntity(mobTag);

        Float ownHp = state.getHealth();
        Float mobHp = minecraftEnv.getMobHealth(mobTag);
        Vec3 ownPos = state.getAgentPosition();
        Vec3 mobPos = (mob == null) ? null : mob.position;
        Double dist = distance(ownPos, mobPos);

        // WorldEntity.type holds the entity name ("zombie", ...)
        Float mobMaxHp = (mob == null) ? null : MinecraftBurlapState.maxHpForMob(mob.type);

        currentState.updateAbstraction(ownHp, mobHp, mobMaxHp, dist);
        markDistanceAsCovered(currentState.getEnemyDistance());
        markOwnHpAsCovered(currentState.getOwnHpBucket());
        markMobHpAsCovered(currentState.getMobHpBucket());

        // reward is computed on these, not on the buckets
        lastMobHp = mobHp;
        lastOwnHp = ownHp;

        Integer deaths = state.getDeathCount();
        if (deaths != null && deathsAtEpisodeStart != null && deaths > deathsAtEpisodeStart) {
            if (!agentDied)
                agentDeaths++;      // count the transition, not every observation after it
            agentDied = true;
            markOwnHpAsCovered(HPBucket.DEAD);
        }

        if (currentState.getMobHpBucket() == HPBucket.DEAD && !mobKilledThisEpisode) {
            mobKilledThisEpisode = true;
            mobKills++;
        }

        // a copy so that the state handed to the learner is not altered by the
        // next observation (BURLAP keeps past states inside the episodes)
        return currentState.copy();
    }

    private WorldEntity mobEntity(String tag) {
        String uuid = minecraftEnv.tagUuids.get(tag);
        if (uuid == null || state.worldmodel == null)
            return null;
        return state.worldmodel.getElement(uuid);
    }

    private static Double distance(Vec3 a, Vec3 b) {
        return (a == null || b == null) ? null : (double) Vec3.dist(a, b);
    }

    @Override
    public double lastReward() {
        return lastReward;
    }

    @Override
    public EnvironmentOutcome executeAction(Action a) {
        MinecraftAction action = (MinecraftAction) a;
        State oldState = currentObservation();

        Float mobHpBefore = lastMobHp;
        Float ownHpBefore = lastOwnHp;
        DistanceBucket distanceBefore = currentState.getEnemyDistance();
        HPBucket ownHPBucketBefore = currentState.getOwnHpBucket();
        HPBucket mobHPBucketBefore = currentState.getMobHpBucket();

        GoalStructure goal = toGoal(action);
        if (goal != null)
            runGoal(goal, maxTicksPerAction, action.actionName());

        State newState = currentObservation();
        lastReward = rewardType == RewardType.COMBAT_ORIENTED ?
                combatReward(mobHpBefore, ownHpBefore) :
                coverageReward(mobHPBucketBefore, ownHPBucketBefore, distanceBefore, action);
        updateCycles++; // the action consumes budget

        recordAction(action, goal, mobHpBefore, ownHpBefore,
                distanceBefore, ownHPBucketBefore, mobHPBucketBefore);

        System.out.println("Action " + action.actionName()
                + " | " + oldState + " -> " + newState
                + " | goal " + (goal == null ? "NOT APPLICABLE" : goal.getStatus())
                + " | reward " + lastReward);

        return new EnvironmentOutcome(oldState, action, newState, lastReward, isInTerminalState());
    }

    /** Translate a symbolic action into the aplib goal that carries it out */
    private GoalStructure toGoal(MinecraftAction action) {
        switch (action.getCommand()) {
            case APPROACH:
                return goalLib.tagReachedWithinDistance(mobTag, APPROACH_DISTANCE);
            case ATTACK: // hit then sit out the weapon cooldown
                return SEQ(goalLib.attacked(mobTag), goalLib.waited(ATTACK_COOLDOWN_TICKS));
            case RETREAT:
                Vec3 target = retreatPosition();
                return (target == null) ? null : goalLib.reached(target, RETREAT_TOLERANCE);
            default:
                throw new RuntimeException("Unknown command: " + action.getCommand());
        }
    }

    private Vec3 retreatPosition() {
        WorldEntity mob = mobEntity(mobTag);
        Vec3 me = state.getAgentPosition();
        if (mob == null || me == null)
            return null;

        Vec3 away = Vec3.sub(me, mob.position);
        away.y = 0;   // move on the ground plane, not into the air or the floor
        if (away.length() < 1e-3f || away.length() >= RETREAT_DISTANCE)
            return null;

        Vec3 destination = Vec3.add(mob.position,
                Vec3.mul(away.normalized(), (float) RETREAT_DISTANCE));
        destination.y = me.y;   // keep the agent's own height, the mob may be elsewhere
        return destination;
    }

    private void runGoal(GoalStructure goal, int tickBudget, String phase) {
        testAgent.setGoal(goal);
        int ticks = 0;
        while (goal.getStatus().inProgress() && ticks < tickBudget) {
            testAgent.update();   // every update() performs an action, blocking server-side
            ticks++;
            tickCounter++;
            accumulateDamageTaken();
            logTick(phase, goal.getStatus().toString());
        }
    }

    private void accumulateDamageTaken() {
        Float hp = state.getHealth();
        if (hp == null)
            return;

        markOwnHpAsCovered(MinecraftBurlapState.hpBucket(hp, MinecraftBurlapState.PLAYER_MAX_HP));

        if (prevOwnHp > hp)
            totalDamageTaken += prevOwnHp - hp;
        prevOwnHp = hp;
    }

    /** One row of ticks.csv: agent and mob telemetry */
    private void logTick(String phase, String goalStatus) {
        if (logger == null)
            return;
        WorldEntity mob = mobEntity(mobTag);
        Vec3 ownPos = state.getAgentPosition();
        Vec3 mobPos = (mob == null) ? null : mob.position;
        logger.logTick(episodeNumber, tickCounter, phase, goalStatus,
                state.getHealth(), ownPos, null, mobPos, distance(ownPos, mobPos));
    }

    /** One row of actions.csv, plus the session counters that summary.txt reports. */
    private void recordAction(MinecraftAction action, GoalStructure goal,
                              Float mobHpBefore, Float ownHpBefore, DistanceBucket distanceBefore,
                              HPBucket ownBucketBefore, HPBucket mobBucketBefore) {

        markActionAsCovered(action.getCommand());

        DistanceBucket distanceAfter = currentState.getEnemyDistance();
        HPBucket ownBucketAfter = agentDied ? HPBucket.DEAD : currentState.getOwnHpBucket();
        HPBucket mobBucketAfter = currentState.getMobHpBucket();

        // only the state the action was chosen in: the one it led to never saw it run
        stateActionCoverage.record(distanceBefore, ownBucketBefore, mobBucketBefore,
                action.getCommand());

        float dealt = (mobHpBefore == null) ? 0f
                : Math.max(0f, mobHpBefore - (lastMobHp == null ? 0f : lastMobHp));
        totalDamageDealt += dealt;

        if (action.getCommand() == MinecraftAction.Command.ATTACK) {
            hitsAttempted++;
            if (dealt > 0f)
                hitsLanded++;
        }

        if (logger != null) {
            logger.logAction(episodeNumber, action.actionName(), mobTag, "",
                    goal == null ? "NOT APPLICABLE" : goal.getStatus().toString(),
                    distanceBefore, distanceAfter,
                    mobHpBefore, lastMobHp, mobBucketBefore, mobBucketAfter,
                    ownHpBefore, lastOwnHp, ownBucketBefore, ownBucketAfter);
        }
    }

    public void setLogger(EpisodeLogger logger) {
        this.logger = logger;
    }

    public String summary() {
        String nl = System.lineSeparator();
        float efficiency = hitsAttempted > 0 ? (float) hitsLanded / hitsAttempted : 0f;
        return "Hits attempted: " + hitsAttempted + " | Hits landed: " + hitsLanded
                    + " | Efficiency: " + efficiency + nl
             + "Total damage dealt: " + totalDamageDealt
                    + " | Total damage taken: " + totalDamageTaken
                    + " | Mob kills: " + mobKills + nl
             + "Agent deaths: " + agentDeaths + nl
             + "-----" + nl
             + "GLOBAL STATE COVERAGE (every state observed):" + nl
             + "- Distance coverage: " + coverage(distanceCoverage, DistanceBucket.values().length) + nl
             + "- Own HP coverage: " + coverage(ownHPCoverage, HPBucket.values().length) + nl
             + "- Mob HP coverage: " + coverage(mobHPCoverage, HPBucket.values().length) + nl
             + "- Actions coverage: " + coverage(actionsCoverage, MinecraftAction.Command.values().length) + nl
             + "-----" + nl
             + stateActionCoverage.summary(nl);
    }

    private static String coverage(EnumSet<?> covered, int total) {
        int percent = (total == 0) ? 0 : Math.round(100f * covered.size() / total);
        return ratio(covered, total) + " (" + percent + "%) " + covered;
    }

    /** Mark a value as covered, in the session metric and in the episode one at once. */
    private void markDistanceAsCovered(DistanceBucket bucket) {
        distanceCoverage.add(bucket);
        episodeDistance.add(bucket);
    }

    private void markOwnHpAsCovered(HPBucket bucket) {
        ownHPCoverage.add(bucket);
        episodeOwnHP.add(bucket);
    }

    private void markMobHpAsCovered(HPBucket bucket) {
        mobHPCoverage.add(bucket);
        episodeMobHP.add(bucket);
    }

    private void markActionAsCovered(MinecraftAction.Command command) {
        actionsCoverage.add(command);
        episodeActions.add(command);
    }

    public String episodeDistanceCoverage() {
        return ratio(episodeDistance, DistanceBucket.values().length);
    }

    public String episodeOwnHpCoverage() {
        return ratio(episodeOwnHP, HPBucket.values().length);
    }

    public String episodeMobHpCoverage() {
        return ratio(episodeMobHP, HPBucket.values().length);
    }

    public String episodeActionsCoverage() {
        return ratio(episodeActions, MinecraftAction.Command.values().length);
    }

    /** State-action coverage reached so far, over the whole session. */
    public String cumulativeStateActionCoverage() {
        return stateActionCoverage.ratio();
    }

    /** How many values were covered out of how many exist. */
    private static String ratio(EnumSet<?> covered, int total) {
        return covered.size() + "/" + total;
    }

    private double combatReward(Float mobHpBefore, Float ownHpBefore) {
        double dealt = 0;
        if (mobHpBefore != null) {
            float mobHpAfter = (lastMobHp == null) ? 0f : lastMobHp;   // gone means dead, i.e. 0 HP
            dealt = Math.max(0f, mobHpBefore - mobHpAfter);
        }

        double taken = 0;
        if (ownHpBefore != null && lastOwnHp != null) {
            taken = Math.max(0f, ownHpBefore - lastOwnHp);
        }

        System.out.println("[COMBAT REWARD] New reward: " + (dealt-taken));
        return dealt - taken;
    }


    private double coverageReward(HPBucket mobHP, HPBucket ownHP, DistanceBucket distance, MinecraftAction action) {
        MinecraftAction.Command command = action.getCommand();

        if (!stateActionCoverage.isInDomain(mobHP, ownHP, distance, command)) {
            System.out.println("[COVERAGE REWARD] Outside the feasible domain, no bonus: "
                    + ownHP + ", " + mobHP + ", " + distance + ", " + action.actionName());
            return 0;
        }

        int visitCount = stateActionCoverage.episodeVisitCount(mobHP, ownHP, distance, command) + 1;
        double bonus = COVERAGE_BONUS / Math.sqrt(visitCount);

        System.out.println("[COVERAGE REWARD] Visit " + visitCount + " of " + ownHP + ", " + mobHP
                + ", " + distance + ", " + action.actionName() + " -> bonus " + bonus);
        return bonus;
    }

    @Override
    public boolean isInTerminalState() {
        return currentState.getMobHpBucket() == HPBucket.DEAD
                || agentDied
                || currentState.getOwnHpBucket() == HPBucket.DEAD
                || updateCycles >= maxActionsPerEpisode;
    }

    /** Get the agent ready to fight and take the first observation of an episode */
    public void startAgentEnvironment() {
        updateCycles = 0;
        lastReward = 0;
        agentDied = false;
        mobKilledThisEpisode = false;
        episodeNumber++;
        tickCounter = 0;

        // Cleared so the equip phase below counts towards the episode it opens
        episodeDistance.clear();
        episodeOwnHP.clear();
        episodeMobHP.clear();
        episodeActions.clear();

        // The reward reads per-episode visit counts: without this the bonus would keep
        // decaying over the whole session and be worth almost nothing after a few episodes
        stateActionCoverage.startEpisode();

        // Re-baseline the death counter before anything else in the episode can kill the agent
        deathsAtEpisodeStart = null;
        state.updateState(AGENT_ID);
        deathsAtEpisodeStart = state.getDeathCount();
        if (deathsAtEpisodeStart == null) {
            throw new RuntimeException("the testbench does not report the death count: "
                    + "update it to se-fbk/MineflayerTestbench main (PR #10 or later), "
                    + "otherwise the agent's death is undetectable");
        }

        Float hp = state.getHealth();
        prevOwnHp = (hp == null) ? 0f : hp;
        
        GoalStructure equip = goalLib.selected(weapon);
        runGoal(equip, maxTicksPerAction, "equip");
        if (!equip.getStatus().success()) {
            throw new RuntimeException("cannot select " + weapon + " (mine.weapon), the agent "
                    + "would fight bare-handed and the run would not be comparable. The weapon has "
                    + "to appear in the hotbar row of the level CSV named by mine.level, which is "
                    + "the first line of the file. Goal status: " + equip.getStatus());
        }

        currentObservation();   // so isInTerminalState() has a fresh observation to read
    }

    /** Start a new episode by rebuilding the arena through the testbench */
    @Override
    public void resetEnvironment() {
        waitUntilAlive();
        minecraftEnv.resetWorker();

        if (minecraftEnv.tagUuids.get(mobTag) == null) {
            throw new RuntimeException("reset succeeded but tag " + mobTag
                    + " is missing from the tag map: the level cannot be played");
        }
        startAgentEnvironment();
    }

    /** Wait for the agent to be back on its feet before rebuilding the level. */
    private void waitUntilAlive() {
        int ticks = 0;
        while (ticks < maxTicksPerAction) {
            state.updateState(AGENT_ID);
            if (state.isAgentAlive())
                return;
            minecraftEnv.waitTicks(AGENT_ID, 1);
            ticks++;
        }
    }
}
