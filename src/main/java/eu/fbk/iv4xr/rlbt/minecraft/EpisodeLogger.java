package eu.fbk.iv4xr.rlbt.minecraft;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;

import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.DistanceBucket;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.HPBucket;
import eu.iv4xr.framework.spatial.Vec3;

public class EpisodeLogger implements AutoCloseable {

    private static final String TICKS_HEADER =
            "episode,tick,phase,goal_status,own_hp,own_x,own_y,own_z,mob_hp,mob_x,mob_y,mob_z,dist";

    private static final String ACTIONS_HEADER =
            "id,episode,action,target,param,goal_status,"
          + "dist_bucket_before,dist_bucket_after,"
          + "mob_hp_before,mob_hp_after,mob_hp_bucket_before,mob_hp_bucket_after,damage_dealt,"
          + "own_hp_before,own_hp_after,own_hp_bucket_before,own_hp_bucket_after,damage_taken,"
          + "hit_landed";

    private final BufferedWriter ticks;
    private final BufferedWriter actions;
    private int actionId = 1;

    /** Open ticks.csv and actions.csv in the given session directory and write their headers */
    public EpisodeLogger(File sessionDir) {
        try {
            ticks   = new BufferedWriter(new FileWriter(new File(sessionDir, "ticks.csv")));
            actions = new BufferedWriter(new FileWriter(new File(sessionDir, "actions.csv")));
            ticks.write(TICKS_HEADER);     ticks.newLine();
            actions.write(ACTIONS_HEADER); actions.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open session CSV files in " + sessionDir, e);
        }
    }

    /** One row of state telemetry: call it every tick.
     * Positions are split into x/y/z columns so no comma leaks into a cell; nulls become empty cells */
    public void logTick(int episode, int tick, String phase, String goalStatus,
                        Float ownHp, Vec3 ownPos, Float mobHp, Vec3 mobPos, Double dist) {
        writeRow(ticks, join(
                num(episode), num(tick), csv(phase), csv(goalStatus),
                num(ownHp), x(ownPos), y(ownPos), z(ownPos),
                num(mobHp), x(mobPos), y(mobPos), z(mobPos), num(dist)));
    }

    /** One row per performed action. The buckets are the ones the coverage metric records,
     * so a row is enough to tell which combination an action was chosen in. */
    public void logAction(int episode, String action, String target, String param, String goalStatus,
                          DistanceBucket distBucketBefore, DistanceBucket distBucketAfter,
                          Float mobHpBefore, Float mobHpAfter,
                          HPBucket mobBucketBefore, HPBucket mobBucketAfter,
                          Float ownHpBefore, Float ownHpAfter,
                          HPBucket ownBucketBefore, HPBucket ownBucketAfter) {
        // the mob entity disappears when it dies, so a missing HP after a hit means 0
        Float dealt = (mobHpBefore == null) ? null
                : mobHpBefore - (mobHpAfter == null ? 0f : mobHpAfter);
        Float taken = (ownHpBefore != null && ownHpAfter != null) ? ownHpBefore - ownHpAfter : null;
        boolean hitLanded = dealt != null && dealt > 0f;
        writeRow(actions, join(
                num(actionId++), num(episode), csv(action), csv(target), csv(param), csv(goalStatus),
                name(distBucketBefore), name(distBucketAfter),
                num(mobHpBefore), num(mobHpAfter), name(mobBucketBefore), name(mobBucketAfter), num(dealt),
                num(ownHpBefore), num(ownHpAfter), name(ownBucketBefore), name(ownBucketAfter), num(taken),
                String.valueOf(hitLanded)));
    }

    /** Same row without the bucket columns, which are left empty */
    public void logAction(int episode, String action, String target, String param, String goalStatus,
                          Float mobHpBefore, Float mobHpAfter, Float ownHpBefore, Float ownHpAfter) {
        logAction(episode, action, target, param, goalStatus,
                null, null,
                mobHpBefore, mobHpAfter, null, null,
                ownHpBefore, ownHpAfter, null, null);
    }

    @Override
    public void close() {
        closeQuietly(ticks);
        closeQuietly(actions);
    }


    /// Helpers
    private static void writeRow(BufferedWriter w, String row) {
        try {
            w.write(row);
            w.newLine();
            w.flush();   // per-row flush = crash-safe; drop it for higher throughput
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String join(String... cells) {
        return String.join(",", cells);
    }

    private static String num(Number n) {
        return n == null ? "" : n.toString();
    }

    /** An enum value as its name, or an empty cell when it is not available. */
    private static String name(Enum<?> e) {
        return e == null ? "" : e.name();
    }

    private static String x(Vec3 v) { return v == null ? "" : Float.toString(v.x); }
    private static String y(Vec3 v) { return v == null ? "" : Float.toString(v.y); }
    private static String z(Vec3 v) { return v == null ? "" : Float.toString(v.z); }

    /** Quote a field only if it contains comma, quote or newline */
    private static String csv(String s) {
        if (s == null)
            return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static void closeQuietly(BufferedWriter w) {
        try {
            if (w != null)
                w.close();
        } catch (IOException ignored) {
            // best effort on close
        }
    }
}
