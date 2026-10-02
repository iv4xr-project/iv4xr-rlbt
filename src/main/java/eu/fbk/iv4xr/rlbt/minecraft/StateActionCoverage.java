package eu.fbk.iv4xr.rlbt.minecraft;

import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.DistanceBucket;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftBurlapState.HPBucket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** State-action coverage over the tuple (distance, own HP, mob HP, action) */
public class StateActionCoverage {

    private static final List<String> FEATURES =
            Arrays.asList("distance", "own HP", "mob HP", "action");

    /** Feasible values, feature by feature. Only DEAD is left out, because it is the
        terminal bucket: CRITICAL is a live agent below 25% health and belongs in here,
        or the tightest fights would earn no coverage and no reward at all. */
    private static final List<List<Object>> DOMAIN = Arrays.asList(
            Arrays.asList(DistanceBucket.values()),
            Arrays.asList(HPBucket.CRITICAL, HPBucket.LOW, HPBucket.MEDIUM, HPBucket.HIGH),
            Arrays.asList(HPBucket.CRITICAL, HPBucket.LOW, HPBucket.MEDIUM, HPBucket.HIGH),
            Arrays.asList(MinecraftAction.Command.values()));

    /** Visits per tuple over the whole session and over an episode */
    private final Map<List<Object>, Integer> visits = new LinkedHashMap<>();
    private final Map<List<Object>, Integer> episodeVisits = new HashMap<>();

    private int outsideDomain;

    public void record(DistanceBucket distance, HPBucket ownHp, HPBucket mobHp,
                       MinecraftAction.Command action) {
        List<Object> tuple = Arrays.asList((Object) distance, ownHp, mobHp, action);
        if (!feasible(tuple)) {
            outsideDomain++;
            return;
        }
        visits.merge(tuple, 1, Integer::sum);
        episodeVisits.merge(tuple, 1, Integer::sum);
    }

    /** Drop the per-episode counts. The session-wide ones are left untouched. */
    public void startEpisode() {
        episodeVisits.clear();
    }

    private static boolean feasible(List<Object> tuple) {
        for (int i = 0; i < DOMAIN.size(); i++)
            if (!DOMAIN.get(i).contains(tuple.get(i)))
                return false;
        return true;
    }

    /** Terminal configurations sit outside it and must not earn a bonus */
    boolean isInDomain(HPBucket mobHP, HPBucket ownHP, DistanceBucket distance, MinecraftAction.Command action) {
        return feasible(Arrays.asList((Object) distance, ownHP, mobHP, action));
    }

    /** How many times the tuple was already visited in the current episode */
    int episodeVisitCount(HPBucket mobHP, HPBucket ownHP, DistanceBucket distance, MinecraftAction.Command action) {
        List<Object> tuple = Arrays.asList((Object) distance, ownHP, mobHP, action);
        return episodeVisits.getOrDefault(tuple, 0);
    }

    /** Combinations covered out of every feasible one. */
    public int covered() {
        return visits.size();
    }

    public int feasible() {
        int total = 1;
        for (List<Object> values : DOMAIN)
            total *= values.size();
        return total;
    }

    public String ratio() {
        return covered() + "/" + feasible();
    }

    private static List<List<Object>> allCombinations() {
        List<List<Object>> combinations = new ArrayList<>();
        combinations.add(new ArrayList<>());
        for (List<Object> values : DOMAIN) {
            List<List<Object>> grown = new ArrayList<>();
            for (List<Object> prefix : combinations)
                for (Object value : values) {
                    List<Object> tuple = new ArrayList<>(prefix);
                    tuple.add(value);
                    grown.add(tuple);
                }
            combinations = grown;
        }
        return combinations;
    }

    private static String key(List<Object> tuple) {
        StringBuilder sb = new StringBuilder();
        for (Object value : tuple) {
            if (sb.length() > 0) sb.append('|');
            sb.append(value);
        }
        return sb.toString();
    }

    /** The coverage block of summary.txt: the combinations exercised and the ones left. */
    public String summary(String nl) {
        List<String> done = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (List<Object> tuple : allCombinations())
            (visits.containsKey(tuple) ? done : missing).add(key(tuple));
        
        StringBuilder sb = new StringBuilder();
        sb.append("STATE-ACTION COVERAGE (").append(String.join(" x ", FEATURES)).append(')').append(nl);
        sb.append("    Feasible combinations: ").append(dimensions())
          .append(" = ").append(feasible())
          .append(" (terminal states excluded)").append(nl);
        sb.append("    Covered: ").append(ratio())
          .append(" (").append(Math.round(100f * covered() / feasible())).append("%)").append(nl);
        sb.append("    Done: [").append(String.join(", ", done)).append(']').append(nl);
        sb.append("    Missing: [").append(String.join(", ", missing)).append(']').append(nl);

        if (outsideDomain > 0)
            sb.append("    WARNING: ").append(outsideDomain)
              .append(" observations fell outside the feasible domain").append(nl);
        return sb.toString();
    }

    private static String dimensions() {
        StringBuilder sb = new StringBuilder();
        for (List<Object> values : DOMAIN) {
            if (sb.length() > 0) sb.append('*');
            sb.append(values.size());
        }
        return sb.toString();
    }
}
