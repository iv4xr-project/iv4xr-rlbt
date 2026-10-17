package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.util.Arrays;

import burlap.mdp.core.state.State;
import burlap.statehashing.HashableState;
import burlap.statehashing.HashableStateFactory;

/** Q-table key of a state: its encoded vector, the same input the DQN gets. */
class EncodedStateHashFactory implements HashableStateFactory {

	private final ExplorerStateEncoder encoder;

	EncodedStateHashFactory(ExplorerStateEncoder encoder) {
		this.encoder = encoder;
	}

	@Override
	public HashableState hashState(State s) {
		return new EncodedState(s, encoder.features(s));
	}

	static class EncodedState implements HashableState {
		final State state;
		final float[] features;

		EncodedState(State state, float[] features) {
			this.state = state;
			this.features = features;
		}

		@Override
		public State s() {
			return state;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof EncodedState && Arrays.equals(features, ((EncodedState) o).features);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(features);
		}
	}
}
