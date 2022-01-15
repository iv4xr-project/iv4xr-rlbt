/**
 * 
 */
package eu.fbk.iv4xr.rlbt.utils;

import java.util.Collection;
import java.util.Random;

import org.apache.commons.math3.random.MersenneTwister;


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
	
}
