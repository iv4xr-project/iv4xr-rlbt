/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits;

import static nl.uu.cs.aplib.AplibEDSL.ABORT;
import static nl.uu.cs.aplib.AplibEDSL.FIRSTof;
import static nl.uu.cs.aplib.AplibEDSL.SEQ;
import static nl.uu.cs.aplib.AplibEDSL.goal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import burlap.debugtools.DPrint;
import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.environment.Environment;
import burlap.mdp.singleagent.environment.EnvironmentOutcome;
import environments.LabRecruitsConfig;
import environments.LabRecruitsEnvironment;
import eu.fbk.iv4xr.rlbt.RlbtMain.RewardType;
import eu.fbk.iv4xr.rlbt.RlbtMain.SearchMode;
import eu.fbk.iv4xr.rlbt.RlbtMultiAgentMain;
import eu.fbk.iv4xr.rlbt.configuration.LRMultiAgentConfiguration;
import eu.fbk.iv4xr.rlbt.distance.StateDistance;
import eu.fbk.iv4xr.rlbt.labrecruits.rewardfunctions.CoverageOrientedRewardFunction;
import eu.fbk.iv4xr.rlbt.labrecruits.rewardfunctions.GoalOrientedRewardFunction;
import eu.fbk.iv4xr.rlbt.rewardfunction.RlbtRewardFunction;
import eu.fbk.iv4xr.rlbt.utils.GoalLib;
import eu.fbk.iv4xr.rlbt.utils.TacticLib;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import game.LabRecruitsTestServer;
import game.Platform;
import nl.uu.cs.aplib.mainConcepts.Goal;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import nl.uu.cs.aplib.multiAgentSupport.ComNode;
import nl.uu.cs.aplib.utils.Pair;
import world.BeliefState;
import world.LabEntity;


/**
 * @author kifetew
 *
 */
public class LabRecruitsRLMultiAgentEnvironment implements Environment {

	private boolean dataCollectionEnabled = true;
	private int traceCounter = 1;
	
	//temporary variable to test connection coverage
	public static boolean functionalCoverageFlag =false;
	private static boolean AgentDeadFlag = false;
	
	private double FullHealthScore =100;
	private double HealthThreshold = 70;  
	private boolean testingenvironment = true;
	
	public static boolean USE_GRAPHICS = false;     /*for running Labrecruit game with graphic*/
    
	public int tickbudgetForExplore = 25;
	public int memorywipeinterval =2;
	public boolean exploreoptionOn=true;
	
	private LabRecruitsEnvironment labRecruitsAgentEnvironment = null; 
	private static LabRecruitsTestServer labRecruitsTestServer = null;
	
	LabRecruitsTestAgent testAgentPassive = null;
	LabRecruitsTestAgent testAgentActive = null;
	
		
	private LabRecruitsState currentState = null;
	
	private LabRecruitsState currentStateActive = null;
	private LabRecruitsState currentStatePassive = null;
	
	private double lastReward = 0;
	
	private int updateCycles = 0;
	int MAX_CYCLES = 100;
	
	private String labRecruitesExeRootDir;
	private String labRecruitsLevel;
	private int maxTicksPerAction = 100;
	private String labRecruitsLevelFolder;
	
	
	/*set the testing goal entity and entity type*/
	private String goalEntity = "door3";
	private String goalEntityType = LabEntity.DOOR;
	private String goalEntityStatus = "isOpen"; // for a door, "isOn" for a button
	private String goalEntityStatusValue = "true";
	
	private String agentNamePassive = "agent1";
	private String agentNameActive = "agent2";
	
	private RlbtRewardFunction rewardFunction;
	
	private SearchMode searchMode = SearchMode.CoverageOriented;
	private RewardType rewardtype = RewardType.Sparse;
	
	//private int healthScore;
	
	private int STAGNATION_THRESHOLD = MAX_CYCLES;
	
	private int currentEpisode = 1;
	//private int healthpenalty;
	
	private HashMap<String, Integer> connectionList = null;    // store connection between buttons and doors
	private HashMap<String, Integer> entityList = null;    // store entity coverage per episode
	//private HashMap<String, Integer> GlobalEntityList = null;   // store entity coverage for all episodes
	private ArrayList<String> GlobalEntityList = null;
	
	private double FuncCovReward=5;     // reward for exploring a new state of an entity
	private boolean ActionOnlySwitch = true;
	
	public LabRecruitsRLMultiAgentEnvironment(LRMultiAgentConfiguration lrConfiguration, StateDistance stateDistance) {
		maxTicksPerAction = (int)lrConfiguration.getParameterValue("labrecruits.max_ticks_per_action");
		MAX_CYCLES = (int) lrConfiguration.getParameterValue("labrecruits.max_actions_per_episode");
		
		goalEntity =  (String) lrConfiguration.getParameterValue("labrecruits.target_entity_name");
		goalEntityStatus = (String) lrConfiguration.getParameterValue("labrecruits.target_entity_property_name");
		goalEntityType = checkEntityType((String) lrConfiguration.getParameterValue("labrecruits.target_entity_type"));
		goalEntityStatusValue = String.valueOf(lrConfiguration.getParameterValue("labrecruits.target_entity_property_value"));
		
		agentNamePassive =(String) lrConfiguration.getParameterValue("labrecruits.agentpassive_id");
		agentNameActive = (String) lrConfiguration.getParameterValue("labrecruits.agentactive_id");
		
		
		labRecruitsLevel = (String) lrConfiguration.getParameterValue("labrecruits.level_name");
		labRecruitsLevelFolder = (String) lrConfiguration.getParameterValue("labrecruits.level_folder");
		labRecruitesExeRootDir = (String) lrConfiguration.getParameterValue("labrecruits.execution_folder");
		USE_GRAPHICS = (Boolean) lrConfiguration.getParameterValue("labrecruits.use_graphics");
		
		
		this.searchMode = SearchMode.valueOf((String)lrConfiguration.getParameterValue("labrecruits.search_mode"));
		this.rewardtype = RewardType.valueOf((String)lrConfiguration.getParameterValue("labrecruits.rewardtype"));   // Sparse, CuriosityDriven
		
		this.rewardFunction = getRewardFunction(searchMode, stateDistance);
		this.STAGNATION_THRESHOLD = (int) lrConfiguration.getParameterValue("labrecruits.max_actions_since_last_new_state");
		//this.healthScore =100;
		//this.healthpenalty=0;
		
		GlobalEntityList = new ArrayList<String>();
		//this.entityList = new HashMap<String, Integer>();
		AgentDeadFlag = false;
		functionalCoverageFlag = (Boolean) lrConfiguration.getParameterValue("labrecruits.functionalCoverage");  // temporary variable
		
		testingenvironment = (Boolean) lrConfiguration.getParameterValue("labrecruits.testingsession");  // identify training or testing sesson
		memorywipeinterval = (int) lrConfiguration.getParameterValue("labrecruits.memory_clean_interval_action");
		exploreoptionOn = (Boolean) lrConfiguration.getParameterValue("labrecruits.exploreEventOn"); 
		ActionOnlySwitch= (Boolean) lrConfiguration.getParameterValue("labrecruits.actionOnlySwitch");
	}
	
	private RlbtRewardFunction getRewardFunction(SearchMode searchMode, StateDistance stateDistance) {
		if (searchMode.equals(SearchMode.GoalOriented)) {
			return new GoalOrientedRewardFunction(stateDistance);
		}else if (searchMode.equals(SearchMode.CoverageOriented)) {
			return new CoverageOrientedRewardFunction(stateDistance);
		}else {
			throw new RuntimeException("Unknown SearchMode: " + searchMode);
		}
	}

	private String checkEntityType(String eType) {
		
		if (eType.equalsIgnoreCase(LabEntity.DOOR)) {
			return LabEntity.DOOR;
		}else if (eType.equalsIgnoreCase(LabEntity.SWITCH)) {
			return LabEntity.SWITCH;
		}else if (eType.equalsIgnoreCase(LabEntity.GOAL)) {
			return LabEntity.GOAL;
		}else if (eType.equalsIgnoreCase(LabEntity.COLORSCREEN)) {
			return LabEntity.COLORSCREEN;
		}else if (eType.equalsIgnoreCase(LabEntity.FIREHAZARD)) {
			return LabEntity.FIREHAZARD;
		}else if (eType.equalsIgnoreCase(LabEntity.NPC)) {
			return LabEntity.NPC;
		}
		throw new RuntimeException("Entity type "+eType+" not recognised");
	}
	
		
	/*reset the state memory buffer*/
	public void resetStateMemory() {
		//this.healthScore=100;
		//this.healthpenalty=0;
		//this.entityList = new HashMap<String, Integer>();
		this.rewardFunction.resetStateBuffer();	
		/*for testing functional coverage*/
		if(functionalCoverageFlag==true) {
//			this.entityList = new HashMap<String, Integer>();
			ResetTestingGoal();
			//printGoalEntities();
		}
	}
	
	public void printGoalEntities() {
		System.out.println("Goal - Entity List to cover, total entity = "+ entityList.size());
		for (String k : entityList.keySet()) {
			System.out.println("Entity name = "+k+"   visit Frequency= "+entityList.get(k));
		}	
		/*System.out.println("----------------------Global entry list =  "+GlobalEntityList.size());
		for(int i = 0; i < GlobalEntityList.size(); i++) {   
		    System.out.println(GlobalEntityList.get(i));
		}*/		
		
	}
	
	/*RefreshTestingGoal*/
	public void ResetTestingGoal() {
		for (String k : entityList.keySet()) {
			entityList.put(k, 0);
		}
	}
	
	private void printConnectionList() {
		System.out.println("Testing - Printing Connection list between entitieys, total connection entry = "+ connectionList.size());
		for (String k : connectionList.keySet()) {
			System.out.println("connection name = "+k+"   visit Frequency= "+connectionList.get(k));
		}	
		/*System.out.println("----------------------Global entry list =  "+GlobalEntityList.size());
		for(int i = 0; i < GlobalEntityList.size(); i++) {   
		    System.out.println(GlobalEntityList.get(i));
		}*/		
		
	}
	
	/*Testing  -  test of the logical connection exists in the level*/
	public void LoadConnectionList(String levelName, String levelFolder) {
		String fullPath = Paths.get(levelFolder, levelName + ".csv").toAbsolutePath().toString();
		
		String line = "";
	    String splitBy = ",";
	    //parsing a CSV file into BufferedReader class constructor  
	    //BufferedReader br;
		try {
			//BufferedReader br;
			BufferedReader br = new BufferedReader(new FileReader(fullPath));
			while ((line = br.readLine()) != null)
		    {
		    	//System.out.println("Line = "+line);
		    	if (line.startsWith("|w") || line.startsWith("|f")|| line.startsWith("w")||line.startsWith("f"))
		    		break;
		    	String[] token = line.split(splitBy);
		    	String ent="";
		        for (int i=0;i<token.length;i++) 
		        {
		        	ent = ent+token[i]+",";		        		
		        }
		        ent =  ent.substring(0, ent.length() - 1);
		        System.out.println("connection line = "+ent);
		        connectionList.put(ent,0);
		    }
			LoadConnectionLessEntity(labRecruitsLevel,labRecruitsLevelFolder);
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	}
	
	private void LoadConnectionLessEntity(String levelName, String levelFolder) {
		String fullPath = Paths.get(levelFolder, levelName + ".csv").toAbsolutePath().toString();
		String line = "";
	    String splitBy = ",";
	    String secondsplit ="\\^";
	    try {
	      //parsing a CSV file into BufferedReader class constructor  
	      BufferedReader br = new BufferedReader(new FileReader(fullPath));
	      while ((line = br.readLine()) != null)
	      {
	    	  //System.out.println("Line = "+line);
	    	  if (line.contains("^")) {
	    		  String[] token = line.split(splitBy);
	    		  //System.out.println("Last token = "+ token[token.length-1]);
	    		  for (int i=0;i<token.length;i++) {
	    			  if(token[i].contains(":b")) { //|| token[i].contains(":d>")) {  // consider only buttons
	    				  String[] smalltoken = token[i].split(secondsplit);
	    				  String key = smalltoken[smalltoken.length-1];
	    				  //System.out.println("Last token =   "+ key);
	    				  String ent = key+",";
	    				  boolean entityexistflag =false;
	    				// Iterating over keys only
	    				  for (String entitykey : connectionList.keySet()) {
	    					  //System.out.println("str = " + ent+"   key in connectionlist = "+ entitykey);
	    					  if (entitykey.contains(ent)==true) 
	    					  {
	    						  entityexistflag=true;
	    						//  System.out.println("Entity match found");
	    						  //connectionList.put(ent,0);
	    					  }	    				      
	    				  }
	    				  if(entityexistflag==false)
	    				  {
	    					  //System.out.println("Entity not found found, making a new entry for entity = "+ ent);
	    					  ent = ent;//+"null";
	    					  connectionList.put(ent,0);
	    				  }
	    			  }
	    		  	}
	    		 }
	    	  }
	    }
	    catch(IOException e) {
	      e.printStackTrace();
	    }
		
	}

	/*load entities*/
	public void LoadTestingEntityGoal(LabRecruitsState obsstate) {		
		for (String k : obsstate.getObjectsMap().keySet()) {
			LabRecruitsEntityObject entity = (LabRecruitsEntityObject) obsstate.getObjectsMap().get(k);
			//System.out.println("Entity observed= "+ entity.toString());
			//System.out.println("Last token =   "+ key);
			  String ent = entity.toString()+" (false)";
			  if (entityList.containsKey(ent)==false) {
				  entityList.put(ent,0);
				  //GlobalEntityList.put(ent,0);
			  }
			  ent = entity.toString()+" (true)";
			  if (entityList.containsKey(ent)==false) {
				  entityList.put(ent,0);
				  //GlobalEntityList.put(ent,0);
			  }	
			//entityList.put(entity.toString(),0);			
		}	
	}
	
	/*load entities*/
	public void LoadTestingGoal(String levelName, String levelFolder) {
		String fullPath = Paths.get(levelFolder, levelName + ".csv").toAbsolutePath().toString();
		
	    /*String line = "";
	    String splitBy = ",";
	    try {
	      //parsing a CSV file into BufferedReader class constructor  
	      BufferedReader br = new BufferedReader(new FileReader(fullPath));
	      while ((line = br.readLine()) != null)
	      {
	    	if (line.startsWith("|w") || line.startsWith("|f")|| line.startsWith("w")||line.startsWith("f"))
	    		break;
	        String[] token = line.split(splitBy);
	        for (int i=0;i<token.length;i++) {
	        	String ent = token[i]+" (false)";
	        	entityList.put(ent,0);
	        	ent = token[i]+" (true)";
	        	entityList.put(ent,0);	        	
	        }
	      }
	    }*/
		
		String line = "";
	    String splitBy = ",";
	    String secondsplit ="\\^";
	    try {
	      //parsing a CSV file into BufferedReader class constructor  
	      BufferedReader br = new BufferedReader(new FileReader(fullPath));
	      while ((line = br.readLine()) != null)
	      {
	    	  //System.out.println("Line = "+line);
	    	  if (line.contains("^")) {
	    		  String[] token = line.split(splitBy);
	    		  //System.out.println("Last token = "+ token[token.length-1]);
	    		  for (int i=0;i<token.length;i++) {
	    			  if(token[i].contains(":b") || token[i].contains(":d>")) {
	    				  String[] smalltoken = token[i].split(secondsplit);
	    				  String key = smalltoken[smalltoken.length-1];
	    				  //System.out.println("Last token =   "+ key);
	    				  String ent = key+" (false)";
	    				  if (entityList.containsKey(ent)==false) {
	    					  entityList.put(ent,0);
	    					  //GlobalEntityList.put(ent,0);
	    				  }
	    				  ent = key+" (true)";
	    				  if (entityList.containsKey(ent)==false) {
	    					  entityList.put(ent,0);
	    					  //GlobalEntityList.put(ent,0);
	    				  }		    				  
	    			  }
	    		  	}
	    		 }
	    	  }
	    }
	    catch(IOException e) {
	      e.printStackTrace();
	    }
	}
	
	
	/*start RL environment*/
	public void startAgentEnvironment () throws InterruptedException {
		System.out.println("Level name : "+labRecruitsLevel);
		System.out.println("=========================================================================================");
		AgentDeadFlag= false;
		lastReward = 0;
		updateCycles = 0;
		//this.healthScore=100;
		startTestServer();
		//System.out.println("Starting training, memory clean interval and training session = "+memorywipeinterval+"  training = "+ testingenvironment);
		
		/*set parameter for interactable entities. Now considering two options : consider only switches/buttons, consider both buttons/switches and doors as action*/
		LabRecruitsActionType actiontype = new LabRecruitsActionType();
		actiontype.setActionOnlySwitch(ActionOnlySwitch);
		
		
		/*only for testing agent*/
		/*if(testingenvironment==true) {
			this.connectionList=new HashMap<String, Integer>();
			LoadConnectionList(labRecruitsLevel,labRecruitsLevelFolder);
			printConnectionList();
		}*/
		/*for testing functional coverage*/
		if(functionalCoverageFlag==true){
			this.entityList = new HashMap<String, Integer>();
			//this.GlobalEntityList = new HashMap<String, Integer>();
			//GlobalEntityList = new ArrayList<String>();
			 LoadTestingGoal(labRecruitsLevel,labRecruitsLevelFolder);
			//printGoalEntities();
		}
		
		LabRecruitsConfig gameConfig = new LabRecruitsConfig(labRecruitsLevel,labRecruitsLevelFolder);
		float viewDistance = 20;
		gameConfig.replaceAgentViewDistance(viewDistance);
		gameConfig.agent_speed = 0.13f;
		gameConfig.host = "localhost"; // "192.168.29.120";
		labRecruitsAgentEnvironment = new LabRecruitsEnvironment(gameConfig);
		labRecruitsAgentEnvironment.startSimulation();
		
		
		// creating two test agents, Passive and Active:
        ComNode communication = new ComNode();
        //passive agent
		testAgentPassive = new LabRecruitsTestAgent(agentNamePassive) // matches the ID in the CSV file
				. attachState(new BeliefState())
				. attachEnvironment(labRecruitsAgentEnvironment)
				. registerTo(communication)
				. setTestDataCollector(new TestDataCollector());
		
		
		//active agent
		testAgentActive = new LabRecruitsTestAgent(agentNameActive) // matches the ID in the CSV file
				. attachState(new BeliefState())
				. attachEnvironment(labRecruitsAgentEnvironment)
				. registerTo(communication)
				. setTestDataCollector(new TestDataCollector());
		
		if (dataCollectionEnabled) {
			testAgentActive.withScalarInstrumenter(state -> instrumenter(testAgentActive.getState()));
		}
		
		System.out.println("=========================start both agents with an explore=====================================");
		// Both agent do an explore to start 
		//Passive agent starts
		GoalStructure goal =  explore(); //getActionGoal(goalEntity, goalEntityType);
		doAction(goal, maxTicksPerAction, testAgentPassive);
		currentStatePassive = (LabRecruitsState) AgentCurrentObservation(testAgentPassive);
		
		
		GoalStructure goalactive =  explore(); //getActionGoal(goalEntity, goalEntityType);
		doAction(goalactive, maxTicksPerAction, testAgentActive);
		currentStateActive = (LabRecruitsState) AgentCurrentObservation(testAgentActive);
		double rewardval= UpdateGoalList(currentStateActive);   // update coverage goal for the first time
		System.out.println("=========================Initial Observation of both agents=====================================");
		DPrint.ul ("Passive agent  - Initial observation state: "+ currentStatePassive.toString());
		DPrint.ul ("Active agent - Initial observation state: "+ currentStateActive.toString());
	}
	
	Pair<String,Number>[] instrumenter(BeliefState st) {
		Pair<String,Number>[] out = new Pair[3] ;
		out[0] = new Pair<String,Number>("posx",st.worldmodel.position.x) ;
		out[1] = new Pair<String,Number>("posz",st.worldmodel.position.z) ;
		out[2] = new Pair<String,Number>("time",st.worldmodel.timestamp) ;
		//out[3] = new Pair<String,Number>("gcd",st.gcd) ;
		//out[4] = new Pair<String,Number>("win",st.win ? 1 : 0) ;
		return out ;
	}
	
	public void stopAgentEnvironment() {
//		if (dataCollectionEnabled) {
//			List<Map<String,Number>> trace = testAgentActive
//					. getTestDataCollector()
//					. getTestAgentScalarsTrace(testAgentActive.getId())
//			        . stream()
//			        . map(event -> event.values) . collect(Collectors.toList());
//			System.out.println(trace);
//			try {
//				testAgentActive.getTestDataCollector()
//				 .saveTestAgentScalarsTraceAsCSV(testAgentActive.getId(),"trace11.csv");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
		labRecruitsAgentEnvironment.close();
		stopTestServer();
	}


	public void RunPassiveAgent() {
//		clearAgentMemory(testAgentPassive);
		System.out.println("----------Passive agent is working now --------------- ");
		//passive agent will only explore the environment and take an observation after each exploration event
//		GoalStructure goal =  explore(); //getActionGoal(goalEntity, goalEntityType);
//		doAction(goal, maxTicksPerAction, testAgentPassive);
		doExplore(testAgentPassive);
		
		currentStatePassive = (LabRecruitsState) AgentCurrentObservation(testAgentPassive); // get observation
		System.out.println("Passive agent state = "+ currentStatePassive.toString());
		//LoadTestingEntityGoal(currentStatePassive);
		
		GoalStructure goalshareobj = shareObservation(testAgentPassive.getId(), testAgentActive.getId());
		doAction(goalshareobj, maxTicksPerAction, testAgentPassive);
				
	}//end of the function
	/**
	 * this way of defining actions is the most promising, it makes the agent more explorative, but could be improved
	 * 
	 * The logic is as follows:
	 * 	- if the entity selected by the algorithm as the next action is a button (switch), generate a goal for the agent so that 
	 * 	it interacts with it (pushes the button). The refresh state sub-goal is added to make the agent go to the button
	 * 	- if the entity is a door, refresh its state and then check to see if it's open
	 * 	- else, simply interact with the entity. This case should not happen in this LabRecruits scenario 
	 * 	because currently there are either buttons or doors. Needs to be elaborated for other entity types
	 * 
	 * Note that in the current LabRecruits version, only buttons can be interacted with
	 * 
	 * @param entityId
	 * @param entityType
	 * @return returns a GoalStructure object that represents the goal the agent should perform next.
	 */
	private GoalStructure getActionGoal (String entityId, String entityType, LabRecruitsTestAgent testAgent) {
		GoalStructure goal = null;
		if (entityType.contentEquals(LabEntity.SWITCH)) {
			goal = GoalLib.entityInteracted(entityId);
			
			//FIXME handle better, this is temporary!
			//clearAgentMemory();
			
			//testAgent.getState().getMemorizedPath().clear();
		} else if(entityType.contentEquals(LabEntity.DOOR)) {
//			float THRESHOLD_DISTANCE_TO_GOALFLAG = 0.5f;
			goal = SEQ (//GoalLib.atBGF(entityId, THRESHOLD_DISTANCE_TO_GOALFLAG, true),
					GoalLib.entityStateRefreshed(entityId),
					GoalLib.entityInCloseRange(entityId),
					GoalLib.entityInvariantChecked(testAgent,
	        		entityId, 
	        		entityId + " should be open", 
	        		(WorldEntity e) -> e.getBooleanProperty("isOpen")));
		} else if (entityType.contentEquals(LabEntity.GOAL)) {
			goal = GoalLib.entityInCloseRange(entityId);
		} else {
			// other kind of entity, for example fireHazard, not to interact, do nothing only observe
			goal = observe();// 
			//goal = GoalLib.entityInteracted(entityId);
		}
		return goal;
	}

	/**
	 * Clear the agent's memory before exploration. This forces the agent to 'refresh' its belief state after an action.
	 */
	private static void clearAgentMemory (LabRecruitsTestAgent testAgent, boolean clearWom, boolean clearVisistedNodes) {
		//System.out.println("Clearing agent's memeory of old observations");
		if (clearWom) {
			testAgent.getState().worldmodel.elements.clear();
		}
		if (clearVisistedNodes) {
			testAgent.getState().pathfinder().wipeOutMemory();
		}
	}
	
	
	private void startTestServer (){
		//String labRecruitesExeRootDir = System.getProperty("user.dir") ;
	
		labRecruitsTestServer = new LabRecruitsTestServer(
				USE_GRAPHICS,
				Platform.PathToLabRecruitsExecutable(labRecruitesExeRootDir)); 
		labRecruitsTestServer.waitForGameToLoad();
	}
	
	private void stopTestServer() {
		if(labRecruitsTestServer!=null) 
			labRecruitsTestServer.close();
	}
	
	
	public State AgentCurrentObservation(LabRecruitsTestAgent testAgent) {	
		LabRecruitsState currentState = new LabRecruitsState(false);
		BeliefState beliefState = testAgent.getState();
		// add the objects into the LR agent state to build the next state
		for (WorldEntity worldEntity : beliefState.knownEntities()){
			worldEntity.timestamp=0;
			//System.out.println("Entity type on current observation = "+ worldEntity.type);
			if (worldEntity.type.contentEquals(LabEntity.SWITCH) || worldEntity.type.contentEquals(LabEntity.DOOR)) 
			//if (worldEntity.type.contentEquals(LabEntity.SWITCH))
			{	
				currentState.addObject(new LabRecruitsEntityObject(worldEntity));
			}
		}
		if(currentState.numObjects()==0)
				System.out.println(testAgent.getId()+ " Warning : Empty Observation , num obj in current observation= "+currentState.numObjects());
		
		//DPrint.ul("Agent name : "+ testAgent.toString()+"   ,Observation state: "+ currentState.toString());
		return currentState;
	}
	
	
	/**
	 * A state in the RL algorithm context is a collection of objects.
	 * The objects in our case are the entities in the LabRecruits world.
	 * Each object has it's state (properties) and the collection of them makes up what 
	 * is considered a state for the RL algorithm.
	 */
	
	@Override	
	public State currentObservation() {	
		LabRecruitsState currentState = new LabRecruitsState(false);
		BeliefState beliefState = testAgentActive.getState();
		// add the objects into the LR agent state to build the next state
		for (WorldEntity worldEntity : beliefState.knownEntities()){
			worldEntity.timestamp=0;
			if (worldEntity.type.contentEquals(LabEntity.SWITCH) || worldEntity.type.contentEquals(LabEntity.DOOR)) 
			//if (worldEntity.type.contentEquals(LabEntity.SWITCH))
			{	
				currentState.addObject(new LabRecruitsEntityObject(worldEntity));
			}
		}
		if(currentState.numObjects()==0) {
		System.out.println("Warning : Empty Observation , num obj in current observation = "+currentState.numObjects());}
		//DPrint.ul("Current Observation state :"+ currentState.toString() );
		return currentState;
	}

	private GoalStructure doEntityStateRefresh (String entityId) {
		GoalStructure goal = null;
		goal = SEQ(GoalLib.entityInCloseRange(entityId),
				GoalLib.entityStateRefreshed(entityId));		
		return goal;
	}
	
	/*explore the environment*/
	private void doExplore(LabRecruitsTestAgent testAgent) {
		DPrint.ul("-----------------START EXPLORATION--"+ testAgent.getId()+ "-------In doExplore()-----");
		
		// first clear the agent's memory, otherwise the explore will not have any meaningful effect
		clearAgentMemory(testAgent, true, false);
		
		GoalStructure goal = explore();
		doAction(goal, maxTicksPerAction, testAgent);
		DPrint.ul("-------END EXPLORATION ---------------------------------\n");
	}
		
	@Override
	public EnvironmentOutcome executeAction(Action a) {
		/*-----------------------------------------------------------------------
		Active RL agent is working
		-------------------------------------------------------------------------*/
		System.out.println("Active RL agent works now, id= "+testAgentActive.getId() );
		
		currentState = (LabRecruitsState) currentObservation();
			
		State oldState = currentState; // state before execution
		System.out.println("Old state = "+ oldState);

		LabRecruitsAction action = (LabRecruitsAction)a;  // this Action a should be mapped into a goal that the agent can execute
		GoalStructure subGoal = getActionGoal(action.getActionId(), action.getInteractedEntity().type, testAgentActive);
		
		boolean terminated = false;
		if (subGoal != null) {  // if action goal is not empty, execute action
			doAction(subGoal, maxTicksPerAction, testAgentActive);
		
			doExplore(testAgentActive);
			currentState = (LabRecruitsState) currentObservation();
			System.out.println(testAgentActive.getId()+"  - Current state before receive = " + currentState);

			RunPassiveAgent();
			GoalStructure goalreceive=  receiveObservationShare();
			doAction(goalreceive, maxTicksPerAction, testAgentActive);
			
			currentState = (LabRecruitsState) currentObservation();  //update current state	after executing the chosen action
			
			if(currentState.numObjects()==0) {
				System.out.println("Current observation is empty, restoring the last state");				
				currentState= (LabRecruitsState) oldState;
			}
			System.out.println(testAgentActive.getId()+ " - Current state after receive = " + currentState);
			terminated = isFinal(currentState);
			
			
			if (subGoal.getStatus().success()==true) // reward calculation and next state observation if only goal is successful
			{ 
				if (testingenvironment==true) {
					System.out.println("Testing environment - reward = "+lastReward);					
				}else {
					lastReward = getReward(oldState, currentState, action);
				}
				System.out.println("Action = "+ action.actionName()+  " ,executed successfully, reward = "+lastReward);
				
				/*-------------For functional coverage calculation (for all RL algorithm)------------------------------------------*/
				if(functionalCoverageFlag==true)  
				{
					double rewardfunc= UpdateGoalList(currentState);
					//UpdateConnectionCoverage((LabRecruitsState)oldState,currentState,action);
				}
				
				// register the action performed for later serialization to test case
				RLActionToTestCaseEncoder.getInstance().registrAction(action, oldState, currentState, testAgentActive);
			}
			else 
			{
				lastReward = 0;  
				System.out.println("Action =  "+ action.actionName()+  " ,execution failure/inprogress, no reward = "+ lastReward);
			}
		
			
		}else {
			lastReward = 0;
		}
		EnvironmentOutcome outcome = new EnvironmentOutcome(oldState, action, currentState, lastReward, terminated);
		
		DPrint.ul ("From: " + oldState.toString() + "\n To: " + currentState.toString() + 
				" Action: " + action.actionName() + " Reward: " + lastReward + 
				" Goal status: " + (subGoal != null?subGoal.getStatus().toString():" NULL"));
		//DPrint.ul("Health penalty = "+this.healthpenalty);
		
		
		// each action consumes budget
		updateCycles++;					
		return outcome;
	}


	private double GetGeneralReward() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	/*check for the update on the connection list*/
	private void UpdateConnectionCoverage(LabRecruitsState oldState, LabRecruitsState currentState, LabRecruitsAction action) {
		System.out.println("---------------Update connection coverage statistics--------------------------");
		System.out.println("Old state = "+ oldState.toString());
		System.out.println("CurrentState = "+currentState.toString());
		System.out.println("Action = "+action.actionName());
		String splitBy =",";
		for (String entitykey : connectionList.keySet()) {
			 String[] token = entitykey.split(splitBy);
			// System.out.println("Token size = "+ token.length);
			  if (token[0].contains(action.actionName())==true) 
			  {
				  if(connectionList.get(entitykey)==0)  // connection has not satisfied yet 
				  {				  
					 // System.out.println("Entity found : "+ action.actionName() + "  "+ entitykey);
					  //String[] token = entitykey.split(splitBy);
					  //System.out.println("Token size = "+ token.length);
					  int entitystatuschangecount=0;
					  //check for changes in state for each entity from old to the new state
					  for (int i=0;i<token.length;i++)
					  {
						//  System.out.println("Chcek for entity = "+ token[i]);
						  boolean entitychangeflag=false;
						  entitychangeflag = isEntityStatusChanged(oldState,currentState,token[i]);
						  if(entitychangeflag == true) {
							  entitystatuschangecount++;
						  }
					  }
					  
					  //if both entity status is changed successfully, we can say this connection satisfy
					  if(entitystatuschangecount == token.length)
					  {
						  //System.out.println("This connection is satisfied = "+entitykey);
						  int freq =  connectionList.get(entitykey);
						  freq=freq+1;
						  //if (freq>1)
							//  System.out.println("visited over 1");
						  connectionList.put(entitykey,freq);					  
					  }
				  }
			  }	    				      
		  }
	}//end of the function

	/*check if an entity status is changed from old to current observation*/
	private boolean isEntityStatusChanged(LabRecruitsState oldState, LabRecruitsState currentState, String entityname) {		
		String oldentitystate =getentitystate(oldState.toString(), entityname);
		String newentitystate=getentitystate(currentState.toString(), entityname);
		//System.out.println("old status = "+ oldentitystate+"  new status= "+newentitystate);
		if(oldentitystate!=null && newentitystate!=null) 
		{
			if(oldentitystate.equals(newentitystate)==false)
			{
			//	System.out.println("Change in entity status");
				return true;
			}
		}
		//System.out.println("No change in entity status");
		return false;
	}

	private String getentitystate(String observationstate, String entityname) {
		String splitBy =",";
		String[] token = observationstate.split(splitBy);
		for (int i=0;i<token.length;i++)
		{
			if(token[i].contains(entityname)) 
			{
				return token[i];
			}
		}
		return null;
	}//end of the function

	private double UpdateGoalList(LabRecruitsState currentState2) {	
		double rewardfuncCov=0;
		//System.out.println(currentState2.toString());
		String strtemp = currentState2.toString().replace("[", "");
		String str =  strtemp.replace("]", "");
		//System.out.println(str);
		StringTokenizer st = new StringTokenizer(str,",");  
	     while (st.hasMoreTokens()) {  
	    	 String entitystr =st.nextToken();
	    	 if(entityList.containsKey(entitystr)) {
	    		 int freq= entityList.get(entitystr);
	    		 freq =freq+1;
	    		 if(freq==1) {   // a new state of this entity is observed, the agent is obtained small reward. This will encourage him to explore new states of entities
	    			// System.out.println("In UpdateGoalList - getting small reward for observing a new entity state: "+ entitystr);
	    			 rewardfuncCov += 1;//FuncCovReward; // 1 for observing a new state of an entity
	    		 }
	    		 entityList.put(entitystr, freq);  // update frequency
	    		 
	    		 //update global entity list
	    		 if (GlobalEntityList.contains(entitystr)==false) {
	    			 GlobalEntityList.add(entitystr);
	    		 }
	    		 //int globfreq= GlobalEntityList.get(entitystr);
	    		 //GlobalEntityList.put(entitystr, (globfreq+1));
	    		 //System.out.println("ENTITY FOUND" + entitystr+"   freq ="+entityList.get(entitystr));
	    	 }
	         //System.out.println(entitystr);  
	     }
	     if (rewardfuncCov>0)
	    	 rewardfuncCov +=FuncCovReward;  // get the final reward
	     System.out.println("UPDATE- Global coverage list size  = "+ GlobalEntityList.size());
	     return rewardfuncCov;	     
	}

	/**
	 * make the agent simply observe the environment, currently used at the start of the simulation
	 * @return
	 */
	private static GoalStructure observe() {
        //always true
        Goal g = goal(String.format("Lets do nothing"))
                .toSolve((BeliefState belief) -> true);

        // make an observation and update agent
        GoalStructure goal = g.withTactic(TacticLib.observe()).lift();

        return goal;
    }
	
	/**
	 * make the agent simply observe the environment, currently used at the start of the simulation
	 * @return
	 */
	private static GoalStructure shareObservation(String agentid, String targetagentid) {
        //always true
        Goal g = goal(String.format("share observation to agent"))
                .toSolve((BeliefState belief) -> true);

        // make an observation and update agent
        GoalStructure goal = g.withTactic(TacticLib.shareObservationToSingleAgent(agentid, targetagentid)).lift();

        return goal;
    }
	
	private static GoalStructure receiveObservationShare() {
        //always true
        Goal g = goal(String.format("share observation to agent"))
                .toSolve((BeliefState belief) -> true);

        // make an observation and update agent
        GoalStructure goal = g.withTactic(TacticLib.receiveDirectObservationShare()).lift();

        return goal;
    }
	
    
    
	//receiveObservationShare
	
	/*private static GoalStructure gotoEntityPosition(String entityId) {
		float deltaSq =(float) (0.1*0.1) ;
		Goal goal = 
        	  goal(String.format("The agent is at: [%s]", entityId))
        	  . toSolve((BeliefState belief) -> {
        		  var e = (LabEntity) belief.worldmodel.getElement(entityId) ;
        		  // bug .. .should be distsq:
        		  // return e!=null && Vec3.dist(belief.worldmodel.getFloorPosition(), e.getFloorPosition()) < 0.35 ;
        		  // System.out.print("entityinteracted: navigate to" + e);
        		  return e!=null && Vec3.sub(belief.worldmodel().getFloorPosition(), e.getFloorPosition()).lengthSq() <= deltaSq ;
        	    });
        	  
  
        	  return goal.withTactic(
             		 FIRSTof(//the tactic used to solve the goal
                        TacticLib.navigateTo(entityId),//move to the goal position
                        TacticLib.explore(), //explore if the goal position is unknown
                        ABORT())) 
             	  . lift();
      }
	*/
	
	/**
	 * Make the agent explore the environment. 
	 * The tactic used here does NOT have the concept of search budget, hence the goal to which the tactic is attached 
	 * must have a way of controlling the budget.
	 * IMPORTANT: the agent's 'memory' must be cleaned before making it execute this goal, otherwise, it will not do any exploration.
	 * 
	 * Recommendend usage: 1) at the beginning of an episode, 2) after each action performed (to determine the effect of the action and construct the next state)
	 * @return
	 */
	private static GoalStructure explore() {
		//System.out.println("-----------------------------------------------------------------------------");
		GoalStructure goal = goal("explore")
			       .toSolve((BeliefState belief) -> false)
			       .withTactic(FIRSTof(TacticLib.explore(), ABORT()))
			       .lift();
		//System.out.println("-----------------------------------------------------------------------------");
        return goal;
    }

	
	/**
	 * actually make the agent do the selection action. Set the goal to the agent and do some 
	 * update cycles to let the agent perform the goal, some tuning of the number of cycles 
	 * and sleep time could be necessary.
	 * @param goal
	 */
	public static void doAction (GoalStructure goal, int tickbugdet, LabRecruitsTestAgent testAgent) {
		//System.out.println("-----------------------------------------------------------------------------");
		System.out.println("--------------"+testAgent.getId() + "---------In doAction()-----------tickbudget : "+ tickbugdet);
		testAgent.setGoal(goal);
		int maxTicks =tickbugdet;// maxTicksPerAction;	// let the agent run until the current goal is either succeeds or fails?!
		int tickCounter = 0;
		while (tickCounter < maxTicks && goal.getStatus().inProgress()) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			testAgent.update();
//			DPrint.ul("*** STEP :" + testAgent.getState().worldmodel.timestamp + ", "
//		               + testAgent.getState().id + " @" + testAgent.getState().worldmodel.position) ;
			
			tickCounter++;
		}
		
		// if agent is stuck, clear its memory
		if (testAgent.getState().isStuck()) {
			clearAgentMemory(testAgent, false, true);
		}
		
		//System.out.println("-----------------------------------------------------------------------------");
	}
	
	@Override
	public double lastReward() {
		return lastReward;
	}

	@Override
	public boolean isInTerminalState() {
		System.out.println("Check if current possition is the goal position");
		boolean isFinal = isFinal(currentState) || updateCycles >= MAX_CYCLES || AgentDeadFlag==true;
		if (isFinal) {
			System.out.println("Achieve goal, Ending episode");
			
			// save collected trace data
			if (dataCollectionEnabled) {
				String traceFile = RlbtMultiAgentMain.outputDir + File.separator + "location_trace" + traceCounter++ + ".csv";
				try {
					testAgentActive.getTestDataCollector()
					 .saveTestAgentScalarsTraceAsCSV(testAgentActive.getId(), traceFile);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			//handle concretization of actions to test case
			String actionsFile = RlbtMultiAgentMain.outputDir + File.separator + "actions.csv";
			try {
				RLActionToTestCaseEncoder.getInstance().saveActionsToFile(testAgentActive, actionsFile);
//				List<GoalStructure> goals = RLActionToTestCaseEncoder.getInstance().serliazeActionsToGoals(testAgentActive);
//				for (GoalStructure goal : goals) {
//					System.out.println(goal.toString());
//				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return isFinal;
	}
	

	@Override
	public void resetEnvironment() {
		
		// TODO agent environment should be restarted only if this is not the last trial,
		// for now we simply call start agent environment, but should be checked
		// if last trial, no start.
		AgentDeadFlag =false;
		currentEpisode ++;
		stopTestServer();
		
		try {
			startAgentEnvironment();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public List<Action> actionList(State state) {
		return new LabRecruitsActionType().allApplicableActions(state);
	}
	

	/**
	 * Compute the reward for the given action.
	 * 	- if current state is the final state (winning state), give maximum reward
	 * 	- if agent is not stuck, but is not moving, penalty -1
	 * 	- otherwise, zero.
	 * 
	 * TODO note that this is an initial intuition and should be experimented with and improved.
	 */
//	@Override
	public double getReward(State state1, State state2, Action action) {
		double reward = 0;
		double rewardfunccov=0;
		
		/*-------------For functional coverage calculation (for all RL algorithm)------------------------------------------*/
		if(functionalCoverageFlag==true)  
		{
			rewardfunccov= UpdateGoalList(currentState);
		}
		
				
		if (isFinal(state2)) {			
			reward = 100;
			System.out.println("Action  = "+action.actionName()+"  this is Final State, reward = "+reward);
		} else {			
			switch(rewardtype) {  //enable either sparse or curiosityDriven reward 
			case Sparse:	
				System.out.println("Sparse Reward = "+reward);
				return 0;				
		
			case CuriousityDriven:
				reward = rewardFunction.reward(state1, action, state2, testAgentActive.getState());
				double curiRe =  reward;
				reward =reward +rewardfunccov; // adding reward from functional coverage calculation
				System.out.println("Total reward= "+reward +"  Curiosity Reward = "+curiRe +"  Functional coverage reward= "+rewardfunccov);
				return reward;
			
			default:
				throw new RuntimeException("Unknown reward type: " + rewardtype);
			}
			
			//reward = rewardFunction.reward(state1, action, state2, testAgent.getState());
		}
		System.out.println("-------------------------Reward : "+ reward);
		return reward; 
	}


//	@Override
	public boolean isFinal(State state) {
		LabRecruitsState labRecruitsState = (LabRecruitsState)state;
		switch(searchMode) {
		case GoalOriented:
			if (labRecruitsState.getObjectsMap().containsKey(goalEntity)) {
				LabRecruitsEntityObject entity = (LabRecruitsEntityObject) labRecruitsState.getObjectsMap().get(goalEntity);
				if (entity.getLabRecruitsEntity().type.contentEquals(LabEntity.GOAL)) {
					return testAgentActive.getState().distanceTo(goalEntity) <= 0.7;
				}else if (entity.getLabRecruitsEntity().type.contentEquals(LabEntity.FIREHAZARD)) {
					return testAgentActive.getState().distanceTo(goalEntity) <= 0.5;
				}else {
					return entity.getLabRecruitsEntity().getProperty(goalEntityStatus).toString().equalsIgnoreCase(goalEntityStatusValue); // .getBooleanProperty(booleanProperty);
				}
			}else {
				return false;
			}
		case CoverageOriented:
			CoverageOrientedRewardFunction rFunction = (CoverageOrientedRewardFunction)rewardFunction;
			if(functionalCoverageFlag==true) {
				//System.out.println("check IsFinal() - Functional coverage testing report till now");
				if(entityList.size()>0 && HasAllGoalSatisfied()==true)
				{
					System.out.println("Reached Final state : all entity covered");
					return true;
				}
				else
					return false;
			}
			else {
				System.out.println("Actions since last new state: " + rFunction.actionsSinceLastNewState());
				if (rFunction.actionsSinceLastNewState() >= STAGNATION_THRESHOLD) {
					return true;
				}else {
					return false;
				}
			}
		default:
			throw new RuntimeException("Unknown search mode: " + searchMode);
		}
	}

	/*check for functional coverage  - if all the goal listed at the begining are satisfied*/
	private boolean HasAllGoalSatisfied() { // if no 0 frequency value exists, meaning every entity has explored at least once
		//System.out.println("entity list size = "+entityList.size());
		if(entityList.containsValue(0)) {
			//printGoalEntities();
			double countzero = Collections.frequency(entityList.values(), 0);
			double coveragecount =  entityList.size() - countzero;
			double coverageRatio = (coveragecount/(double)entityList.size())*100;
			System.out.println("Not all states are visited, Visited entity states " +coveragecount+" out of "+entityList.size()+" entity states, Coverage percentage = "+ coverageRatio+"%");
			return false;
			}
		else {
			System.out.println("Finish -All entitity is covered at least once. 100% entity coverage");
			printGoalEntities(); // print all the goal activities		
			return true;
		}
	}
	
	/*calculate coverage percentage for an episode*/
	public double CalculateEpisodeCoverage() {
		System.out.println("Calculate Episode coverage ");
		double coverageRatio = 0;
		if (functionalCoverageFlag==true) {			
			printGoalEntities();	
			double countzero = Collections.frequency(entityList.values(), 0);
			double coveragecount =  entityList.size() - countzero;
			coverageRatio = (coveragecount/(double)entityList.size())*100;
			System.out.println("Episode Coverage calculation - Visited entity states " +coveragecount+" out of "+entityList.size()+" entity states, Coverate percentage = "+ coverageRatio+"%");
			}
		return coverageRatio;
	}/*end of the function*/
	
	/*calculate coverage percentage for an episode*/
	public double CalculateConnectionCoverage() {
		double coverageRatio = 0;
		System.out.println("End testing episode - Calculate connection coverage ");
		printConnectionList();	
		double countzero = Collections.frequency(connectionList.values(), 0);
		double coveragecount =  connectionList.size() - countzero;
		coverageRatio = (coveragecount/(double)connectionList.size())*100;
		System.out.println("Connection coverage calculation - satisfied connection= " +coveragecount+"/ total connection =  "+connectionList.size()+"  Coverate percentage = "+ coverageRatio+"%");
		return coverageRatio;
	}/*end of the function*/
	
	
	
	/*calculate coverage percentage for an episode*/
	public void CalculateGlobalCoverageAfterTraining() {
		double coverageRatio = 0;
		System.out.println("End of training session - Calculate coverage ");
		System.out.println("Global coverage list, total entity = "+ GlobalEntityList.size());
		System.out.println("----------------------Global entry list - entries visited by agent during training -----------");
		for(int i = 0; i < GlobalEntityList.size(); i++) {   
		    System.out.println(GlobalEntityList.get(i));
		}
		/*if (functionalCoverageFlag==true) {
			System.out.println("End of training episodes - Calculate coverage ");
			System.out.println("Global coverage list, total entity = "+ GlobalEntityList.size());
			for (String k : GlobalEntityList.keySet()) {
				System.out.println("entity = "+k+"   visit Frequency= "+GlobalEntityList.get(k));
			}		
			double countzero = Collections.frequency(GlobalEntityList.values(), 0);
			double coveragecount =  GlobalEntityList.size() - countzero;
			coverageRatio = (coveragecount/(double)GlobalEntityList.size())*100;
			System.out.println("Global Coverage at the end of training - Visited entity states " +coveragecount+" out of "+GlobalEntityList.size()+" entity states, Coverate percentage = "+ coverageRatio+"%");
			}*/
	}/*end of the function*/
	
	/*globar coverage after each episode*/
	public void GlobalCoveragePerEpisode() {
		double globalcov=0;
		if (GlobalEntityList.size()>0) {
			globalcov = ((double)GlobalEntityList.size()/entityList.size())*100;
		}
		System.out.println("Global coverage till this episode = "+globalcov+"  (%) covered entity = "+ GlobalEntityList.size()+"   out of  "+entityList.size());
		System.out.println("Entries that are not covered till this episode  ");
		for (String k : entityList.keySet()) {
			if (GlobalEntityList.contains(k)==false) {
				System.out.println("Entity =  "+k);
				}
			}
		}/*End of function*/
	
}
