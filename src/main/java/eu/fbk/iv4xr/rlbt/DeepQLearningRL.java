package eu.fbk.iv4xr.rlbt;

import burlap.behavior.policy.EpsilonGreedy;
import burlap.behavior.policy.Policy;
import burlap.behavior.singleagent.Episode;
import burlap.behavior.singleagent.MDPSolver;
import burlap.behavior.singleagent.learning.LearningAgent;
import burlap.behavior.valuefunction.QProvider;
import burlap.behavior.valuefunction.QValue;
import burlap.mdp.core.action.Action;
import burlap.mdp.core.action.ActionUtils;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.SADomain;
import burlap.mdp.singleagent.environment.Environment;
import burlap.mdp.singleagent.environment.EnvironmentOutcome;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsState;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsStateEncoder;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * Deep Q-Network (DQN) implementation, shared by every SUT.
 * The class is domain-agnostic: the SUT enters only through a StateEncoder
 * (input side) and a list of action names (output side).
 * This implementation includes the two core DQN stabilisation techniques:
 * 1. Experience Replay (replay buffer): past transitions (s, a, r, s', done) are
 *    stored in a circular buffer. At each step, a random mini-batch is sampled from
 *    it for training. This breaks the temporal correlation between consecutive samples
 *    and allows each transition to be reused multiple times.
 * 2. Target Network: a frozen copy of the main network used exclusively to compute
 *    the Bellman target. It is re-synchronized with the main network every
 *    targetUpdateFrequency steps. Without it, the target shifts at every gradient
 *    step, causing the network to chase a moving target.
 */
public class DeepQLearningRL extends MDPSolver implements QProvider, LearningAgent {

    private MultiLayerNetwork network;

    /** A frozen copy of the main network used to compute stable
     *  Bellman targets. Re-synchronized every targetUpdateFrequency global steps. */
    private MultiLayerNetwork targetNetwork;

    /** Turns a domain state into the network's input vector: determines the input size. */
    private final StateEncoder encoder;

    /* Action names in the order of the network's output neurons: determines the output
     * size, and is what maps a BURLAP Action back to its column index. */
    private final List<String> actionNames;

    /** Current epsilon for the epsilon-greedy policy. Decays toward epsilonMin over episodes. */
    private double epsilongr;

    /** Floor value for epsilon decay: exploration never drops below this threshold. */
    private double epsilonMin;
    private double decayedEpsilonstep;
    protected EpsilonGreedy learningPolicy;
    protected int maxEpisodeSize;

    /** Number of neurons in each hidden layer of the network. */
    private int hiddenSize;

    /** Step counter within the current episode. Reset at the start of each episode. */
    protected int eStepCounter;

    /** Total number of steps taken across all episodes.
     * Used to schedule target network synchronisation. */
    protected int totalNumberOfSteps = 0;

    /*
     * ---- Replay buffer and target network ----
     *
     * These four are not constants: their right value depends on how long an episode is
     * in the SUT at hand, which varies by almost an order of magnitude between the two
     * (LabRecruits caps an episode at 200 actions, Minecraft at 30). The defaults below
     * are the LabRecruits-sized ones; a SUT overrides what it needs through the setters,
     * which is how burlap.network.* reaches this class.
     *
     * Getting this wrong is silent: with minReplaySize larger than the transitions a
     * whole run produces, training never starts and the serialised model is nothing but
     * its Xavier initialisation. Check "Total training steps" in the network summary.
     */

    private static final int DEFAULT_REPLAY_BUFFER_CAPACITY = 10000;
    private static final int DEFAULT_BATCH_SIZE = 32;
    private static final int DEFAULT_MIN_REPLAY_SIZE = 64;
    private static final int DEFAULT_TARGET_UPDATE_FREQUENCY = 100;

    /** Maximum number of transitions stored in the replay buffer (FIFO, oldest discarded). */
    private int replayBufferCapacity = DEFAULT_REPLAY_BUFFER_CAPACITY;

    /** Number of transitions sampled per training step. */
    private int batchSize = DEFAULT_BATCH_SIZE;

    /** Minimum number of transitions that must be in the buffer before training begins.
     * This warm-up phase ensures the first batches are sufficiently diverse. */
    private int minReplaySize = DEFAULT_MIN_REPLAY_SIZE;

    /** Number of global steps between each copy of the main network weights into the
     * target network. A lower value means faster adaptation but less stability. */
    private int targetUpdateFrequency = DEFAULT_TARGET_UPDATE_FREQUENCY;

    /** The replay buffer: a circular list of past transitions (s, a, r, s', done). */
    private final List<Transition> replayBuffer = new ArrayList<>();

    /** MSE loss of the most recent trainOnBatch() call (NaN until the first one runs).
     * Logged/printed so that training instability (loss exploding or oscillating)
     * can be distinguished from slow-but-stable convergence during experiments. */
    private double lastTrainingLoss = Double.NaN;

    public double getLastTrainingLoss() { return lastTrainingLoss; }

    /** Shared RNG for uniform random sampling from the replay buffer. */
    private final Random random = new Random();

    // ---- Getters ----
    public double getEpsilongr() { return epsilongr; }
    public double getEpsilonMin() { return epsilonMin; }
    public double getDecayedEpsilonstep() { return decayedEpsilonstep; }
    public EpsilonGreedy getLearningPolicy() { return learningPolicy; }
    public int getMaxEpisodeSize() { return maxEpisodeSize; }
    public int getHiddenSize() { return hiddenSize; }
    public int getLastNumSteps() { return eStepCounter; }
    public int getTotalNumberOfSteps() { return totalNumberOfSteps; }

    // ---- Setters ----
    public void setEpsilongr(double epsilongr) { this.epsilongr = epsilongr; }
    public void setEpsilonMin(double epsilonMin) { this.epsilonMin = epsilonMin; }
    public void setDecayedEpsilonStep(double decayedEpsilonStep) { this.decayedEpsilonstep = decayedEpsilonStep; }
    public void setLearningPolicy(EpsilonGreedy learningPolicy) { this.learningPolicy = learningPolicy; }
    public void setMaxEpisodeSize(int maxEpisodeSize) { this.maxEpisodeSize = maxEpisodeSize; }
    public void setTotalNumberOfSteps(int totalNumberOfSteps) { this.totalNumberOfSteps = totalNumberOfSteps; }

    // ---- Replay buffer / target network: getters and setters ----
    public int getReplayBufferCapacity() { return replayBufferCapacity; }
    public int getBatchSize() { return batchSize; }
    public int getMinReplaySize() { return minReplaySize; }
    public int getTargetUpdateFrequency() { return targetUpdateFrequency; }

    public void setReplayBufferCapacity(int replayBufferCapacity) { this.replayBufferCapacity = replayBufferCapacity; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setMinReplaySize(int minReplaySize) { this.minReplaySize = minReplaySize; }
    public void setTargetUpdateFrequency(int targetUpdateFrequency) { this.targetUpdateFrequency = targetUpdateFrequency; }

    /**
     * LabRecruits convenience constructor: there one action interacts with one entity, so
     * a single entity list defines both the encoding and the action space.
     *
     * @param entityIds fixed list of all entity IDs in the level
     */
    public DeepQLearningRL(SADomain domain, double gamma,
                           List<String> entityIds,
                           double learningRate, double epsilon,
                           double decayEpsilonStep, int maxEpisodeSize,
                           double epsilonMin, int hiddenSize) {
        this(domain, gamma, new LabRecruitsStateEncoder(entityIds), entityIds,
                learningRate, epsilon, decayEpsilonStep, maxEpisodeSize, epsilonMin, hiddenSize);
    }

    /**
     * Constructs the DQN agent. Both the main network and the target network are
     * created with the same architecture; the target network is immediately
     * synchronised with the main network so they start identical.
     *
     * @param domain           the domain in which to learn
     * @param gamma            discount factor γ for future rewards (e.g. 0.99)
     * @param encoder          turns a state of this domain into the network input vector
     * @param actionNames      action names, in the order of the network's output neurons
     * @param learningRate     Adam optimizer learning rate (e.g. 0.001)
     * @param epsilon          initial exploration rate (1.0 = full random, 0.0 = greedy)
     * @param decayEpsilonStep amount subtracted from epsilon at the end of each episode
     * @param maxEpisodeSize   maximum steps per episode before forced termination
     * @param epsilonMin       floor value for epsilon: exploration never drops below this
     * @param hiddenSize       number of neurons per hidden layer (both layers use the same size)
     */
    public DeepQLearningRL(SADomain domain, double gamma,
                           StateEncoder encoder, List<String> actionNames,
                           double learningRate, double epsilon,
                           double decayEpsilonStep, int maxEpisodeSize,
                           double epsilonMin, int hiddenSize) {

        /*
         * null: no HashableStateFactory needed — the neural network replaces the Q-table
         * entirely, taking a feature vector as input and returning Q-values as output.
         */
        this.solverInit(domain, gamma, null);
        this.encoder = encoder;
        this.actionNames = actionNames;
        this.epsilongr = epsilon;
        this.epsilonMin = epsilonMin;
        this.decayedEpsilonstep = decayEpsilonStep;
        this.maxEpisodeSize = maxEpisodeSize;
        this.hiddenSize = hiddenSize;
        this.learningPolicy = new EpsilonGreedy(this, epsilon);

        int inputSize = encoder.inputSize();
        int outputSize = actionNames.size();
        System.out.println("DQN network: " + encoder.describe()
                + " -> " + outputSize + " actions " + actionNames);

        this.network = buildNetwork(inputSize, outputSize, learningRate, hiddenSize);

        // Target network starts as an exact copy of the main network
        this.targetNetwork = buildNetwork(inputSize, outputSize, learningRate, hiddenSize);
        updateTargetNetwork();
    }

    /**
     * Builds a fully-connected neural network with two hidden layers (64 units, ReLU)
     * and a linear output layer (IDENTITY activation) to allow unbounded Q-values.
     * Adam is used as the optimizer.
     *
     * @param inputSize  number of input features (= encoder.inputSize())
     * @param outputSize number of output Q-values (= |actionNames|)
     * @param lr         Adam learning rate
     * @param hiddenSize number of neurons per hidden layer (configurable via burlap.network.hidden_size)
     * @return the initialised MultiLayerNetwork (Xavier weight initialisation)
     */
    private MultiLayerNetwork buildNetwork(int inputSize, int outputSize, double lr, int hiddenSize) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new Adam(lr))
                .list()
                .layer(new DenseLayer.Builder()
                        .nIn(inputSize).nOut(hiddenSize).activation(Activation.RELU).build())
                .layer(new DenseLayer.Builder()
                        .nIn(hiddenSize).nOut(hiddenSize).activation(Activation.RELU).build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(hiddenSize).nOut(outputSize).activation(Activation.IDENTITY).build())
                .build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        return net;
    }

    /**
     * True when the observation is unusable and the episode has to be cut short.
     * This is a LabRecruits failure mode: the active agent occasionally reports an empty
     * observation, which encodes to an all-zeros vector indistinguishable from "nothing
     * discovered yet". Minecraft states always carry their three features, so the check
     * simply does not apply there.
     */
    private boolean isEmptyObservation(State s) {
        return (s instanceof LabRecruitsState) && ((LabRecruitsState) s).numObjects() == 0;
    }

    /**
     * Returns all Q-values for a given state via a forward pass on the MAIN network.
     * Required by QProvider — called internally by EpsilonGreedy to select actions.
     *
     * @param s the current state
     * @return list of QValue objects, one per applicable action
     */
    @Override
    public List<QValue> qValues(State s) {
        INDArray qVals = network.output(encoder.encode(s));
        List<Action> actions = ActionUtils.allApplicableActionsForTypes(this.domain.getActionTypes(), s);
        List<QValue> result = new ArrayList<>();
        for (Action a : actions) {
            int idx = actionNames.indexOf(a.actionName());
            double q = (idx >= 0) ? qVals.getDouble(0, idx) : 0.0;
            result.add(new QValue(s, a, q));
        }
        return result;
    }

    /**
     * Returns the Q-value for a specific (state, action) pair via the MAIN network.
     *
     * @param s the state
     * @param a the action
     * @return Q(s, a) according to the current main network
     */
    @Override
    public double qValue(State s, Action a) {
        INDArray qVals = network.output(encoder.encode(s));
        int idx = actionNames.indexOf(a.actionName());
        return (idx >= 0) ? qVals.getDouble(0, idx) : 0.0;
    }

    /**
     * Returns the maximum Q-value over all actions for a given state.
     * Equivalent to V*(s) under the current learned policy.
     *
     * @param s the state
     * @return max_a Q(s, a)
     */
    @Override
    public double value(State s) {
        return network.output(encoder.encode(s)).max(1).getDouble(0);
    }

    @Override
    public Episode runLearningEpisode(Environment env) {
        return this.runLearningEpisode(env, -1);
    }

    /**
     * Runs one full training episode using the full DQN algorithm.
     *
     * At each step:
     *   1. Encode the current state as a feature vector.
     *   2. Select an action via epsilon-greedy (main network, via EpsilonGreedy policy).
     *   3. Execute the action in the LabRecruits environment.
     *   4. Encode the resulting next state.
     *   5. Store the transition (s, a, r, s', done) in the replay buffer.
     *   6. Sample a random mini-batch from the buffer and train the main network
     *      (only once minReplaySize transitions have been collected).
     *   7. Every targetUpdateFrequency steps, copy the main network weights into
     *      the target network.
     *
     * At the end of the episode, epsilon is reduced by decayedEpsilonstep
     * (minimum clamped at 0.1).
     *
     * @param env      the LabRecruits environment
     * @param maxSteps maximum steps before the episode is cut off (-1 = unlimited)
     * @return the Episode record containing all transitions and rewards
     */
    @Override
    public Episode runLearningEpisode(Environment env, int maxSteps) {
        System.out.println("----------DeepQLearningRL : Starting runLearningEpisode()----------------------");
        State curState = env.currentObservation();
        Episode ea = new Episode(curState);
        eStepCounter = 0;

        while (!env.isInTerminalState() && (eStepCounter < maxSteps || maxSteps == -1)) {
            System.out.println("==================DeepQL - Next turn for this episode==================================");

            if (isEmptyObservation(curState)) {
                System.out.println(" BUG : Empty Observation of RL active agent. Ending Episode...");
                break;
            }

            // Step 1: Encode current state as a fixed-size feature vector
            INDArray stateVec = encoder.encode(curState);

            // Step 2: Select action via epsilon-greedy using the main network
            Action action = learningPolicy.action(curState);
            System.out.println("Action Selected : in runLearningEpisode(): " + action.actionName());

            // Step 3: Execute the action in the environment
            EnvironmentOutcome eo = env.executeAction(action);

            // Step 4: Encode the next state
            INDArray nextStateVec = encoder.encode(eo.op);

            // Step 5: Store transition in the replay buffer (.dup() to prevent mutation)
            int actionIdx = actionNames.indexOf(action.actionName());
            addToReplayBuffer(new Transition(stateVec.dup(), actionIdx, eo.r, nextStateVec.dup(), eo.terminated));

            // Step 6: Train the main network on a random mini-batch from the buffer
            trainOnBatch();
            if (!Double.isNaN(lastTrainingLoss)) {
                System.out.println("Training loss (MSE) at step " + totalNumberOfSteps + " = " + lastTrainingLoss);
            }

            // Step 7: Periodically synchronise the target network with the main network
            if (totalNumberOfSteps % targetUpdateFrequency == 0) {
                updateTargetNetwork();
                System.out.println("Target network updated at step " + totalNumberOfSteps);
            }

            ea.transition(action, eo.op, eo.r);
            curState = eo.op;
            eStepCounter++;
            totalNumberOfSteps++;
        }

        System.out.println("=============Episode summary==========================");
        System.out.println("Action sequence " + ea.actionSequence.size() + "  =" + ea.actionSequence);
        System.out.println("Reward sequence " + ea.rewardSequence.size() + "  =" + ea.rewardSequence);
        System.out.println("Epsilon value = " + this.epsilongr);
        System.out.println("Replay buffer size = " + replayBuffer.size());
        System.out.println("Last training loss (MSE) = " + lastTrainingLoss);

        // Decay epsilon at the end of each episode (floor = epsilonMin)
        if (this.epsilongr > this.epsilonMin)
            this.epsilongr = Math.max(this.epsilonMin, this.epsilongr - decayedEpsilonstep);
        this.learningPolicy = new EpsilonGreedy(this, this.epsilongr);
        System.out.println("Decay Epsilon Value : End of an episode = " + this.epsilongr);

        return ea;
    }

    /**
     * Adds a transition to the replay buffer.
     * If the buffer has reached replayBufferCapacity, the oldest entry is removed
     * first (FIFO circular behaviour).
     *
     * @param t the transition to store
     */
    private void addToReplayBuffer(Transition t) {
        if (replayBuffer.size() >= replayBufferCapacity) {
            replayBuffer.remove(0);
        }
        replayBuffer.add(t);
    }

    /**
     * Samples a uniformly random mini-batch from the replay buffer and performs one
     * gradient descent step on the MAIN network.
     *
     * Bellman targets use Double DQN: the next-state action is *selected* by the MAIN
     * network and *evaluated* by the TARGET network:
     *   - terminal transition:     target = r
     *   - non-terminal transition: target = r + γ * Q_target(s', argmax_a' Q_main(s', a'))
     * Selecting and evaluating with the same (target) network, as in vanilla DQN,
     * systematically overestimates Q-values because the max operator picks out the
     * network's own positive estimation errors. Decoupling the two networks for this
     * step reduces that overestimation bias.
     *
     * All batchSize transitions are stacked into matrices and processed in a single
     * network.fit() call, making the update efficient and reducing gradient variance
     * compared to single-sample updates.
     *
     * After fitting, the resulting loss is recorded (see lastTrainingLoss) so that
     * training instability (loss exploding/oscillating) can be told apart from slow
     * but stable convergence when analysing experiment results.
     *
     * Does nothing if the buffer has fewer than minReplaySize transitions.
     */
    private void trainOnBatch() {
        if (replayBuffer.size() < minReplaySize) return;

        // Uniform random sampling (with replacement) from the replay buffer
        List<Transition> batch = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            batch.add(replayBuffer.get(random.nextInt(replayBuffer.size())));
        }

        // Stack individual (1, n) state vectors into (batchSize, n) matrices
        List<INDArray> stateList     = new ArrayList<>(batchSize);
        List<INDArray> nextStateList = new ArrayList<>(batchSize);
        for (Transition t : batch) {
            stateList.add(t.state);
            nextStateList.add(t.nextState);
        }
        INDArray stateBatch     = Nd4j.vstack(stateList);
        INDArray nextStateBatch = Nd4j.vstack(nextStateList);

        // Forward pass on main network  → current Q-values (base for target matrix)
        INDArray currentQBatch = network.output(stateBatch);
        // Double DQN: MAIN network selects the best next action, TARGET network evaluates it
        INDArray nextQBatchMain   = network.output(nextStateBatch);
        INDArray nextQBatchTarget = targetNetwork.output(nextStateBatch);
        INDArray nextBestActions  = nextQBatchMain.argMax(1);

        // Build target matrix: copy current Q-values and overwrite only the executed action
        INDArray targetBatch = currentQBatch.dup();
        for (int i = 0; i < batch.size(); i++) {
            Transition t = batch.get(i);
            double target;
            if (t.terminated) {
                target = t.reward;
            } else {
                int bestNextAction = nextBestActions.getInt(i);
                double doubleQ = nextQBatchTarget.getDouble(i, bestNextAction);
                target = t.reward + this.gamma * doubleQ;
            }
            targetBatch.putScalar(new int[]{i, t.actionIdx}, target);
        }

        // Single gradient descent step on the whole batch
        network.fit(stateBatch, targetBatch);
        lastTrainingLoss = network.score(new DataSet(stateBatch, targetBatch));
    }

    /**
     * Copies the current weights of the main network into the target network.
     * The copy is made with .dup() so that the two sets of parameters remain
     * completely independent after the call.
     * Called every targetUpdateFrequency global steps during training,
     * and also immediately after deserialising a saved model.
     */
    private void updateTargetNetwork() {
        targetNetwork.setParams(network.params().dup());
    }

    /**
     * Tests the learned policy without modifying any internal state.
     * Differences from runLearningEpisode:
     *   - Action selection is always greedy (no epsilon-greedy randomness).
     *   - network.fit() is never called (no learning).
     *   - The replay buffer, epsilon and step counters are never modified.
     *
     * @param env      the LabRecruits environment (must already be started)
     * @param maxSteps maximum steps per test episode (-1 = unlimited)
     * @return the Episode record with all transitions and rewards
     */
    public Episode testDeepQLearningAgent(Environment env, int maxSteps) {
        System.out.println("---------------------------------------------------------------\n Test DeepQLearning agent");
        State curState = env.currentObservation();
        Episode episode = new Episode(curState);
        int stepCounter = 0;

        while (!env.isInTerminalState() && (stepCounter < maxSteps || maxSteps == -1)) {
            if (isEmptyObservation(curState)) {
                System.out.println(" BUG : Empty Observation of RL active agent. Ending Episode...");
                break;
            }

            INDArray qValues = network.output(encoder.encode(curState));
            Action action = getMaxValuedAction(curState, qValues);
            if (action == null) {
                System.out.println("No action available from state: " + curState.toString());
                break;
            }
            System.out.println("Action selected: " + action.actionName());

            EnvironmentOutcome eo = env.executeAction(action);
            episode.transition(action, eo.op, eo.r);
            curState = eo.op;
            stepCounter++;
        }

        System.out.println("=============Test Episode summary==========================");
        System.out.println("Action sequence " + episode.actionSequence.size() + "  =" + episode.actionSequence);
        System.out.println("Reward sequence " + episode.rewardSequence.size() + "  =" + episode.rewardSequence);

        return episode;
    }

    /**
     * Returns the action with the highest Q-value for the given state.
     * Used exclusively by testDeepQLearningAgent() for greedy action selection.
     *
     * @param s       the current state
     * @param qValues the main network output for this state (pre-computed)
     * @return the best action, or null if no applicable actions exist
     */
    private Action getMaxValuedAction(State s, INDArray qValues) {
        List<Action> actions = ActionUtils.allApplicableActionsForTypes(this.domain.getActionTypes(), s);
        Action best = null;
        double maxQ = Double.NEGATIVE_INFINITY;
        for (Action a : actions) {
            int idx = actionNames.indexOf(a.actionName());
            double q = (idx >= 0) ? qValues.getDouble(0, idx) : 0.0;
            if (q > maxQ) {
                maxQ = q;
                best = a;
            }
        }
        return best;
    }

    /**
     * Prints a human-readable summary of the main network architecture and entity mapping.
     * Replaces the Q-table printout used in tabular Q-learning.
     *
     * @param ps the output stream (System.out or a FileOutputStream)
     */
    public void printNetworkSummary(PrintStream ps) {
        ps.println("\n\n=====================Deep Q-Network Summary========================================");
        ps.println("State encoding: " + encoder.describe());
        ps.println("Actions (output order): " + actionNames);
        ps.println("Number of actions: " + actionNames.size());
        ps.println("Network layers: " + network.getnLayers());
        ps.println(network.summary());
        ps.println("Hyperparameters: bufferCapacity=" + replayBufferCapacity
                + ", batchSize=" + batchSize
                + ", minReplaySize=" + minReplaySize
                + ", targetUpdateFrequency=" + targetUpdateFrequency
                + ", hiddenSize=" + hiddenSize);
        ps.println("Replay buffer size: " + replayBuffer.size());
        ps.println("Total training steps: " + totalNumberOfSteps);
        // A gradient step happens only once the buffer passes minReplaySize, so a run can
        // end with a model that was never trained. Say so, instead of leaving it to be
        // inferred from two numbers.
        int trainingSteps = Math.max(0, totalNumberOfSteps - minReplaySize + 1);
        if (replayBuffer.size() < minReplaySize)
            ps.println("WARNING: the buffer never reached minReplaySize (" + replayBuffer.size()
                    + " < " + minReplaySize + "): the network was NEVER trained and these"
                    + " weights are still the Xavier initialisation.");
        else
            ps.println("Gradient steps performed: ~" + trainingSteps);
        ps.println("----------------------------------------------------------------------------");
    }

    /**
     * Saves the main network weights, architecture and Adam updater state to disk
     * using DL4J's ModelSerializer. Saving the updater state allows training to
     * resume without resetting Adam's moment estimates.
     *
     * @param path destination file path (e.g. "rlbt-files/results/qnetwork.ser")
     */
    public void serializeModel(String path) {
        try {
            ModelSerializer.writeModel(network, new File(path), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads the main network weights and architecture from disk.
     * After loading, the target network is immediately re-synchronised with the
     * restored weights so that both networks are consistent.
     *
     * @param path source file path of the previously saved model
     */
    public void deserializeModel(String path) {
        try {
            this.network = ModelSerializer.restoreMultiLayerNetwork(new File(path));
            updateTargetNetwork();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Fully resets the agent: re-initialises both network weight sets (Xavier),
     * clears the replay buffer, and resets all step counters.
     * Epsilon is NOT reset here — use setEpsilongr() separately if needed.
     */
    @Override
    public void resetSolver() {
        this.network.init();
        updateTargetNetwork();
        this.replayBuffer.clear();
        this.eStepCounter = 0;
        this.totalNumberOfSteps = 0;
        this.lastTrainingLoss = Double.NaN;
    }

    private static class Transition {
        final INDArray state;
        final int actionIdx;
        final double reward;
        final INDArray nextState;
        final boolean terminated;

        Transition(INDArray state, int actionIdx, double reward, INDArray nextState, boolean terminated) {
            this.state = state;
            this.actionIdx = actionIdx;
            this.reward = reward;
            this.nextState = nextState;
            this.terminated = terminated;
        }
    }
}