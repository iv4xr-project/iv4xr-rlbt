/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits;

import static nl.uu.cs.aplib.AplibEDSL.SEQ;
import static nl.uu.cs.aplib.AplibEDSL.goal;

import java.util.List;
import java.util.Map;

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
import environments.LabRecruitsEnvironment;
import eu.fbk.iv4xr.rlbt.QLearningRL;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.spatial.Vec3;
import game.LabRecruitsTestServer;
import game.Platform;
import nl.uu.cs.aplib.mainConcepts.Goal;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import world.BeliefState;
import world.LabEntity;
import nl.uu.cs.aplib.mainConcepts.Tactic;
import environments.LabRecruitsConfig;


/**
 * @author kifetew
 *
 */
public class LabRecruitsRLEnvironment implements Environment {

	
	public static boolean USE_GRAPHICS = false;     /*for running Labrecruit game with graphic*/
    
	private LabRecruitsEnvironment labRecruitsAgentEnvironment = null; 
	private static LabRecruitsTestServer labRecruitsTestServer = null;
	LabRecruitsTestAgent testAgent = null;
	
	private LabRecruitsState currentState = null;
	private double lastReward = 0;
	
	private int updateCycles = 0;
	int MAX_CYCLES = 100;
	private String labRecruitsLevel;
	
	/*set the testing goal entity and entity type*/
	private String goalentity = "door3"; //"button3";
	private String goalentitytype = LabEntity.DOOR;//LabEntity.SWITCH;
	private String goalentitystatus ="isOpen"; //"isOn" for a door, "isOn" for a button
	
	public LabRecruitsRLEnvironment(int maxUpdateCycles, String level) {
		MAX_CYCLES = maxUpdateCycles;
		labRecruitsLevel = level;
	}
	
	/*start RL environment*/
	public void startAgentEnvironment () throws InterruptedException {
		lastReward = 0;
		updateCycles = 0;
		
		startTestServer();

		labRecruitsAgentEnvironment = new LabRecruitsEnvironment(new LabRecruitsConfig(labRecruitsLevel));
		labRecruitsAgentEnvironment.startSimulation();
		
		// create a test agent
		testAgent = new LabRecruitsTestAgent("agent1") // matches the ID in the CSV file
				. attachState(new BeliefState())
				. attachEnvironment(labRecruitsAgentEnvironment);
		
		var dataCollector = new TestDataCollector();
		testAgent.setTestDataCollector(dataCollector);
		
		// set the testing goal here
		//GoalStructure goal = getActionGoal("button1", LabEntity.SWITCH); //observe(); //getActionGoal("door1", LabEntity.DOOR); // getTestGoal ();
		GoalStructure goal = getActionGoal(goalentity, goalentitytype);
		DPrint.ul("Starting Simulation : \n  Goal : "+goalentity +"    "+goal.toString() +"   Entity type : "+goalentitytype+"  status : "+goal.getStatus());
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
		if (entityType == LabEntity.SWITCH) {
			goal = GoalLib.entityInteracted(entityId);
			//System.out.println("In goal structure : entity is a button");
			/*if (testAgent.getState().canInteract(entityId)) {
				goal = SEQ (//GoalLib.entityStateRefreshed(entityId).lift(),
						GoalLib.entityInteracted(entityId));
			}else {
				// entity indicated by the action is not near by,
				// TODO what should be done? currently simply refreshes the entity state
				goal = GoalLib.entityStateRefreshed(entityId);
				//System.out.println("STATUS : BUTTON NOT INTERACTABLE");
			}*/
		} else if(entityType == LabEntity.DOOR) {
			goal = SEQ (GoalLib.entityStateRefreshed(entityId),
					GoalLib.entityInCloseRange(entityId),
					GoalLib.entityInvariantChecked(testAgent,
	        		entityId, 
	        		entityId + " should be open", 
	        		(WorldEntity e) -> e.getBooleanProperty("isOpen")));
		} else {
			// other kind of entity, for example fireHazard, not to interact, do nothing only observe
			goal = observe();// 
			//goal = GoalLib.entityInteracted(entityId);
		}
		return goal;
	}

	
	private void startTestServer (){
		String labRecruitesExeRootDir = System.getProperty("user.dir") ;
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
		LabRecruitsState currentState = new LabRecruitsState(false);
		BeliefState beliefState = testAgent.getState();
		
		for (WorldEntity worldEntity : beliefState.knownEntities()){
			worldEntity.timestamp=0;
			//DPrint.ul("Entity : "+worldEntity.id+ "   type : "+worldEntity.type+" hashcode:"+worldEntity.hashCode()+"  dynamic: "+worldEntity.dynamic+"  extent: "+worldEntity.extent+"  position : "+worldEntity.position+"  has changed state : "+worldEntity.hasChangedState()+"  is moving : "+worldEntity.getStringProperty(labRecruitsLevel));
			//System.out.println("Printing worldmodel key  = ");
			//for (String key : worldEntity.properties.keySet())
	        //    System.out.println(key + " - " + worldEntity.getProperty(key));
	        //System.out.println("Element value : is empty = "+ worldEntity.elements.isEmpty());
	        //for (String key : worldEntity.elements.keySet())
	        //    System.out.println(key + " - " + worldEntity.elements.get(key));
	        
			//LabRecruitsEntityModel labentity = new LabRecruitsEntityModel(worldEntity.id, worldEntity.type, worldEntity.dynamic);
			//labentity.setExtent(worldEntity.extent);
			//labentity.setPosition(worldEntity.position);
			//labentity.setVelocity(worldEntity.velocity);
			//DPrint.ul("Entity : "+worldEntity.id+ "   type : "+worldEntity.type+" hashcode:"+worldEntity.hashCode()+"  dynamic: "+worldEntity.dynamic+"  extent: "+worldEntity.extent+"  position : "+worldEntity.position+"  has changed state : "+worldEntity.hasChangedState()+"  is moving : "+worldEntity.getStringProperty(labRecruitsLevel));
			//currentState.addObject(new LabRecruitsEntityObject(worldEntity));
			currentState.addObject(new LabRecruitsEntityObject(worldEntity));
		}
		DPrint.ul("Current Observation state of Agent  :"+ currentState.toString() );
		return currentState;
	}

	@Override
	public EnvironmentOutcome executeAction(Action a) {
		DPrint.ul("Inside Execute an action  ------- : ");
		State oldState = currentState; // state before execution
		//DPrint.ul ("Old state: "+ oldState.toString());
		
		LabRecruitsAction action = (LabRecruitsAction)a;  // this Action a should be mapped into a goal that the agent can execute
		GoalStructure subGoal = getActionGoal(action.getActionId(), action.getInteractedEntity().type);
		DPrint.ul("Action selected : "+ action.getActionId()+ " interacted entity and type : "+action.getInteractedEntity()+"  "+action.getInteractedEntity().type);
				
		if (subGoal != null) {
			doAction(subGoal);
		} else {
			// TODO this means the agent cannot do anything, so let the current goal continue?
		}

		updateCycles++;
			
		currentState = (LabRecruitsState) currentObservation();  //update current state	after executing the chosen action
		//DPrint.ul ("Updated current state: "+ currentState.toString());
		
		boolean terminated = isFinal(currentState);
		//DPrint.ul("is this the final state:" + terminated);
		lastReward = getReward(oldState, currentState, action);
		//DPrint.ul("get reward :" + lastReward);
		
		EnvironmentOutcome outcome = new EnvironmentOutcome(oldState, action, currentState, lastReward, terminated);
		
		DPrint.ul ("From: " + oldState.toString() + "\n To: " + currentState.toString() + 
				" Action: " + action.actionName() + " Reward: " + lastReward + 
				" Goal status: " + (subGoal != null?subGoal.getStatus().toString():" NULL"));
		return outcome;
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
		GoalStructure goal = getActionGoal(goalentity, goalentitytype); //observe(); //getActionGoal("door1", LabEntity.DOOR); // getTestGoal ();
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
	 * actually make the agent do the selection action. Set the goal to the agent and do some 
	 * update cycles to let the agent perform the goal, some tuning of the number of cycles 
	 * and sleep time could be necessary.
	 * @param goal
	 */
	private void doAction (GoalStructure goal) {
		testAgent.setGoal(goal);
		int maxTicks = 100;	// let the agent run until the current goal is either succeeds or fails?!
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
		return isFinal(currentState) || updateCycles >= MAX_CYCLES;
	}

	@Override
	public void resetEnvironment() {
		
		// TODO agent environment should be restarted only if this is not the last trial,
		// for now we simply call start agent environment, but should be checked
		// if last trial, no start.
		
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
		if (isFinal(state2)) {
			reward = 100;
		} else {
		
			// check if the agent was stuck, don't penalize it
			if (testAgent.getState().isStuck()) {
				reward = 0;			
			} /*else {
				// reward movement, penalise staying at the same position
				Vec3 recentPositions = testAgent.getState().worldmodel.getFloorPosition();//recentPositions.length()
				//List<Vec3> recentPositions = testAgent.getState().getRecentPositions();
				if (recentPositions.length() >= 2) {//(recentPositions.size() >= 2) {
					if (recentPositions.get(recentPositions.length()-1).equals(recentPositions.get(recentPositions.length()-2))) {
						reward = -1;
					} else {
						reward = 0;
					}
				}else {
					// means did not move enough to have more recent positions
					reward = -1;
				}
			}*/
		}
		return reward;
	}

//	@Override
	public boolean isFinal(State state) {
		return isFinal(state, goalentity, goalentitystatus);
	}

	
	/**
	 * helper function to decide whether or not a state is final (wining)
	 * 	- if button, check if it's pushed
	 * 	- if door, check if it's opened
	 * @param state
	 * @param doorId
	 * @param booleanProperty
	 * @return
	 */
	private boolean isFinal (State state, String doorId, String booleanProperty) {
		LabRecruitsState labRecruitsState = (LabRecruitsState)state;
		if (labRecruitsState.getObjectsMap().containsKey(doorId)) {
			LabRecruitsEntityObject entity = (LabRecruitsEntityObject) labRecruitsState.getObjectsMap().get(doorId);
			return entity.labRecruitsEntity.getBooleanProperty(booleanProperty);
		}else {
			return false;
		}
	}
}
