package eu.fbk.iv4xr.rlbt.minecraft;

import burlap.mdp.core.oo.state.OOState;
import burlap.mdp.core.oo.state.generic.GenericOOState;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** BURLAP state for the Minecraft combat scenario.
    The state is a small vector of already-discretized combat
    features: the distance to the enemy and the health of both fighters. Raw
    float positions and HP would make the state space effectively continuous, and
    the bucketization doubles as the definition of state coverage. */
public class MinecraftBurlapState extends GenericOOState implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * IN_REACH      ATTACK lands
     * OUT_OF_REACH  ATTACK misses, RETREAT still has somewhere to go
     * FAR           RETREAT has no destination either
     * UNSEEN        the testbench does not report the enemy
     */
    public enum DistanceBucket {
        IN_REACH,
        OUT_OF_REACH,
        FAR,
        UNSEEN
    }

    /**
     * DEAD
     * CRITICAL < 25%
     * LOW      25 - 50%
     * MEDIUM   50% - 75%
     * HIGH     > 75%
     */
    public enum HPBucket {
        DEAD,
        CRITICAL,
        LOW,
        MEDIUM,
        HIGH
    }

    /** Attack reach in blocks, measured on the outdoor1 logs with zombie and iron sword:
        every hit landed below 4.60, every miss happened from 4.63 on. */
    public static final double ATTACK_REACH = 4.6;
        
    /** Past this distance RETREAT has no destination to aim for. */
    public static final double RETREAT_RANGE = 10.0;

    /** Health boundaries as a fraction of maximum health.ì */
    public static final float HP_CRITICAL_RATIO = 0.25f;
    public static final float HP_LOW_RATIO = 0.50f;
    public static final float HP_HIGH_RATIO = 0.75f;

    /** Maximum health of the agent. A Minecraft player has 10 hearts. */
    public static final float PLAYER_MAX_HP = 20f;

    /** Fallback used by {@link #maxHpForMob(String)} for unlisted mobs. */
    public static final float DEFAULT_MOB_MAX_HP = 20f;

    /** Maximum health of the mobs used in the arena levels. Extend as needed. */
    private static final Map<String, Float> MOB_MAX_HP;
    static {
        Map<String, Float> m = new HashMap<>();
        m.put("zombie", 20f);
        m.put("skeleton", 20f);
        m.put("creeper", 20f);
        m.put("spider", 16f);
        m.put("enderman", 40f);
        MOB_MAX_HP = Collections.unmodifiableMap(m);
    }

    public MinecraftBurlapState() {
        super();
        addObject(new MinecraftFeatureObject());
    }

    /** Copy constructor: takes over the objects of {@code src}, features included. */
    public MinecraftBurlapState(OOState src) {
        super(src);
    }


    /** GETTERS */
    public MinecraftFeatureObject getFeatures() {
        return (MinecraftFeatureObject) object(MinecraftFeatureObject.OBJECT_NAME);
    }

    public DistanceBucket getEnemyDistance() { return getFeatures().getEnemyDistance(); }
    public HPBucket getOwnHpBucket() { return getFeatures().getOwnHp(); }
    public HPBucket getMobHpBucket() { return getFeatures().getMobHp(); }


    /** Recompute the whole abstraction from a fresh observation. */
    public void updateAbstraction(Float ownHp, Float mobHp, Float mobMaxHp, Double enemyDistance) {
        addObject(new MinecraftFeatureObject(
                distanceBucket(enemyDistance),
                hpBucket(ownHp, PLAYER_MAX_HP),
                hpBucket(mobHp, mobMaxHp == null ? DEFAULT_MOB_MAX_HP : mobMaxHp)));
    }

    /** Same as {@link #updateAbstraction(Float, Float, Float, Double)} for an enemy
     * whose maximum health is the standard 20 HP */
    public void updateAbstraction(Float ownHp, Float mobHp, Double enemyDistance) {
        updateAbstraction(ownHp, mobHp, DEFAULT_MOB_MAX_HP, enemyDistance);
    }

    /** Maximum health of a mob given its entity name */
    public static float maxHpForMob(String entityName) {
        if (entityName == null)
            return DEFAULT_MOB_MAX_HP;
        Float max = MOB_MAX_HP.get(entityName.toLowerCase());
        return max == null ? DEFAULT_MOB_MAX_HP : max;
    }

    /** Discretize a health value relative to the maximum health of its owner */
    public static HPBucket hpBucket(Float hp, float maxHp) {
        if (hp == null || hp <= 0f || maxHp <= 0f) return HPBucket.DEAD;

        float ratio = hp / maxHp;
        if (ratio < HP_CRITICAL_RATIO) return HPBucket.CRITICAL;
        if (ratio < HP_LOW_RATIO) return HPBucket.LOW;
        if (ratio < HP_HIGH_RATIO) return HPBucket.MEDIUM;
        return HPBucket.HIGH;
    }

    /** Discretize a distance in blocks */
    public static DistanceBucket distanceBucket(Double distance) {
        if (distance == null) return DistanceBucket.UNSEEN;
        if (distance < ATTACK_REACH) return DistanceBucket.IN_REACH;
        if (distance < RETREAT_RANGE) return DistanceBucket.OUT_OF_REACH;
        return DistanceBucket.FAR;
    }

    /** Compact signature of the abstraction, e.g. "IN_REACH|HIGH|MEDIUM". Two states
        sharing this key are indistinguishable to the learner */
    public String abstractionKey() {
        return getFeatures().toString();
    }

    @Override
    public MinecraftBurlapState copy() {
        return new MinecraftBurlapState(this);
    }

    @Override
    public String toString() {
        return "[" + abstractionKey() + "]";
    }
}
