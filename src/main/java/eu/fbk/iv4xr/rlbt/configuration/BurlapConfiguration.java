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
		// Adam learning rate for DeepQLearningRL. Kept separate from burlap.qlearning.lr
		// (the tabular Bellman update step-size, valid in [0,1]) because Adam needs a
		// much smaller value (typically 1e-4 - 1e-3); reusing 0.85/0.25 there caused
		// the network weights to diverge/oscillate instead of converging.
		parameters.put("burlap.qlearning.dqn_lr",(double) 0.001);
		parameters.put("burlap.qlearning.gamma",(double) 0.85);
		parameters.put("burlap.qlearning.epsilonval",(double) 0.5);
		parameters.put("burlap.qlearning.epsilonmin",(double) 0.1);
		parameters.put("burlap.qlearning.out_qtable",System.getProperty("user.dir")+"/src/test/resources/output/qtable.yaml");
		parameters.put("burlap.algorithm", "QLearning");
		parameters.put("burlap.qlearning.decayedepsilonstep", 0.95);
		parameters.put("burlap.network.hidden_size",(int) 64);
		/*
		 * Replay buffer and target network. The defaults below are sized for LabRecruits,
		 * whose episodes run up to 200 actions; a SUT with much shorter episodes has to
		 * override them, or it will spend the whole run in the warm-up phase. Minecraft
		 * caps episodes at 30 actions (and they end earlier on death), so
		 * burlap_minecraft.config sets its own values.
		 */
		parameters.put("burlap.network.replay_buffer_capacity",(int) 10000);
		parameters.put("burlap.network.batch_size",(int) 32);
		parameters.put("burlap.network.min_replay_size",(int) 64);
		parameters.put("burlap.network.target_update_frequency",(int) 100);
	}
	

	
}
