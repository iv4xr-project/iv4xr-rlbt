package eu.fbk.iv4xr.rlbt.distance;

import burlap.mdp.core.state.State;

/**
 * A {@link StateDistance} that never merges two states: every state is only ever
 * equal to itself, as decided by the hashing factory.
 *
 * This is the right choice for a SUT whose state is already an abstraction, i.e.
 * a handful of discretised features (as in the Minecraft port, see PROJECT.md
 * §2.7). There the generalisation is done upstream by the bucketisation: two
 * states either land in the same bucket or describe genuinely different
 * situations, and merging them further would collapse buckets that were kept
 * apart on purpose.
 *
 * Contrast with {@code JaccardDistance}, which exists to paper over the partial
 * observability of LabRecruits: there the agent sees a different subset of the
 * world at each step, so two observations of the same underlying world look
 * different and have to be reconciled. That problem does not arise with a
 * feature-vector state.
 *
 * @author RLbT Minecraft port
 */
public class NoStateSimilarity implements StateDistance {

    @Override
    public double distance(State s1, State s2) {
        return s1.equals(s2) ? 0.0 : 1.0;
    }

    @Override
    public boolean subsume(State s1, State s2) {
        return false;
    }

    @Override
    public double statesimilarity(State s1, State s2) {
        return 0.0;
    }
}
