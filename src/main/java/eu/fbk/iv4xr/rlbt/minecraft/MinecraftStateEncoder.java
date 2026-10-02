package eu.fbk.iv4xr.rlbt.minecraft;

import burlap.mdp.core.state.State;
import eu.fbk.iv4xr.rlbt.StateEncoder;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.DistanceBucket;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.HPBucket;

/** Encodes a {@link MinecraftBurlapState} as three concatenated one-hot blocks, one per state variable:
 *   [0 ... 3]    enemyDistance : IN_REACH, OUT_OFcaude_REACH, FAR, UNSEEN
 *   [4 ... 8]    ownHp         : DEAD, CRITICAL, LOW, MEDIUM, HIGH
 *   [9 ... 13]   mobHp         : DEAD, CRITICAL, LOW, MEDIUM, HIGH    */
public class MinecraftStateEncoder implements StateEncoder {

    private static final int DISTANCE_SIZE = DistanceBucket.values().length;
    private static final int HP_SIZE = HPBucket.values().length;

    /** Offsets of each one-hot block within the feature vector. */
    private static final int DISTANCE_OFFSET = 0;
    private static final int OWN_HP_OFFSET = DISTANCE_OFFSET + DISTANCE_SIZE;
    private static final int MOB_HP_OFFSET = OWN_HP_OFFSET + HP_SIZE;

    private static final int INPUT_SIZE = MOB_HP_OFFSET + HP_SIZE;

    @Override
    public int inputSize() {
        return INPUT_SIZE;
    }

    @Override
    public float[] features(State s) {
        MinecraftBurlapState mcs = (MinecraftBurlapState) s;
        float[] features = new float[INPUT_SIZE];

        features[DISTANCE_OFFSET + mcs.getEnemyDistance().ordinal()] = 1.0f;
        features[OWN_HP_OFFSET + mcs.getOwnHpBucket().ordinal()] = 1.0f;
        features[MOB_HP_OFFSET + mcs.getMobHpBucket().ordinal()] = 1.0f;

        return features;
    }

    @Override
    public String describe() {
        return "Minecraft one-hot buckets: dist(" + DISTANCE_SIZE + ") + ownHp(" + HP_SIZE
                + ") + mobHp(" + HP_SIZE + ") = " + INPUT_SIZE;
    }
}
