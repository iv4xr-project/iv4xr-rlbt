package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** State coverage of the block targets: how many times each (target, state) of the domains
 *  was reached in the episode, and which ones were reached in the whole session. The reward
 *  reads the episode count, which restarts with every episode, or the bonus would be worth
 *  almost nothing after a few of them. The session coverage is the measure of the results. */
class TargetCoverage {

	private final Map<String, TargetDomain> domains;

	/** "target=state" -> visits in the current episode. */
	private final Map<String, Integer> episodeVisits = new HashMap<>();

	/** "target=state" -> the episode that covered it first, over the whole session. */
	private final Map<String, Integer> firstEpisode = new HashMap<>();

	/** "target=state" reached but not part of the domain of the target: not counted. */
	private final Set<String> outsideDomain = new TreeSet<>();

	private int episode;

	TargetCoverage(Map<String, TargetDomain> domains) {
		this.domains = domains;
	}

	/** Drop the visits and count the states the episode starts in: they are covered from the
	 *  beginning, so going back to one of them is not a first visit. */
	void startEpisode(ExplorerState start) {
		episode++;
		episodeVisits.clear();
		for (String tag : start.blockNames.keySet())
			visit(tag, start);
	}

	/** Count the state a target is in.
	 *  @return its visits in the episode, this one included; 0 if the state is outside the
	 *          domain of the target, which counts nothing */
	int visit(String tag, ExplorerState s) {
		TargetDomain domain = domains.get(tag);
		String state = tag + "=" + s.blockState(tag);
		if (domain == null || !domain.contains(s.blockNames.get(tag), s.blockProperties.get(tag))) {
			outsideDomain.add(state);
			return 0;
		}
		firstEpisode.putIfAbsent(state, episode);
		return episodeVisits.merge(state, 1, Integer::sum);
	}

	/** States covered in the episode. */
	int covered() {
		return episodeVisits.size();
	}

	/** States covered in the session so far, by any episode. */
	int sessionCovered() {
		return firstEpisode.size();
	}

	/** One row per state of the domains: whether the session covered it and in which episode
	 *  first. The file is rewritten whole, so it can be refreshed after every episode. */
	void writeStates(File file) throws IOException {
		try (PrintStream out = new PrintStream(file)) {
			out.println("target,state,covered,first_episode");
			for (TargetDomain domain : domains.values())
				for (String state : domain.allStates()) {
					Integer first = firstEpisode.get(domain.tag + "=" + state);
					out.println(domain.tag + ",\"" + state + "\"," + (first != null) + ","
							+ (first == null ? "" : first));
				}
		}
	}

	/** The session coverage in words: the total, then each target with the states it still
	 *  misses, then the states reached outside the domains. */
	String summary() {
		StringBuilder sb = new StringBuilder("States covered in the session: " + sessionCovered() + "/" + total()
				+ String.format(Locale.ROOT, " (%.1f%%)", 100.0 * sessionCovered() / Math.max(1, total())));
		for (TargetDomain domain : domains.values()) {
			Set<String> missing = new TreeSet<>();
			for (String state : domain.allStates())
				if (!firstEpisode.containsKey(domain.tag + "=" + state))
					missing.add(state);
			sb.append(System.lineSeparator()).append("  ").append(domain.tag).append(": ")
					.append(domain.states() - missing.size()).append("/").append(domain.states());
			if (!missing.isEmpty())
				sb.append(", missing ").append(missing);
		}
		sb.append(System.lineSeparator()).append("States reached outside the domains (not counted): ")
				.append(outsideDomain.isEmpty() ? "none" : outsideDomain);
		return sb.toString();
	}

	/** States of all the domains. */
	int total() {
		int total = 0;
		for (TargetDomain domain : domains.values())
			total += domain.states();
		return total;
	}
}
