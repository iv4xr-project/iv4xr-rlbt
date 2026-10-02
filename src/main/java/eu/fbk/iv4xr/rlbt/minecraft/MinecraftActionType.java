package eu.fbk.iv4xr.rlbt.minecraft;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.action.ActionType;
import burlap.mdp.core.state.State;
import eu.fbk.iv4xr.rlbt.minecraft.MinecraftAction.Command;

/** The action set of the Minecraft combat scenario: a fixed, agent-centric list,
 * the same in every state */
public class MinecraftActionType implements ActionType, Serializable {

	private static final long serialVersionUID = 1L;

	private String typeName = "minecraftAction";

	/** One action per command, built once: they are immutable and stateless. */
	private static final List<Action> FIXED_ACTIONS;
	static {
		List<Action> actions = new ArrayList<Action>();
		for (Command command : Command.values())
			actions.add(new MinecraftAction(command));
		FIXED_ACTIONS = actions;
	}

	@Override
	public String typeName() {
		return typeName;
	}

	@Override
	public Action associatedAction(String strRep) {
		return new MinecraftAction(Command.valueOf(strRep));
	}

	/** @param s ignored: every action is applicable in every state */
	@Override
	public List<Action> allApplicableActions(State s) {
		// a fresh list, so that a caller cannot alter the shared action set
		return new ArrayList<Action>(FIXED_ACTIONS);
	}
}
