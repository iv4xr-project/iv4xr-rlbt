package eu.fbk.iv4xr.rlbt.configuration;

import java.util.LinkedHashMap;

public class LRConfiguration extends Configuration{
	
	
	public LRConfiguration(){
		parameters = new LinkedHashMap<String, Object>();
		parameters.put("labrecruits.level_name", "buttons_doors_1");
		parameters.put("labrecruits.level_folder", "src/test/resources/levels/");
		
		parameters.put("labrecruits.execution_folder", ".");
		
		parameters.put("labrecruits.agent_id", "agent1");
		parameters.put("labrecruits.use_graphics",true);
		parameters.put("labrecruits.max_ticks_per_action",100);
		parameters.put("labrecruits.max_actions_per_episode",500);
		parameters.put("labrecruits.target_entity_name","door3");
		parameters.put("labrecruits.target_entity_type","DOOR");
		parameters.put("labrecruits.target_entity_property_name","isOpen");
		parameters.put("labrecruits.target_entity_property_value",true);
		parameters.put("labrecruits.search_mode", "GoalOriented");		
		parameters.put("labrecruits.functionalCoverage", true);
		parameters.put("labrecruits.max_actions_since_last_new_state", 100);
		parameters.put("labrecruits.rewardtype", "CuriousityDriven");
	}
	
}
