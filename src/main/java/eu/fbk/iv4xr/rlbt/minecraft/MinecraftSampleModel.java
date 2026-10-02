package eu.fbk.iv4xr.rlbt.minecraft;

import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.environment.EnvironmentOutcome;
import burlap.mdp.singleagent.model.SampleModel;

/**
 * BURLAP wants a SampleModel on every domain, but Minecraft is stepped through
 * the live server by {@link MinecraftRLEnvironment}, so no transition is ever
 * sampled from a model. The methods below are inert on purpose, mirroring
 * {@link eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsSampleModel}.
 */
public class MinecraftSampleModel implements SampleModel {
    public MinecraftSampleModel() {
    }

    /** Never used: transitions come from the live environment. */
    @Override
    public EnvironmentOutcome sample(State s, Action a) {
        return null;
    }

    /** Never used: termination is decided by {@link MinecraftRLEnvironment}. */
    @Override
    public boolean terminal(State s) {
        return false;
    }

}
