/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits;

import burlap.mdp.core.state.State;
import burlap.statehashing.HashableState;
import burlap.statehashing.HashableStateFactory;

/**
 * @author kifetew
 *
 */
public class RlbtHashableStateFactory implements HashableStateFactory {

	/**
	 * 
	 */
	public RlbtHashableStateFactory() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public HashableState hashState(State s) {
		return new RlbtSimpleHashableState(s);
	}

}
