package eu.fbk.iv4xr.rlbt.labrecruits;

import burlap.mdp.auxiliary.DomainGenerator;
import burlap.mdp.core.Domain;
import burlap.mdp.core.action.ActionType;
import burlap.mdp.singleagent.SADomain;

public class LabRecruitsDomainGenerator implements DomainGenerator {

	@Override
	public Domain generateDomain() {
		SADomain domain = new SADomain();
		
		ActionType labRecruitsActionType = new LabRecruitsActionType();
		domain.addActionType(labRecruitsActionType );
		
		LabRecruitsSampleModel model = new LabRecruitsSampleModel();
		domain.setModel(model);
		
		return domain;
	}

}
