/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits;

import static nl.uu.cs.aplib.AplibEDSL.SEQ;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import agents.LabRecruitsTestAgent;
import burlap.mdp.core.action.Action;
import burlap.mdp.core.oo.state.generic.GenericOOState;
import burlap.mdp.core.state.State;
import eu.fbk.iv4xr.rlbt.utils.GoalLib;
import eu.iv4xr.framework.mainConcepts.TestAgent;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import world.LabEntity;

/**
 * @author kifetew
 * This class collects the series of actions performed by the RL agent for each episode 
 * and finally it serializes the collected actions to test cases that can later be executed
 * 
 */
public class RLActionToTestCaseEncoder {

	// each entry has an id, sequentially incremented after each action is recorded
	// this is needed since actions are ordered!
	private int id;
	private int episode = 1; // for now all actions would be part of a single episode
	private Map<String, StringBuffer> buffer;
	
	
	private static final String ENTRY_ID = "id";
	private static final String EPISODE = "episode";
	private static final String ENTITY_ID = "entity_id";
	private static final String ENTITY_TYPE = "entity_type";
	private static final String ENTITY_PROPERTY_NAME = "entity_property_name";
	private static final String ENTITY_PROPERTY_VALUE = "entity_property_value";
	private static final String GOAL_STATUS = "goal_status";
	
	private static final String HEADER = String.format("%s,%s,%s,%s,%s,%s,%s,%s", 
			ENTRY_ID, EPISODE, ENTITY_ID, ENTITY_TYPE, ENTITY_PROPERTY_NAME, 
			ENTITY_PROPERTY_VALUE, GOAL_STATUS, System.lineSeparator());
	
	private static RLActionToTestCaseEncoder instance = null;
	
	/**
	 * 
	 */
	private RLActionToTestCaseEncoder() {
		id = 1;
		buffer = new HashMap<>();
	}

	
	public static RLActionToTestCaseEncoder getInstance() {
		if (instance == null) {
			instance = new RLActionToTestCaseEncoder();
		}
		return instance;
	}
	
	
	public void registrAction(Action action, State oldState, State newState, TestAgent agent) {
		// id, episode, entity_id, entity_type, entity_property_name, entity_property_value, goal_status
//		LabRecruitsAction lrAction = (LabRecruitsAction)action;
//		WorldEntity interactedEntity = ((LabRecruitsState)newState).getLabEntityById(entityId); // lrAction.getInteractedEntity();
		LabEntity labEntity = ((LabRecruitsState)newState).getLabEntityById(action.actionName());
		if (labEntity == null) {
			// if entity is null, it means that the new state is empty/null, skip this action
			return;
		}
		String entityId = labEntity.id;
		String entityType = labEntity.type;
		Pair<String, String> propertyNameValue = getPropertyNameValue (labEntity);
		String actionStatus = agent.getLastHandledGoal().getStatus().toString();
		if (!buffer.containsKey(agent.getId())) {
			buffer.put(agent.getId(), new StringBuffer());
			buffer.get(agent.getId()).append(HEADER);
		}
		buffer.get(agent.getId()).append(String.format("%s,%s,%s,%s,%s,%s,%s,%s", 
				id, episode, entityId, entityType, 
				propertyNameValue.getLeft(), propertyNameValue.getRight(), 
				actionStatus, System.lineSeparator()));
		id++;
	}
	
	
	private Pair<String, String> getPropertyNameValue(LabEntity labEntity) {
		switch(labEntity.type) {
		  case LabEntity.DOOR   : return Pair.of("isOpen", String.valueOf(labEntity.getBooleanProperty("isOpen"))) ;
		  case LabEntity.SWITCH : return Pair.of("isOn", String.valueOf(labEntity.getBooleanProperty("isOn"))) ;
		  default:
			  throw new RuntimeException("unsupported entity: " + labEntity.toString());
		}
	}


	public boolean saveActionsToFile (TestAgent agent, String fileName) throws IOException {
		boolean success = true;
		FileUtils.writeStringToFile(new File(fileName), buffer.get(agent.getId()).toString(), Charset.defaultCharset());
		return success;
	}
	
	
	public List<GoalStructure> serliazeActionsToGoals(TestAgent agent) throws IOException{
		List<GoalStructure> goals = serializeActionsToGoals(agent, buffer.get(agent.getId()).toString());
		return goals;
	}
	
	
	public static List<GoalStructure> serializeActionsToGoals(TestAgent agent, String actions) throws IOException{
		List<GoalStructure> goals = new ArrayList<GoalStructure>();
		final CSVParser parser =
				new CSVParserBuilder()
				.withSeparator(',')
				.withIgnoreQuotations(true)
				.build();
		
		final CSVReader reader = 
				new CSVReaderBuilder(new StringReader(actions))
				.withSkipLines(1) // skip first line (header)
				.withCSVParser(parser)
				.build();
		
		String[] line;
		while ((line = reader.readNext()) != null) {
			String id = line[0];
			String episode = line[1];
			String entityId = line[2];
			String entityType = line[3];
			String entityPropertyName = line[4];
			String entityPropertyValue = line[5];
			String goalStatus = line[6];
			
			GoalStructure goal = getGoalStructure (entityId, entityType, entityPropertyName, entityPropertyValue, goalStatus, agent);
			goals.add(goal);
		}
		return goals;
	}
	
	private static GoalStructure getGoalStructure(String entityId, String entityType, String entityPropertyName,
			String entityPropertyValue, String goalStatus, TestAgent testAgent) {
		
		GoalStructure goal = null;
		
		GoalStructure invariantGoal = GoalLib.entityInvariantChecked(testAgent,
        		entityId, 
        		entityId + String.format("entity %s: %s = %s", entityId, entityPropertyName, entityPropertyValue), 
        		(WorldEntity e) -> e.getBooleanProperty(entityPropertyName) == Boolean.valueOf(entityPropertyValue));
		if (entityType.contentEquals(LabEntity.SWITCH)) {
			goal = SEQ (GoalLib.entityInteracted(entityId),
					invariantGoal);
		} else if(entityType.contentEquals(LabEntity.DOOR)) {
			goal = SEQ (GoalLib.entityStateRefreshed(entityId),
					GoalLib.entityInCloseRange(entityId),
					invariantGoal);
		} else if (entityType.contentEquals(LabEntity.GOAL)) {
			goal = GoalLib.entityInCloseRange(entityId);
		} else {
			throw new RuntimeException(String.format("Unexpected entity id: %s, with type: %s", entityId, entityType));
		}
		return goal;
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
