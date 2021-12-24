/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits;

import java.io.Serializable;
import java.util.Arrays;

import burlap.mdp.core.state.State;
import burlap.statehashing.simple.IISimpleHashableState;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import world.LabEntity;

/**
 * @author kifetew
 *
 */
public class RlbtSimpleHashableState extends IISimpleHashableState implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5127590788733589119L;


	/**
	 * 
	 */
	public RlbtSimpleHashableState() {
		super();
	}

	/**
	 * @param s
	 */
	public RlbtSimpleHashableState(State s) {
		super(s);
	}

	/**
	 * Returns whether two values are equal.
	 * @param key the state variable key
	 * @param v1 the first value to compare
	 * @param v2 the second value to compare
	 * @return true if v1 = v2; false otherwise
	 */
	@Override
	protected boolean valuesEqual(Object key, Object v1, Object v2){
		if(v1.getClass().isArray() && v2.getClass().isArray()){
			return Arrays.equals((Object[])v1, (Object[])v2);
		}
		return hasSameState(v1, v2);
	}
	
	
	private boolean hasSameState(Object e1, Object e2) {
		WorldEntity entity1;
		WorldEntity entity2;
		if (e1 instanceof WorldEntity && e2 instanceof WorldEntity) {
			entity1 = (WorldEntity)e1;
			entity2 = (WorldEntity)e2;
		}else {
			return false;
		}
		if (!entity1.type.contentEquals(entity2.type)) return false;
		if (!entity1.id.contentEquals(entity2.id)) return false;
		if (! entity1.position.equals(entity2.position) || ! entity1.extent.equals(entity2.extent)) return false ;
//		if (!entity1.dynamic) return true ;
		switch(entity1.type) {
		  case LabEntity.FIREHAZARD : return true;
		  case LabEntity.GOAL : return true;
		  case LabEntity.DOOR   : return entity1.getBooleanProperty("isOpen") == entity2.getBooleanProperty("isOpen") ;
		  case LabEntity.SWITCH : return entity1.getBooleanProperty("isOn") == entity2.getBooleanProperty("isOn") ;
		  case LabEntity.COLORSCREEN : return entity1.getProperty("color").equals(entity2.getProperty("color")) ;
		}
		return false ;
	}
}
