/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits.rewardfunctions;

import java.util.HashMap;
import java.util.List;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import eu.fbk.iv4xr.rlbt.distance.StateDistance;
import eu.fbk.iv4xr.rlbt.rewardfunction.AbstractRlbtRewardFunction;
import eu.iv4xr.framework.spatial.Vec3;
import world.BeliefState;

/**
 * @author kifetew
 *
 */
public class GoalOrientedRewardFunction extends AbstractRlbtRewardFunction {

	
	//store visited states from environment in an episode
	HashMap<String, Integer> visitedStates = null;//new HashMap<String, Integer>();
	int stateOccuranceThreshold =4;
	
	public GoalOrientedRewardFunction(StateDistance stateDistanceFunction) {
		super(stateDistanceFunction);
		
	}
	
	/*reset the state memory buffer*/
	@Override
	public void resetStateBuffer() {
		visitedStates = new HashMap<String, Integer>();		
	}
	
	@Override
	public double reward(State previousState, Action action, State currentState, BeliefState agentBeliefState) {
		double reward = 0;
		// check if the agent was stuck, don't penalize it
		if (agentBeliefState.isStuck()) {
			reward = 0;			
			//System.out.println("Action  = "+action.actionName()+"  Stuck , reward = "+reward);
		} else {
			//first - consider explored states- give reward for exploring a new state
			double sim = getStateDistanceFunction().distance(previousState, currentState); // getStatesSimilarityMeasures(state1,state2); 
			double dissimilarity = (1-sim);
			int stateOccurance = 0;
			stateOccurance =  getNumofStateOccurance(currentState.toString());
			// give reward for exploring a new quite different state
			if (dissimilarity >=0.2 && stateOccurance<=stateOccuranceThreshold) {
				reward = reward+ (dissimilarity*10);
				//System.out.println("Action  = "+action.actionName()+" Dissimilarity and fewer State Occurance, reward = "+reward);
			}
			//give penalty for exploring same state 
			if (stateOccurance>stateOccuranceThreshold)
			{
				reward = reward - (dissimilarity*10);
				//System.out.println("Action  = "+action.actionName()+"  State visited over threshold,  penalty = "+reward);
			}
			//System.out.println("dissimilarity = "+dissimilarity+"  statevisited = "+stateOccurance);
			// second -  consider agent's movement - reward movement, penalize staying at the same position
			List<Vec3> recentPositions = agentBeliefState.getRecentPositions();
			if (recentPositions.size() >= 2) {//(recentPositions.size() >= 2) {
				if (recentPositions.get(recentPositions.size()-1).equals(recentPositions.get(recentPositions.size()-2))) {
					//reward = -1;
					reward= reward-1;
					//System.out.println("Action  = "+action.actionName()+"  agent moving around same position, penalty = "+reward);
				} else {
					reward = reward -0;
					//System.out.println("Action  = "+action.actionName()+"  State visited over threshold,  penalty = "+reward);
					//reward = 0;
				}
			}else {
				// means did not move enough to have more recent positions
				reward=reward-1;
				//System.out.println("Action  = "+action.actionName()+" agent did not move enough to get more position,  penalty = "+reward);
				//reward = -1;
			}
		}
		return reward;
	}

	/** 
	 * Check the memory of visited states and returns the number of times this state is visited
	 * 
	 */
	private int getNumofStateOccurance(String state2) {
		int numoftimesvisited =0;
		if (visitedStates.containsKey(state2.toString())){
			numoftimesvisited = visitedStates.get(state2.toString());
			visitedStates.put(state2.toString(), (numoftimesvisited+1));
			return numoftimesvisited;
		}else {  // make a new entry for this state
			visitedStates.put(state2.toString(), 1);
			return numoftimesvisited;
		}
	}


}
