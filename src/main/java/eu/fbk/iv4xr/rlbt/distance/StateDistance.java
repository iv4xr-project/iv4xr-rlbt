/**
 * 
 */
package eu.fbk.iv4xr.rlbt.distance;

import burlap.mdp.core.state.State;

/**
 * Interface for implementations that calculate the distance between two states
 * 
 * @author kifetew
 *
 */
public interface StateDistance {
	public double distance (State s1, State s2);
}