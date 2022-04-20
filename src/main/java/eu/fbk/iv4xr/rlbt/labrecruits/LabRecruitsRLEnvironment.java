/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits;

import static nl.uu.cs.aplib.AplibEDSL.*;
import static nl.uu.cs.aplib.AplibEDSL.SEQ;
import static nl.uu.cs.aplib.AplibEDSL.goal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;

import agents.LabRecruitsTestAgent;
import agents.tactics.GoalLib;
import agents.tactics.TacticLib;
import burlap.behavior.singleagent.learning.tdmethods.QLearningStateNode;
import burlap.debugtools.DPrint;
import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.environment.Environment;
import burlap.mdp.singleagent.environment.EnvironmentOutcome;
import burlap.statehashing.HashableState;
import environments.LabRecruitsConfig;
import environments.LabRecruitsEnvironment;
import eu.fbk.iv4xr.rlbt.RlbtMain.RewardType;
import eu.fbk.iv4xr.rlbt.RlbtMain.SearchMode;
import eu.fbk.iv4xr.rlbt.configuration.LRConfiguration;
import eu.fbk.iv4xr.rlbt.distance.StateDistance;
import eu.fbk.iv4xr.rlbt.labrecruits.rewardfunctions.CoverageOrientedRewardFunction;
import eu.fbk.iv4xr.rlbt.labrecruits.rewardfunctions.GoalOrientedRewardFunction;
import eu.fbk.iv4xr.rlbt.rewardfunction.RlbtRewardFunction;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.spatial.Vec3;
import game.LabRecruitsTestServer;
import game.Platform;
import nl.uu.cs.aplib.mainConcepts.Goal;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import nl.uu.cs.aplib.mainConcepts.Tactic.PrimitiveTactic;
import world.BeliefState;
import world.LabEntity;
import world.LabWorldModel;


/**
 * @author kifetew
 *
 */
public class LabRecruitsRLEnvironment implements Environment {

	//temporary variable to test connection coverage
	public static boolean functionalCoverageFlag =false;
	private static boolean AgentDeadFlag = false;
	private double FullHealthScore =100;
	private double HealthThreshold = 70;  
	
	public static boolean USE_GRAPHICS = false;     /*for running Labrecruit game with graphic*/
    
	private LabRecruitsEnvironment labRecruitsAgentEnvironment = null; 
	private static LabRecruitsTestServer labRecruitsTestServer = null;
	LabRecruitsTestAgent testAgent = null;
	
		
	private LabRecruitsState currentState = null;
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
	
	private String agentName = "agent1";
	
	private RlbtRewardFunction rewardFunction;
	
	private SearchMode searchMode = SearchMode.CoverageOriented;
	private RewardType rewardtype = RewardType.Sparse;
	
	//private int healthScore;
	
	private int STAGNATION_THRESHOLD = MAX_CYCLES;
	
	private int currentEpisode = 1;
	//private int healthpenalty;
	
	private HashMap<String, Integer> entityList = null;    // store entity coverage per episode
	//private HashMap<String, Integer> GlobalEntityList = null;   // store entity coverage for all episodes
	private ArrayList<String> GlobalEntityList = null;
	
	private double FuncCovReward=10;     // reward for exploring a new state of an entity
	
	
	public LabRecruitsRLEnvironment(LRConfiguration lrConfiguration, StateDistance stateDistance) {
		maxTicksPerAction = (int)lrConfiguration.getParameterValue("labrecruits.max_ticks_per_action");
		MAX_CYCLES = (int) lrConfiguration.getParameterValue("labrecruits.max_actions_per_episode");
		
		goalEntity =  (String) lrConfiguration.getParameterValue("labrecruits.target_entity_name");
		goalEntityStatus = (String) lrConfiguration.getParameterValue("labrecruits.target_entity_property_name");
		goalEntityType = checkEntityType((String) lrConfiguration.getParameterValue("labrecruits.target_entity_type"));
		goalEntityStatusValue = String.valueOf(lrConfiguration.getParameterValue("labrecruits.target_entity_property_value"));
		agentName = (String) lrConfiguration.getParameterValue("labrecruits.agent_id");
		
		
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
			ResetTestingGoal(labRecruitsLevel,labRecruitsLevelFolder);
			//printGoalEntities();
		}
	}
	private void printGoalEntities() {
		System.out.println("Printing Goal Entity List - functional coverage testing, total entity = "+ entityList.size());
		for (String k : entityList.keySet()) {
			System.out.println("entity = "+k+"   visit Frequency= "+entityList.get(k));
		}	
		/*System.out.println("----------------------Global entry list =  "+GlobalEntityList.size());
		for(int i = 0; i < GlobalEntityList.size(); i++) {   
		    System.out.println(GlobalEntityList.get(i));
		}*/		
		
	}
	
	/*RefreshTestingGoal*/
	public void ResetTestingGoal(String levelName, String levelFolder) {
		for (String k : entityList.keySet()) {
			entityList.put(k, 0);
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
		AgentDeadFlag= false;
		lastReward = 0;
		updateCycles = 0;
		//this.healthScore=100;
		
		startTestServer();
		
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
		
		// create a test agent
		testAgent = new LabRecruitsTestAgent(agentName) // matches the ID in the CSV file
				. attachState(new BeliefState())
				. attachEnvironment(labRecruitsAgentEnvironment);
		
		var dataCollector = new TestDataCollector();
		testAgent.setTestDataCollector(dataCollector);
		
		// set the testing goal here
		//GoalStructure goal = getActionGoal("button1", LabEntity.SWITCH); //observe(); //getActionGoal("door1", LabEntity.DOOR); // getTestGoal ();
		GoalStructure goal =  explore(); //getActionGoal(goalEntity, goalEntityType);
		DPrint.ul("Starting Simulation : \n  Goal : "+goalEntity +"    "+goal.toString() +"   Entity type : "+goalEntityType+"  status : "+goal.getStatus());
		doAction(goal);

		DPrint.ul ("========Getting current State from start agent Environment==================");
		currentState = (LabRecruitsState) currentObservation();
		DPrint.ul ("Initial State (Agent's view): "+ currentState.toString());
	}
	
	
	public void stopAgentEnvironment() {
		labRecruitsAgentEnvironment.close();
		stopTestServer();
	}

	
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
	private GoalStructure getActionGoal (String entityId, String entityType) {
		GoalStructure goal = null;
		if (entityType.contentEquals(LabEntity.SWITCH)) {
			goal = GoalLib.entityInteracted(entityId);
			
			//FIXME handle better, this is temporary!
			clearAgentMemory();
			
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
	private void clearAgentMemory () {
		var surfaceNavGraph = testAgent.getState().pathfinder();
		surfaceNavGraph.wipeOutMemory();
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
	
	/**
	 * A state in the RL algorithm context is a collection of objects.
	 * The objects in our case are the entities in the LabRecruits world.
	 * Each object has it's state (properties) and the collection of them makes up what 
	 * is considered a state for the RL algorithm.
	 */
	
	@Override	
	public State currentObservation() {	
		//LabWorldModel wom = null ;
		
//		if (this.currentState != null) {
//			DPrint.ul("Current Observation state of Agent before explore :"+ this.currentState.toString() );
//		}
		// before making the observation of the state, 
		// first force the agent to explore the surrounding for changes and refresh its belief (state)
		DPrint.ul("-------START EXPLORATION  - after an action to update agent's view---------------------------------");
		GoalStructure goal = explore();
		doAction(goal);
		DPrint.ul("-------END EXPLORATION ---------------------------------");
		
		LabRecruitsState currentState = new LabRecruitsState(false);
		BeliefState beliefState = testAgent.getState();
		//testAgent.getState().worldmodel().mergeNewObservation(testAgent.getState().worldmodel());
		//System.out.println("Health loss: "+ testAgent.getState().worldmodel().healthLost);
		
//		Set<String> doorIds = new HashSet<>();
//		for (WorldEntity worldEntity : beliefState.knownEntities()){
//			if (worldEntity.type == "Door") // || worldEntity.type =="Switch")
//			{
//				doorIds.add(worldEntity.id);
//			}
//		}
//		
//		// refresh the state of every door in the agent's belief state
//		for (String doorId : doorIds){
//			GoalStructure goal = doEntityStateRefresh(doorId);
//			doAction(goal);
//		}
		
		// add the objects into the LR agent state to build the next state
		for (WorldEntity worldEntity : beliefState.knownEntities()){
			worldEntity.timestamp=0;
			//System.out.println("Entity type on current observation = "+ worldEntity.type);
			if (worldEntity.type.contentEquals(LabEntity.SWITCH)
					|| worldEntity.type.contentEquals(LabEntity.DOOR)) // != "FireHazard")
			{	
				currentState.addObject(new LabRecruitsEntityObject(worldEntity));
			}
		}
		if(currentState.numObjects()==0) {
		System.out.println("Observation empty , num obj = "+currentState.numObjects());}
		DPrint.ul("Current Observation state of Agent after explore :"+ currentState.toString() );
		return currentState;
	}

	private GoalStructure doEntityStateRefresh (String entityId) {
		GoalStructure goal = null;
		goal = SEQ(GoalLib.entityInCloseRange(entityId),
				GoalLib.entityStateRefreshed(entityId));		
		return goal;
	}
	
	@Override
	public EnvironmentOutcome executeAction(Action a) {
		System.out.println("Inside function executeAction()");
		State oldState = currentState; // state before execution
		double currHealthpoint = testAgent.getState().worldmodel().health; // get current health point
		LabRecruitsAction action = (LabRecruitsAction)a;  // this Action a should be mapped into a goal that the agent can execute
		GoalStructure subGoal = getActionGoal(action.getActionId(), action.getInteractedEntity().type);

		if (subGoal != null) {
			doAction(subGoal);
		} else {
			// TODO this means the agent cannot do anything, so let the current goal continue?
		}

//		updateCycles++;
			
		currentState = (LabRecruitsState) currentObservation();  //update current state	after executing the chosen action
		boolean terminated = isFinal(currentState);
		
		/*TODO:check this consideration- calculate reward if only the action is successful otherwise reward 0*/	
		if (subGoal!=null) {
			if (subGoal.getStatus().success()==true) {
				lastReward = getReward(oldState, currentState, action);
				System.out.println("Action ="+ action.actionName()+  "executed successfully, reward = "+lastReward);
			}
			else {
				lastReward = 0;
				System.out.println("Action ="+ action.actionName()+  "execution failure/inprogress, no reward = "+ lastReward);
				
			}
		}
		/*-------------For functional coverage calculation------------------------------------------*/
		if(functionalCoverageFlag==true) {  // for functional coverage testing
			double rewardfunccov=0;
			rewardfunccov= UpdateGoalList(currentState);
			/*if (rewardtype == RewardType.CuriousityDriven) {
				System.out.println("Curiosity driven");
				lastReward =lastReward+rewardfunccov;
			}*/		
		}
		
		/*-----------------Penalty for curiosity RL: for moving around the same corner/place-----------------*/
		/*if (rewardtype == RewardType.CuriousityDriven) {
			List<Vec3> recentPositions = testAgent.getState().getRecentPositions();
			if (recentPositions.size() >= 2) {//(recentPositions.size() >= 2) {
				if (recentPositions.get(recentPositions.size()-1).equals(recentPositions.get(recentPositions.size()-2))) {
					lastReward= lastReward-1;
					//System.out.println("Action  = "+action.actionName()+"  agent moving around same position, penalty = "+reward);
				} else {
					lastReward = lastReward -0;
					//System.out.println("Action  = "+action.actionName()+"  State visited over threshold,  penalty = "+reward);
				}
			}else {
				// means did not move enough to have more recent positions
				lastReward=lastReward-1;
				//System.out.println("Action  = "+action.actionName()+" agent did not move enough to get more position,  penalty = "+reward);
			}
		}*/
		/*------------------Penalize the agent for health loss and death- applicable to all kind of RL algorithm*/
		//case 1: penalty for health loss. penalty value is the point that is lost for executing this action  
		if(testAgent.getState().worldmodel().health<HealthThreshold) {  // penalty if only health point is below threshold
			lastReward =  lastReward- (currHealthpoint - testAgent.getState().worldmodel().health);
			DPrint.ul("Penalty for health loss = "+(currHealthpoint - testAgent.getState().worldmodel().health)+"  final reward ="+lastReward);
		}
		
		//case 2: big penalty if agent dies and end the episode
		if (testAgent.getState().worldmodel().health <= 0)
		{
			terminated = true;
			//System.out.println("----EMERGENCY NOTICE : AGENT DIED. Health point =  "+ testAgent.getState().worldmodel().health);
			lastReward =lastReward - 100*100;
			AgentDeadFlag= true;	
			DPrint.ul("STOP SIMULAITON - AGENT DIED = "+testAgent.getState().worldmodel().health );
		}
		/*----------------------------------------------------------------------------------------------------------*/
		//DPrint.ul("Goal status :" + lastReward);
		//DPrint.ul("Goal status :" + subGoal.getStatus().toString());
		
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
	    			 rewardfuncCov = FuncCovReward;
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
	     System.out.println("UPDATE- Global coverage list size  = "+ GlobalEntityList.size());
	     return rewardfuncCov;	     
	}

	// for testing q-learning agent after training
	public void EvaluateQLearningAgent(Map<HashableState, QLearningStateNode> qFunction) {
	
		DPrint.ul("\n\nTESTING :----------Testing Q learning agent----- ------- : ");
	
		// get the final q 
				System.out.println("key set:  "+qFunction.keySet().size());
//				System.out.println("TESTING : MAX q val : "+agent.getQs(agent.qFunction.keySet().iterator().next()));
				
				System.out.println("Q function size :  " +  qFunction.size());
				for (HashableState key:qFunction.keySet()){
					//QLearningStateNode node = agent.qFunction.get(key);
					//node = new QLearningStateNode(key);
					//List<Action> gas =agent.applicableActions(key.s());
					System.out.println("---------------------------Key name : "+key.toString()+ "     "+qFunction.get(key).toString());
					System.out.println("first qentry for this key :  action : "+qFunction.get(key).qEntry.get(0).a.actionName()+"   state :  "+qFunction.get(key).qEntry.get(0).s+"  qval:  "+qFunction.get(key).qEntry.get(0).q);
					System.out.println("second qentry for this key :  action : "+qFunction.get(key).qEntry.get(1).a.actionName()+"   state :  "+qFunction.get(key).qEntry.get(1).s+"  qval:  "+qFunction.get(key).qEntry.get(1).q);
					System.out.println("third qentry for this key :  action : "+qFunction.get(key).qEntry.get(2).a.actionName()+"   state :  "+qFunction.get(key).qEntry.get(2).s+"  qval:  "+qFunction.get(key).qEntry.get(2).q);
					System.out.println("fourth qentry for this key :  action : "+qFunction.get(key).qEntry.get(3).a.actionName()+"   state :  "+qFunction.get(key).qEntry.get(3).s+"  qval:  "+qFunction.get(key).qEntry.get(3).q);
					
					//System.out.println("STATE :   "+agent.qFunction.keySet().iterator().next().s().toString());
					//System.out.print("action  : "+agent.getStateNode(key).qEntry.iterator().next().a.actionName()+"   :  "+agent.getStateNode(key).qEntry.iterator().next().a);
					//System.out.print("states : "+agent.getStateNode(key).qEntry.iterator().next().s);
					//System.out.print("value : "+agent.getStateNode(key).qEntry.iterator().next().q);	
					//System.out.print("\n");
					
				}
				
		// set the testing goal here
		//GoalStructure goal = getActionGoal("door3", LabEntity.DOOR);
		GoalStructure goal = getActionGoal(goalEntity, goalEntityType); //observe(); //getActionGoal("door1", LabEntity.DOOR); // getTestGoal ();
		DPrint.ul("Testing Goal : "+goal.toString() +"  status : "+goal.getStatus());
		doAction(goal);

		currentState = (LabRecruitsState) currentObservation();
		DPrint.ul ("TESTING : Initial State (Agent's view): "+ currentState.toString());

		// state before execution
		/*State oldState = currentState;
		
		LabRecruitsAction action = (LabRecruitsAction)a;
		GoalStructure subGoal = getActionGoal(action.getActionId(), action.getInteractedEntity().type);
		
		
		if (subGoal != null) {
			doAction(subGoal);
		} else {
			// TODO this means the agent cannot do anything, so let the current goal continue?
		}

		updateCycles++;
				
		//update current state		
		currentState = (LabRecruitsState) currentObservation();
		
		boolean terminated = isFinal(currentState);
		lastReward = getReward(oldState, currentState, action);
		
		EnvironmentOutcome outcome = new EnvironmentOutcome(oldState, action, currentState, lastReward, terminated);
		
		DPrint.ul ("From: " + oldState.toString() + " To: " + currentState.toString() + 
				" Action: " + action.actionName() + " Reward: " + lastReward + 
				" Goal status: " + (subGoal != null?subGoal.getStatus().toString():" NULL"));
		*/
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
	 * Make the agent explore the environment. 
	 * The tactic used here does NOT have the concept of search budget, hence the goal to which the tactic is attached 
	 * must have a way of controlling the budget.
	 * IMPORTANT: the agent's 'memory' must be cleaned before making it execute this goal, otherwise, it will not do any exploration.
	 * 
	 * Recommendend usage: 1) at the beginning of an episode, 2) after each action performed (to determine the effect of the action and construct the next state)
	 * @return
	 */
	private static GoalStructure explore() {
		GoalStructure goal = goal("explore")
			       .toSolve((BeliefState belief) -> false)
			       .withTactic(FIRSTof(TacticLib.explore(), ABORT()))
			       .lift();
        return goal;
    }

	
	/**
	 * actually make the agent do the selection action. Set the goal to the agent and do some 
	 * update cycles to let the agent perform the goal, some tuning of the number of cycles 
	 * and sleep time could be necessary.
	 * @param goal
	 */
	private void doAction (GoalStructure goal) {
		testAgent.setGoal(goal);
		int maxTicks = maxTicksPerAction;	// let the agent run until the current goal is either succeeds or fails?!
		int tickCounter = 0;
		while (tickCounter < maxTicks && goal.getStatus().inProgress()) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			testAgent.update();
			//DPrint.ul("*** STEP :" + testAgent.getState().worldmodel.timestamp + ", "
		     //          + testAgent.getState().id + " @" + testAgent.getState().worldmodel.position) ;
			tickCounter++;
		}
	}
	
	@Override
	public double lastReward() {
		return lastReward;
	}

	@Override
	public boolean isInTerminalState() {
		boolean isFinal = isFinal(currentState) || updateCycles >= MAX_CYCLES || AgentDeadFlag==true;
		if (isFinal) {
			System.out.println("Finished Episode: " + currentEpisode);
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
		
		//System.out.println("Action  = "+action.actionName());
		double reward = 0;
				
		if (isFinal(state2)) {			
			reward = 100;
			//System.out.println("Action  = "+action.actionName()+"  Final State, reward = "+reward);
		} else {			
			switch(rewardtype) {  //enable either sparse or curiosityDriven reward 
			case Sparse:	
				return 0;				
		
			case CuriousityDriven:
				reward = rewardFunction.reward(state1, action, state2, testAgent.getState());
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
					return testAgent.getState().distanceTo(goalEntity) <= 0.7;
				}else if (entity.getLabRecruitsEntity().type.contentEquals(LabEntity.FIREHAZARD)) {
					return testAgent.getState().distanceTo(goalEntity) <= 0.5;
				}else {
					return entity.getLabRecruitsEntity().getProperty(goalEntityStatus).toString().equalsIgnoreCase(goalEntityStatusValue); // .getBooleanProperty(booleanProperty);
				}
			}else {
				return false;
			}
		case CoverageOriented:
			CoverageOrientedRewardFunction rFunction = (CoverageOrientedRewardFunction)rewardFunction;
			if(functionalCoverageFlag==true) {
				System.out.println("Functional coverage testing");
				if(entityList.size()>0 && HasAllGoalSatisfied()==true)
					return true;
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
		System.out.println("entity list size = "+entityList.size());
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
			printGoalEntities();		
			return true;
		}
	}
	
	/*calculate coverage percentage for an episode*/
	public double CalculateEpisodeCoverage() {
		double coverageRatio = 0;
		if (functionalCoverageFlag==true) {
			System.out.println("End episode - Calculate coverage ");
			printGoalEntities();	
			double countzero = Collections.frequency(entityList.values(), 0);
			double coveragecount =  entityList.size() - countzero;
			coverageRatio = (coveragecount/(double)entityList.size())*100;
			System.out.println("Coverage calculation - Visited entity states " +coveragecount+" out of "+entityList.size()+" entity states, Coverage percentage = "+ coverageRatio+"%");
			}
		return coverageRatio;
	}/*end of the function*/
	
	/*calculate coverage percentage for an episode*/
	public void CalculateGlobalCoverageAfterTraining() {
		double coverageRatio = 0;
		System.out.println("End of training episodes - Calculate coverage ");
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
			globalcov = (double)GlobalEntityList.size()/entityList.size();
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
