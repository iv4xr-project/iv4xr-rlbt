package eu.fbk.iv4xr.rlbt.configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Burlap parameters
 * @author prandi
 *
 */
public class BurlapConfiguration extends Configuration{

	
	public BurlapConfiguration() {
		parameters = new LinkedHashMap<String, Object>();
		parameters.put("burlap.max_update_cycles",(int) 400);
		parameters.put("burlap.num_of_episodes",(int) 2);
		parameters.put("burlap.qlearning.qinit",(double) 0);
		parameters.put("burlap.qlearning.lr",(double) 0.85);
		parameters.put("burlap.qlearning.gamma",(double) 0.85);
		parameters.put("burlap.qlearning.epsilonval",(double) 0.5);
		parameters.put("burlap.qlearning.out_qtable",System.getProperty("user.dir")+"/src/test/resources/output/qtable.yaml");
		parameters.put("burlap.algorithm", "QLearning");
		parameters.put("burlap.qlearning.decayedepsilonstep", 0.95);
	}
	

	
}
