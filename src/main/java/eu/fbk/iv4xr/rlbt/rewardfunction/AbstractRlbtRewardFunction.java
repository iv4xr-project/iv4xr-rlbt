/**
 * 
 */
package eu.fbk.iv4xr.rlbt.rewardfunction;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import eu.fbk.iv4xr.rlbt.distance.StateDistance;
import world.BeliefState;

/**
 * @author kifetew
 *
 */
public class AbstractRlbtRewardFunction implements RlbtRewardFunction {

	private StateDistance stateDistanceFunction;
	
	public AbstractRlbtRewardFunction() {
		
	}
	
	/**
	 * 
	 */
	public AbstractRlbtRewardFunction(StateDistance stateDistanceFunction) {
		this.stateDistanceFunction = stateDistanceFunction;
	}

	@Override
	public double reward(State previousState, Action action, State currentState, BeliefState agentBeliefState) {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * @return the stateDistanceFunction
	 */
	public StateDistance getStateDistanceFunction() {
		return stateDistanceFunction;
	}

	/**
	 * @param stateDistanceFunction the stateDistanceFunction to set
	 */
	public void setStateDistanceFunction(StateDistance stateDistanceFunction) {
		this.stateDistanceFunction = stateDistanceFunction;
	}
	
	@Override
	public void resetStateBuffer() {
		// TODO Auto-generated method stub
		
	}

}
