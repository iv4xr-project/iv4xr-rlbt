package eu.fbk.iv4xr.rlbt.minecraft;

import burlap.mdp.core.oo.state.ObjectInstance;
import burlap.mdp.core.state.MutableState;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.DistanceBucket;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.HPBucket;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The single feature object held by a {@link MinecraftBurlapState}: the whole RL
 * state as a handful of already-bucketised combat variables */
public class MinecraftFeatureObject implements ObjectInstance, MutableState, Serializable {

    private static final long serialVersionUID = 1L;

    /** Object class name, as seen by BURLAP. */
    public static final String CLASS_NAME = "combatFeatures";

    /** Name of the single instance living in a state. */
    public static final String OBJECT_NAME = "features";

    /** Variable keys. These are what ends up in the state hash. */
    public static final String KEY_ENEMY_DISTANCE = "enemyDistance";
    public static final String KEY_OWN_HP = "ownHp";
    public static final String KEY_MOB_HP = "mobHp";

    private static final List<Object> KEYS = Collections.unmodifiableList(
            Arrays.<Object>asList(KEY_ENEMY_DISTANCE, KEY_OWN_HP, KEY_MOB_HP));

    private DistanceBucket enemyDistance;
    private HPBucket ownHp;
    private HPBucket mobHp;

    private String objectName = OBJECT_NAME;

    public MinecraftFeatureObject() {
        this(DistanceBucket.UNSEEN, HPBucket.HIGH, HPBucket.HIGH);
    }

    public MinecraftFeatureObject(DistanceBucket enemyDistance, HPBucket ownHp, HPBucket mobHp) {
        this.enemyDistance = enemyDistance;
        this.ownHp = ownHp;
        this.mobHp = mobHp;
    }

    public DistanceBucket getEnemyDistance() { return enemyDistance; }
    public HPBucket getOwnHp() { return ownHp; }
    public HPBucket getMobHp() { return mobHp; }

    @Override
    public List<Object> variableKeys() {
        return KEYS;
    }

    @Override
    public Object get(Object variableKey) {
        if (KEY_ENEMY_DISTANCE.equals(variableKey)) return enemyDistance;
        if (KEY_OWN_HP.equals(variableKey)) return ownHp;
        if (KEY_MOB_HP.equals(variableKey)) return mobHp;
        throw new IllegalArgumentException("Unknown feature key: " + variableKey);
    }

    @Override
    public MutableState set(Object variableKey, Object value) {
        if (KEY_ENEMY_DISTANCE.equals(variableKey)) {
            enemyDistance = (DistanceBucket) value;
        } else if (KEY_OWN_HP.equals(variableKey)) {
            ownHp = (HPBucket) value;
        } else if (KEY_MOB_HP.equals(variableKey)) {
            mobHp = (HPBucket) value;
        } else {
            throw new IllegalArgumentException("Unknown feature key: " + variableKey);
        }
        return this;
    }

    @Override
    public MinecraftFeatureObject copy() {
        return (MinecraftFeatureObject) copyWithName(objectName);
    }

    @Override
    public ObjectInstance copyWithName(String objectName) {
        MinecraftFeatureObject copy = new MinecraftFeatureObject(enemyDistance, ownHp, mobHp);
        copy.objectName = objectName;
        return copy;
    }

    @Override
    public String className() {
        return CLASS_NAME;
    }

    @Override
    public String name() {
        return objectName;
    }

    /** Compact signature of the features, e.g. "IN_REACH|HIGH|MEDIUM". */
    @Override
    public String toString() {
        return enemyDistance + "|" + ownHp + "|" + mobHp;
    }
}
