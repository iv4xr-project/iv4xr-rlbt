/**
 * 
 */
package eu.fbk.iv4xr.rlbt.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.*;

import burlap.behavior.singleagent.Episode;
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

	/**
	 * 
	 * @param episode
	 * @param outputFile the base name onto which the file name extension will be appended
	 */
	public static void serializeEpisode (Episode episode, String outputFile) {
		if (episode != null) {
			String serializedEpisode = episode.serialize();
			try {
				FileUtils.writeStringToFile(new File (outputFile + ".yaml"), serializedEpisode, Charset.defaultCharset());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * 
	 * @param episodes
	 * @param outputFile the base name onto which the episode number and file name extension will be appended
	 * 		e.g., outputFile = "/path/level" => {/path/level_1.yaml, /path/level_2.yaml, ...}
	 */
	public static void serializeEpisodes (List<Episode> episodes, String outputFile) {
		if (episodes != null) {
			for (int i = 0; i < episodes.size(); i++) {
				Episode episode  = episodes.get(i);
				String serializedEpisode = episode.serialize();
				try {
					FileUtils.writeStringToFile(new File (outputFile + "_" + (i+1) + ".yaml"), serializedEpisode, Charset.defaultCharset());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
}
