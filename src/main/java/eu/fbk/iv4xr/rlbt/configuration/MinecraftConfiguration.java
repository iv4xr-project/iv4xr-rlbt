package eu.fbk.iv4xr.rlbt.configuration;

import java.util.LinkedHashMap;

/** SUT configuration of the Minecraft scenario, the counterpart of
 * {@link LRConfiguration} for LabRecruits: it holds the defaults of every
 * parameter mineAgent.config may set. */
public class MinecraftConfiguration extends Configuration {

	public MinecraftConfiguration() {
		parameters = new LinkedHashMap<String, Object>();
		parameters.put("mine.address", "localhost:25565");
		parameters.put("mine.level", "src/test/resources/minecraft-levels/arena.csv");
		parameters.put("mine.testbenchUrl", "http://localhost:3000");
		parameters.put("mine.max_ticks_per_action", 120);
		parameters.put("mine.max_actions_per_episode", 30);
		// exploration only: ticks waited after every action, for the effects that are not immediate
		parameters.put("mine.wait_ticks_after_action", 10);
		parameters.put("mine.mob_tag", "mob1");
		parameters.put("mine.reward_type", "CoverageOriented");
		parameters.put("mine.weapon", "iron_sword");
	}

}
