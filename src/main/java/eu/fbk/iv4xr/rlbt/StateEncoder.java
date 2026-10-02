package eu.fbk.iv4xr.rlbt;

import burlap.mdp.core.state.State;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/** Turns a domain state into the fixed-size feature vector that {@link DeepQLearningRL}
 * feeds to its network. */
public interface StateEncoder {

    /** Length of the vector returned by State */
    int inputSize();

    float[] features(State s);

    /** Human-readable description of the layout, e.g. "dist(4) + ownHp(5) + mobHp(5) = 14".
     * Printed into the run summary */
    String describe();

    /** Wraps State into the (1, n) matrix DL4J expects, where rows are
     * the batch size and columns the features. */
    default INDArray encode(State s) {
        float[] f = features(s);
        if (f.length != inputSize())
            throw new IllegalStateException(getClass().getSimpleName() + " produced "
                    + f.length + " features but declares inputSize() = " + inputSize());
        return Nd4j.create(f).reshape(1, inputSize());
    }
}
