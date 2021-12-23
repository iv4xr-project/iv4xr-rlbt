/**
 * 
 */
package eu.fbk.iv4xr.rlbt.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.Map;

import org.apache.commons.lang3.*;

import burlap.behavior.singleagent.learning.tdmethods.QLearningStateNode;
import burlap.statehashing.HashableState;

/**
 * @author kifetew
 *
 */
public class SerializationUtil {

	
	public static void saveQTable (Map<HashableState, QLearningStateNode> qTable, String outputFile) throws FileNotFoundException {
		SerializationUtils.serialize((Serializable) qTable, new FileOutputStream(outputFile));
	}
	
	
	public static Map<HashableState, QLearningStateNode> loadQTable (String serializedFile) throws FileNotFoundException{
		Map<HashableState, QLearningStateNode> qTable = (Map<HashableState, QLearningStateNode>) SerializationUtils.deserialize(new FileInputStream(serializedFile));
		return qTable;
	}
	
	public static String serializeToString (Serializable obj) {
		byte[] bytes = SerializationUtils.serialize(obj);
		String ser = Base64.getEncoder().encodeToString(bytes);
		return ser;
	}
	
	
	public static Serializable deserializeFromString (String ser) {
		byte[] bytes = Base64.getDecoder().decode(ser);
		Object obj = SerializationUtils.deserialize(bytes);
		return (Serializable)obj;
	}

}
