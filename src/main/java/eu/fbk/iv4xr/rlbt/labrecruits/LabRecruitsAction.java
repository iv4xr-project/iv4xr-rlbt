package eu.fbk.iv4xr.rlbt.labrecruits;

import java.io.Serializable;

import burlap.mdp.core.action.Action;
import eu.iv4xr.framework.mainConcepts.WorldEntity;

/**
 * This class represents an action in the context of LabRecruits. The action in this case represents an interaction 
 * of the agent with a particular entity in the WOM
 * @author kifetew
 *
 */
public class LabRecruitsAction implements Action, Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4299949484336198494L;

	private String actionId;
	
	private WorldEntity interactedEntity;

	public LabRecruitsAction(String id) {
		actionId = id;
	}
	
	/**
	 * @return the actionId
	 */
	public String getActionId() {
		return actionId;
	}

	/**
	 * @param actionId the actionId to set
	 */
	public void setActionId(String actionId) {
		this.actionId = actionId;
	}

	/**
	 * @return the interactedEntity
	 */
	public WorldEntity getInteractedEntity() {
		return interactedEntity;
	}

	/**
	 * @param interactedEntity the interactedEntity to set
	 */
	public void setInteractedEntity(WorldEntity interactedEntity) {
		this.interactedEntity = interactedEntity;
	}

	@Override
	public String actionName() {
		return getActionId();
	}

	@Override
	public Action copy() {
		return new LabRecruitsAction(this.actionId);
	}
	
	@Override
	public String toString() {
		return actionName();
	}
}
