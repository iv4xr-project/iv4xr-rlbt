/**
 * 
 */
package eu.fbk.iv4xr.rlbt.rewardfunction;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import world.BeliefState;

/**
 * @author kifetew
 *
 */
public interface RlbtRewardFunction {
	public double reward(State previousState, Action action, State currentState, BeliefState agentBeliefState);
}
