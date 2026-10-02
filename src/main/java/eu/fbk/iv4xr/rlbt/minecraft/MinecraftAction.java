package eu.fbk.iv4xr.rlbt.minecraft;

import java.io.Serializable;

import burlap.mdp.core.action.Action;

/**
 * An action of the Minecraft combat scenario. An action is a bare symbol: the
 * command alone, with no target and no parameter. */
public class MinecraftAction implements Action, Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * APPROACH  move towards the current enemy, up to melee range
	 * ATTACK    hit the current enemy, then wait out the weapon cooldown
	 * RETREAT   move away from the current enemy, along the line joining them
	 */
	public enum Command {
		APPROACH, ATTACK, RETREAT
	}

	private final Command command;

	public MinecraftAction(Command command) {
		this.command = command;
	}

	public Command getCommand() {
		return command;
	}

	@Override
	public String actionName() {
		return command.name();
	}

	@Override
	public Action copy() {
		return new MinecraftAction(command);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof MinecraftAction)) {
			return false;
		}
		return command == ((MinecraftAction) obj).command;
	}

	@Override
	public int hashCode() {
		return command.hashCode();
	}

	@Override
	public String toString() {
		return actionName();
	}
}
