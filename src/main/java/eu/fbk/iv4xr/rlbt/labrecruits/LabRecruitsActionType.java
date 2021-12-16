package eu.fbk.iv4xr.rlbt.labrecruits;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.action.ActionType;
import burlap.mdp.core.oo.state.ObjectInstance;
import burlap.mdp.core.state.State;
import eu.iv4xr.framework.mainConcepts.WorldEntity;

public class LabRecruitsActionType implements ActionType {

	private String typeName = "entityAction";
	public LabRecruitsActionType() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String typeName() {
		return typeName;
	}

	@Override
	public Action associatedAction(String strRep) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Action> allApplicableActions(State s) {
		// get all the interactable entities in this state
		
		List<Action> actions = new ArrayList<Action>();
		Map<String, ObjectInstance> objectsMap = ((LabRecruitsState)s).getObjectsMap();
		for (Entry<String , ObjectInstance> entry : objectsMap.entrySet()) {
			String name = entry.getKey();
			LabRecruitsEntityObject labEntityObject = (LabRecruitsEntityObject) entry.getValue();
			
			WorldEntity entity = (WorldEntity) labEntityObject.get(name);
			LabRecruitsAction labRecruitsAction = new LabRecruitsAction(entity.id);
			labRecruitsAction.setInteractedEntity(entity);
			actions.add(labRecruitsAction);
		}
		return actions;
	}

}
