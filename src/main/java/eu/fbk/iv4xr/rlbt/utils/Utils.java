/**
 * 
 */
package eu.fbk.iv4xr.rlbt.utils;

import java.util.Collection;
import java.util.Random;

import org.apache.commons.math3.random.MersenneTwister;

import eu.iv4xr.framework.mainConcepts.WorldEntity;
import world.LabEntity;
import world.LabWorldModel;


/**
 * @author kifetew
 *
 */
public class Utils {

	private static MersenneTwister random = new MersenneTwister(System.currentTimeMillis());
	/**
	 * 
	 */
	private Utils() {
	}

	
	public static <T> T choice(Collection<T> set) {
		if (set.isEmpty())
			return null;

		int position = random.nextInt(set.size());
		return (T) set.toArray()[position];
	}
	
	
	/**
	 * returns a string representation of the Wom for use in debug messages
	 * @param model
	 * @return
	 */
	public static String WomtoString(LabWorldModel model) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("[");
		for (WorldEntity e : model.elements.values()) {
			
			LabEntity entity = (LabEntity) e;
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
