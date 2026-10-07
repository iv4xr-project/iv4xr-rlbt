package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.action.ActionType;
import burlap.mdp.core.state.State;

/** The action set of the exploring agent: the list built for the level, the same in every
 *  state. Incompatible actions are not filtered out: the agent has to learn them. */
class ExplorerActionType implements ActionType, Serializable {

	private static final long serialVersionUID = 1L;

	private final List<ExplorerAction> actions;

	ExplorerActionType(List<ExplorerAction> actions) {
		this.actions = actions;
	}

	@Override
	public String typeName() {
		return "explorerAction";
	}

	@Override
	public Action associatedAction(String strRep) {
		for (ExplorerAction action : actions)
			if (action.actionName().equals(strRep))
				return action;
		throw new IllegalArgumentException("Unknown action: " + strRep);
	}

	/** @param s ignored: every action is applicable in every state */
	@Override
	public List<Action> allApplicableActions(State s) {
		// a fresh list, so that a caller cannot alter the shared action set
		return new ArrayList<Action>(actions);
	}
}
