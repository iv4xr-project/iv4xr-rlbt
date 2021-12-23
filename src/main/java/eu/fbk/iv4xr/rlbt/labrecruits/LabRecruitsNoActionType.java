package eu.fbk.iv4xr.rlbt.labrecruits;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.action.ActionType;
import burlap.mdp.core.state.State;

public class LabRecruitsNoActionType implements ActionType, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5835437462000478188L;
	private String typeName = "noAction";
	public LabRecruitsNoActionType() {
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
	/**
	 * TODO define possible non-entity related actions, define a new action type for this
	 */
	public List<Action> allApplicableActions(State s) {
		List<Action> actions = new ArrayList<Action>();
		
		return actions;
	}

}
