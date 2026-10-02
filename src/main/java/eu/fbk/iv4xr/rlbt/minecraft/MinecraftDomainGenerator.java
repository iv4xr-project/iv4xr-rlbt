package eu.fbk.iv4xr.rlbt.minecraft;

import burlap.mdp.auxiliary.DomainGenerator;
import burlap.mdp.core.Domain;
import burlap.mdp.core.action.ActionType;
import burlap.mdp.singleagent.SADomain;

public class MinecraftDomainGenerator implements DomainGenerator {
    @Override
    public Domain generateDomain() {
        SADomain domain = new SADomain();

        ActionType minecraftActionType = new MinecraftActionType();
        domain.addActionType(minecraftActionType);

        MinecraftSampleModel model = new MinecraftSampleModel();
        domain.setModel(model);

        return domain;
    }

}
