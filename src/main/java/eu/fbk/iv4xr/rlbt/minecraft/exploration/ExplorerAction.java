package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.io.Serializable;

import burlap.mdp.core.action.Action;

/** An action of the exploring agent: a verb and its argument, a target tag (clicked, placed, ...)
 *  or an item (select). Its name is unique, e.g. clicked(lever) or select(candle). */
class ExplorerAction implements Action, Serializable {

	private static final long serialVersionUID = 1L;

	final String verb;
	final String argument;

	ExplorerAction(String verb, String argument) {
		this.verb = verb;
		this.argument = argument;
	}

	@Override
	public String actionName() {
		return verb + "(" + argument + ")";
	}

	@Override
	public Action copy() {
		return new ExplorerAction(verb, argument);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ExplorerAction && actionName().equals(((ExplorerAction) obj).actionName());
	}

	@Override
	public int hashCode() {
		return actionName().hashCode();
	}

	@Override
	public String toString() {
		return actionName();
	}
}
