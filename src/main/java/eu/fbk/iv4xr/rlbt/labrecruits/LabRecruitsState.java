/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits;

import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;

import burlap.mdp.core.oo.state.ObjectInstance;
import burlap.mdp.core.oo.state.generic.GenericOOState;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import world.LabEntity;

/**
 * @author kifetew
 *
 */
public class LabRecruitsState extends GenericOOState implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2179112444014557064L;

	// add LabRecruits specific code here
	
	
	/**
	 * Construct the inital state
	 * @param initial
	 */
	public LabRecruitsState(boolean initial) {
		super();
		
	}

	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("[");
		Map<String, ObjectInstance> objectsMap = getObjectsMap();
		for (Entry<String , ObjectInstance> entry : objectsMap.entrySet()) {
			String name = entry.getKey();
			LabRecruitsEntityObject labEntityObject = (LabRecruitsEntityObject) entry.getValue();
			
			LabEntity entity = (LabEntity) labEntityObject.get(name);
			String property = "";
			if (entity.type.equalsIgnoreCase(LabEntity.DOOR)) {
				property += entity.getBooleanProperty("isOpen");
			}else if (entity.type.equalsIgnoreCase(LabEntity.SWITCH)){
				property += entity.getBooleanProperty("isOn");
			}
				
			buffer.append(entity.id + " (" + property + ")" + ",");
		}
		buffer.deleteCharAt(buffer.length()-1);
		buffer.append("]");
		return buffer.toString();
	}

}
