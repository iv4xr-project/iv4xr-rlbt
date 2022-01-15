package eu.fbk.iv4xr.rlbt;

import burlap.behavior.learningrate.ConstantLR;
import burlap.behavior.learningrate.LearningRate;
import burlap.behavior.policy.EpsilonGreedy;
import burlap.behavior.policy.GreedyQPolicy;
import burlap.behavior.policy.Policy;
import burlap.behavior.singleagent.Episode;
import burlap.behavior.singleagent.MDPSolver;
import burlap.behavior.singleagent.learning.LearningAgent;
import burlap.behavior.singleagent.learning.tdmethods.QLearningStateNode;
import burlap.behavior.singleagent.options.EnvironmentOptionOutcome;
import burlap.behavior.singleagent.options.Option;
import burlap.behavior.singleagent.planning.Planner;
import burlap.behavior.valuefunction.ConstantValueFunction;
import burlap.behavior.valuefunction.QFunction;
import burlap.behavior.valuefunction.QProvider;
import burlap.behavior.valuefunction.QValue;
import burlap.debugtools.DPrint;
import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.SADomain;
import burlap.mdp.singleagent.environment.Environment;
import burlap.mdp.singleagent.environment.EnvironmentOutcome;
import burlap.mdp.singleagent.environment.SimulatedEnvironment;
import burlap.mdp.singleagent.model.RewardFunction;
import burlap.statehashing.HashableState;
import burlap.statehashing.HashableStateFactory;
import eu.fbk.iv4xr.rlbt.utils.SerializationUtil;
import eu.fbk.iv4xr.rlbt.utils.Utils;

import org.yaml.snakeyaml.Yaml;

import javax.management.RuntimeErrorException;
import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Tabular Q-learning algorithm [1]. This implementation will work correctly with Options [2]. The implementation can either be used for learning or planning,
 * the latter of which is performed by running many learning episodes in succession in a {@link burlap.mdp.singleagent.environment.SimulatedEnvironment}.
 * If you are going to use this algorithm for planning, call the {@link #initializeForPlanning(int)}
 * method before calling {@link #planFromState(State)}.
 * The number of episodes used for planning can be determined
 * by a threshold maximum number of episodes, or by a maximum change in the Q-function threshold.
 * <p>
 * By default, this agent will use an epsilon-greedy policy with epsilon=0.1. You can change the learning policy to
 * anything with the {@link #setLearningPolicy(burlap.behavior.policy.Policy)} policy.
 * <p>
 * If you
 * want to use a custom learning rate decay schedule rather than a constant learning rate, use the
 * {@link #setLearningRateFunction(burlap.behavior.learningrate.LearningRate)}.
 * <p>
 * 1. Watkins, Christopher JCH, and Peter Dayan. "Q-learning." Machine learning 8.3-4 (1992): 279-292. <p>
 * 2. Sutton, Richard S., Doina Precup, and Satinder Singh. "Between MDPs and semi-MDPs: A framework for temporal abstraction in reinforcement learning." Artificial intelligence 112.1 (1999): 181-211.
 * 
 * @author James MacGlashan
 *
 */
public class QLearningRL extends MDPSolver implements QProvider, LearningAgent, Planner{


	/**
	 * The tabular mapping from states to Q-values
	 */
	protected Map<HashableState, QLearningStateNode> 				qFunction;
	
	/**
	 * The object that defines how Q-values are initialized.
	 */
	protected QFunction 											qInitFunction;
	
	

	public Map<HashableState, QLearningStateNode> getqFunction() {
		return qFunction;
	}


	public void setqFunction(Map<HashableState, QLearningStateNode> qFunction) {
		this.qFunction = qFunction;
	}

	/**
	 * Epsilon value (exploit-explore)
	 */
	protected double							 				epsilongr;
	/**
	 * The learning rate function used.
	 */
	protected LearningRate											learningRate;
	
	public QFunction getqInitFunction() {
		return qInitFunction;
	}


	public void setqInitFunction(QFunction qInitFunction) {
		this.qInitFunction = qInitFunction;
	}


	public LearningRate getLearningRate() {
		return learningRate;
	}


	public void setLearningRate(LearningRate learningRate) {
		this.learningRate = learningRate;
	}


	public int getMaxEpisodeSize() {
		return maxEpisodeSize;
	}


	public void setMaxEpisodeSize(int maxEpisodeSize) {
		this.maxEpisodeSize = maxEpisodeSize;
	}


	public int geteStepCounter() {
		return eStepCounter;
	}


	public void seteStepCounter(int eStepCounter) {
		this.eStepCounter = eStepCounter;
	}


	public int getNumEpisodesForPlanning() {
		return numEpisodesForPlanning;
	}


	public void setNumEpisodesForPlanning(int numEpisodesForPlanning) {
		this.numEpisodesForPlanning = numEpisodesForPlanning;
	}


	public double getMaxQChangeForPlanningTermination() {
		return maxQChangeForPlanningTermination;
	}


	public void setMaxQChangeForPlanningTermination(double maxQChangeForPlanningTermination) {
		this.maxQChangeForPlanningTermination = maxQChangeForPlanningTermination;
	}


	public double getMaxQChangeInLastEpisode() {
		return maxQChangeInLastEpisode;
	}


	public void setMaxQChangeInLastEpisode(double maxQChangeInLastEpisode) {
		this.maxQChangeInLastEpisode = maxQChangeInLastEpisode;
	}


	public boolean isShouldDecomposeOptions() {
		return shouldDecomposeOptions;
	}


	public void setShouldDecomposeOptions(boolean shouldDecomposeOptions) {
		this.shouldDecomposeOptions = shouldDecomposeOptions;
	}


	public int getTotalNumberOfSteps() {
		return totalNumberOfSteps;
	}


	public void setTotalNumberOfSteps(int totalNumberOfSteps) {
		this.totalNumberOfSteps = totalNumberOfSteps;
	}


	public Policy getLearningPolicy() {
		return learningPolicy;
	}


	/**
	 * The learning policy to use. Typically these will be policies that link back to this object so that they change as the Q-value estimate change.
	 */
	protected Policy												learningPolicy;


	/**
	 * The maximum number of steps that will be taken in an episode before the agent terminates a learning episode
	 */
	protected int													maxEpisodeSize;
	
	/**
	 * A counter for counting the number of steps in an episode that have been taken thus far
	 */
	protected int													eStepCounter;
	
	/**
	 * The maximum number of episodes to use for planning
	 */
	protected int													numEpisodesForPlanning;
	
	/**
	 * The maximum allowable change in the Q-function during an episode before the planning method terminates.
	 */
	protected double												maxQChangeForPlanningTermination;
	
	/**
	 * The maximum Q-value change that occurred in the last learning episode.
	 */
	protected double												maxQChangeInLastEpisode = Double.POSITIVE_INFINITY;

	
	
	/**
	 * Whether options should be decomposed into actions in the returned {@link Episode} objects.
	 */
	protected boolean												shouldDecomposeOptions = true;

	
	
	/**
	 * The total number of learning steps performed by this agent.
	 */
	protected int													totalNumberOfSteps = 0;
	
	
	/**
	 * Initializes Q-learning with 0.1 epsilon greedy policy, the same Q-value initialization everywhere, and places no limit on the number of steps the 
	 * agent can take in an episode. By default the agent will only save the last learning episode and a call to the {@link #planFromState(State)} method
	 * will cause the valueFunction to use only one episode for planning; this should probably be changed to a much larger value if you plan on using this
	 * algorithm as a planning algorithm.
	 * @param domain the domain in which to learn
	 * @param gamma the discount factor
	 * @param hashingFactory the state hashing factory to use for Q-lookups
	 * @param qInit the initial Q-value to user everywhere
	 * @param learningRate the learning rate
	 */
	public QLearningRL(SADomain domain, double gamma, HashableStateFactory hashingFactory,
			double qInit, double learningRate) {
		this.QLInit(domain, gamma, hashingFactory, new ConstantValueFunction(qInit), learningRate, new EpsilonGreedy(this, 0.1), Integer.MAX_VALUE);
	}

	public QLearningRL(SADomain domain, double gamma, HashableStateFactory hashingFactory,
			double qInit, double learningRate, double epsilonval, int maxEpisodeSize) {
		this.QLInit(domain, gamma, hashingFactory, new ConstantValueFunction(qInit), learningRate, new EpsilonGreedy(this, epsilonval), epsilonval, maxEpisodeSize);
	}

	/**
	 * Initializes Q-learning with 0.1 epsilon greedy policy, the same Q-value initialization everywhere. By default the agent will only save the last learning episode and a call to the {@link #planFromState(State)} method
	 * will cause the valueFunction to use only one episode for planning; this should probably be changed to a much larger value if you plan on using this
	 * algorithm as a planning algorithm.
	 * @param domain the domain in which to learn
	 * @param gamma the discount factor
	 * @param hashingFactory the state hashing factory to use for Q-lookups
	 * @param qInit the initial Q-value to user everywhere
	 * @param learningRate the learning rate
	 * @param maxEpisodeSize the maximum number of steps the agent will take in a learning episode for the agent stops trying.
	 */
	public QLearningRL(SADomain domain, double gamma, HashableStateFactory hashingFactory,
			double qInit, double learningRate, int maxEpisodeSize) {
		this.QLInit(domain, gamma, hashingFactory, new ConstantValueFunction(qInit), learningRate, new EpsilonGreedy(this, 0.1), maxEpisodeSize);
	}
	
	
	/**
	 * Initializes the same Q-value initialization everywhere. Note that if the provided policy is derived from the Q-value of this learning agent (as it should be),
	 * you may need to set the policy to point to this object after call this constructor; the constructor will not do this automatically in case it was by design
	 * to use the policy that was learned in some other domain. By default the agent will only save the last learning episode and a call to the {@link #planFromState(State)} method
	 * will cause the valueFunction to use only one episode for planning; this should probably be changed to a much larger value if you plan on using this
	 * algorithm as a planning algorithm.
	 * @param domain the domain in which to learn
	 * @param gamma the discount factor
	 * @param hashingFactory the state hashing factory to use for Q-lookups
	 * @param qInit the initial Q-value to user everywhere
	 * @param learningRate the learning rate
	 * @param learningPolicy the learning policy to follow during a learning episode.
	 * @param maxEpisodeSize the maximum number of steps the agent will take in a learning episode for the agent stops trying.
	 */
	public QLearningRL(SADomain domain, double gamma, HashableStateFactory hashingFactory,
			double qInit, double learningRate, Policy learningPolicy, int maxEpisodeSize) {
		this.QLInit(domain, gamma, hashingFactory, new ConstantValueFunction(qInit), learningRate, learningPolicy, maxEpisodeSize);
	}
	
	
	/**
	 * Initializes the algorithm. Note that if the provided policy is derived from the Q-value of this learning agent (as it should be),
	 * you may need to set the policy to point to this object after call this constructor; the constructor will not do this automatically in case it was by design
	 * to use the policy that was learned in some other domain. By default the agent will only save the last learning episode and a call to the {@link #planFromState(State)} method
	 * will cause the valueFunction to use only one episode for planning; this should probably be changed to a much larger value if you plan on using this
	 * algorithm as a planning algorithm.
	 * @param domain the domain in which to learn
	 * @param gamma the discount factor
	 * @param hashingFactory the state hashing factory to use for Q-lookups
	 * @param qInit a {@link burlap.behavior.valuefunction.QFunction} object that can be used to initialize the Q-values.
	 * @param learningRate the learning rate
	 * @param learningPolicy the learning policy to follow during a learning episode.
	 * @param maxEpisodeSize the maximum number of steps the agent will take in a learning episode for the agent stops trying.
	 */
	public QLearningRL(SADomain domain, double gamma, HashableStateFactory hashingFactory,
			QFunction qInit, double learningRate, Policy learningPolicy, int maxEpisodeSize) {
		this.QLInit(domain, gamma, hashingFactory, qInit, learningRate, learningPolicy, maxEpisodeSize);
	}
	
	
	
	/**
	 * Initializes the algorithm. By default the agent will only save the last learning episode and a call to the {@link #planFromState(State)} method
	 * will cause the valueFunction to use only one episode for planning; this should probably be changed to a much larger value if you plan on using this
	 * algorithm as a planning algorithm.
	 * @param domain the domain in which to learn
	 * @param gamma the discount factor
	 * @param hashingFactory the state hashing factory to use for Q-lookups
	 * @param qInitFunction a {@link burlap.behavior.valuefunction.QFunction} object that can be used to initialize the Q-values.
	 * @param learningRate the learning rate
	 * @param learningPolicy the learning policy to follow during a learning episode.
	 * @param maxEpisodeSize the maximum number of steps the agent will take in a learning episode for the agent stops trying.
	 */
	protected void QLInit(SADomain domain, double gamma, HashableStateFactory hashingFactory,
						  QFunction qInitFunction, double learningRate, Policy learningPolicy, int maxEpisodeSize){
		
		this.solverInit(domain, gamma, hashingFactory);
		this.qFunction = new HashMap<HashableState, QLearningStateNode>();
		this.learningRate = new ConstantLR(learningRate);
		this.learningPolicy = learningPolicy;
		this.maxEpisodeSize = maxEpisodeSize;
		this.qInitFunction = qInitFunction;
		
		numEpisodesForPlanning = 1;
		maxQChangeForPlanningTermination = 0.;

		
	}

	protected void QLInit(SADomain domain, double gamma, HashableStateFactory hashingFactory,
			  QFunction qInitFunction, double learningRate, Policy learningPolicy, double epsilonval,int maxEpisodeSize){
		this.solverInit(domain, gamma, hashingFactory);
		this.qFunction = new HashMap<HashableState, QLearningStateNode>();
		this.learningRate = new ConstantLR(learningRate);
		this.learningPolicy = learningPolicy;
		this.maxEpisodeSize = maxEpisodeSize;
		this.qInitFunction = qInitFunction;
		this.epsilongr= epsilonval;
		
		numEpisodesForPlanning = 1;
		maxQChangeForPlanningTermination = 0.;
}
	/**
	 * Sets the {@link RewardFunction}, {@link burlap.mdp.core.TerminalFunction},
	 * and the number of simulated episodes to use for planning when
	 * the {@link #planFromState(State)} method is called.
	 * @param numEpisodesForPlanning the number of simulated episodes to run for planning.
	 */
	public void initializeForPlanning(int numEpisodesForPlanning){
		this.numEpisodesForPlanning = numEpisodesForPlanning;
	}
	
	
	/**
	 * Sets the learning rate function to use
	 * @param lr the learning rate function to use
	 */
	public void setLearningRateFunction(LearningRate lr){
		this.learningRate = lr;
	}
	
	/**
	 * Sets how to initialize Q-values for previously unexperienced state-action pairs.
	 * @param qInit a {@link burlap.behavior.valuefunction.QFunction} object that can be used to initialize the Q-values.
	 */
	public void setQInitFunction(QFunction qInit){
		this.qInitFunction = qInit;
	}
	
	
	/**
	 * Sets which policy this agent should use for learning.
	 * @param p the policy to use for learning.
	 */
	public void setLearningPolicy(Policy p){
		this.learningPolicy = p;
	}

	/**
	 * Sets the maximum number of episodes that will be performed when the {@link #planFromState(State)} method is called.
	 * @param n the maximum number of episodes that will be performed when the {@link #planFromState(State)} method is called.
	 */
	public void setMaximumEpisodesForPlanning(int n){
		if(n > 0){
			this.numEpisodesForPlanning = n;
		}
		else{
			this.numEpisodesForPlanning = 1;
		}
	}

	/**
	 * Sets a max change in the Q-function threshold that will cause the {@link #planFromState(State)} to stop planning
	 * when it is achieved.
	 * @param m the maximum allowable change in the Q-function before planning stops
	 */
	public void setMaxQChangeForPlanningTerminaiton(double m){
		if(m > 0.){
			this.maxQChangeForPlanningTermination = m;
		}
		else{
			this.maxQChangeForPlanningTermination = 0.;
		}
	}
	
	/**
	 * Returns the number of steps taken in the last episode;
	 * @return the number of steps taken in the last episode;
	 */
	public int getLastNumSteps(){
		return eStepCounter;
	}
	
	
	/**
	 * Sets whether the primitive actions taken during an options will be included as steps in returned EpisodeAnalysis objects.
	 * The default value is true. If this is set to false, then EpisodeAnalysis objects returned from a learning episode will record options
	 * as a single "action" and the steps taken by the option will be hidden. 
	 * @param toggle whether to decompose options into the primitive actions taken by them or not.
	 */
	public void toggleShouldDecomposeOption(boolean toggle){
		
		this.shouldDecomposeOptions = toggle;
	}


	@Override
	public List<QValue> qValues(State s) {
		return this.getQs(this.stateHash(s));
	}

	@Override
	public double qValue(State s, Action a) {
		return this.getQ(this.stateHash(s), a).q;
	}
	
	
	/**
	 * Returns the possible Q-values for a given hashed stated.
	 * @param s the hashed state for which to get the Q-values.
	 * @return the possible Q-values for a given hashed stated.
	 */
	protected List<QValue> getQs(HashableState s) {
		QLearningStateNode node = this.getStateNode(s);
		return node.qEntry; 
	}


	/**
	 * Returns the Q-value for a given hashed state and action.
	 * @param s the hashed state
	 * @param a the action
	 * @return the Q-value for a given hashed state and action; null is returned if there is not Q-value currently stored.
	 */
	protected QValue getQ(HashableState s, Action a) {
		QLearningStateNode node = this.getStateNode(s);

		for(QValue qv : node.qEntry){
			if(qv.a.equals(a)){
				return qv;
			}
		}
		
		return null; //no action for this state indexed
	}


	@Override
	public double value(State s) {
		return Helper.maxQ(this, s);
	}
	
	/**
	 * Returns the {@link QLearningStateNode} object stored for the given hashed state. If no {@link QLearningStateNode} object.
	 * is stored, then it is created and has its Q-value initialize using this objects {@link burlap.behavior.valuefunction.QFunction} data member.
	 * @param s the hashed state for which to get the {@link QLearningStateNode} object
	 * @return the {@link QLearningStateNode} object stored for the given hashed state. If no {@link QLearningStateNode} object.
	 */
	protected QLearningStateNode getStateNode(HashableState s){
		//System.out.println("Inside QLearningStateNode() -  Q function size: "+qFunction.size());
		QLearningStateNode node = qFunction.get(s);
		
		if(node == null){
			node = new QLearningStateNode(s);
			List<Action> gas = this.applicableActions(s.s());
			if(gas.isEmpty()){
				gas = this.applicableActions(s.s());
				throw new RuntimeErrorException(new Error("No possible actions in this state, cannot continue Q-learning"));
			}
			for(Action ga : gas){
				node.addQValue(ga, qInitFunction.qValue(s.s(), ga));
			}
			
			qFunction.put(s, node);
			//System.out.println("Qlearning -  state not found. Making new entry, Q function size : " +  qFunction.size());
			
		}
		else {
			//System.out.println("Qlearning -  State exists. Q table size = "+  qFunction.size());
		}
		return node;
		
	}
	
	/**
	 * Returns the maximum Q-value in the hashed stated.
	 * @param s the state for which to get he maximum Q-value;
	 * @return the maximum Q-value in the hashed stated.
	 */
	protected double getMaxQ(HashableState s){
		//System.out.println("IN getMaxQ() function");
		double max = Double.NEGATIVE_INFINITY;
		
		if (qFunction.containsKey(s)) {
			QLearningStateNode node = qFunction.get(s);
			//System.out.println("In getMaxQ , got state ="+s);
			List <QValue> qs = node.qEntry;//this.getQs(s);		
			for(QValue q : qs){
				if(q.q > max){
					max = q.q;
				}
			}
		}		
		// tofix : avoid sending negative infinity value
		if (max == Double.NEGATIVE_INFINITY) {
			max=0;
		}		
		return max;
	}
	

	/**
	 * Plans from the input state and then returns a {@link burlap.behavior.policy.GreedyQPolicy} that greedily
	 * selects the action with the highest Q-value and breaks ties uniformly randomly.
	 * @param initialState the initial state of the planning problem
	 * @return a {@link burlap.behavior.policy.GreedyQPolicy}.
	 */
	@Override
	public GreedyQPolicy planFromState(State initialState) {

		if(this.model == null){
			throw new RuntimeException("QLearning (and its subclasses) cannot execute planFromState because a model is not specified.");
		}

		SimulatedEnvironment env = new SimulatedEnvironment(this.domain, initialState);

		int eCount = 0;
		do{
			this.runLearningEpisode(env, this.maxEpisodeSize);
			eCount++;
		}while(eCount < numEpisodesForPlanning && maxQChangeInLastEpisode > maxQChangeForPlanningTermination);


		return new GreedyQPolicy(this);

	}

	@Override
	public Episode runLearningEpisode(Environment env) {
		return this.runLearningEpisode(env, -1);
		//return this.runLearningEpisode(env, 20);
	}

	
	
	@Override
	public Episode runLearningEpisode(Environment env, int maxSteps) {	
		//System.out.println("Starting runLearningEpisode()");
		State initialState = env.currentObservation();		
		Episode ea = new Episode(initialState);
		HashableState curState = this.stateHash(initialState);
//		System.out.println("Hashed state = " +curState);
		//System.out.println("In runLearningEpisode() - Starting while() loop");
		
		eStepCounter = 0;

		maxQChangeInLastEpisode = 0.;
		while(!env.isInTerminalState() && (eStepCounter < maxSteps || maxSteps == -1)){
			Action action = learningPolicy.action(curState.s());
			printQtable(curState);
			//System.out.println("Inside runLearningEpisode(), selected action for this pass : "+action.actionName());
			//System.out.println("Get Q value");
			QValue curQ = this.getQ(curState, action);
			
			EnvironmentOutcome eo;
			if(!(action instanceof Option)){
				eo = env.executeAction(action);
			}
			else{
				eo = ((Option)action).control(env, this.gamma);
			}
			//System.out.println("Action executed, returned in runLearningEpisode()- look for max q ");
			HashableState nextState = this.stateHash(eo.op);
			//System.out.println("In runlearningepisode() ,hashed current state = "+curState.toString()+"  \n  next state = "+nextState.toString());
			double maxQ = 0.;

//			System.out.println("hash state availabe in q table =  "+checkhashentry(nextState)); 
			
			if(!eo.terminated){
				maxQ = this.getMaxQ(nextState);
			}
			//System.out.println("After getMaxQ() function - returned in runlearningepisodes()");
			//manage option specifics
			double r = eo.r;
			double discount = eo instanceof EnvironmentOptionOutcome ? ((EnvironmentOptionOutcome)eo).discount : this.gamma;
			int stepInc = eo instanceof EnvironmentOptionOutcome ? ((EnvironmentOptionOutcome)eo).numSteps() : 1;
			eStepCounter += stepInc;

			if(!(action instanceof Option) || !this.shouldDecomposeOptions){
				ea.transition(action, nextState.s(), r);
			}
			else{
				ea.appendAndMergeEpisodeAnalysis(((EnvironmentOptionOutcome)eo).episode);
			}
			
			double oldQ = curQ.q;

			//update Q-value
			//System.out.println("In runlearningepisodes() - update Q value");
			double vv = this.learningRate.pollLearningRate(this.totalNumberOfSteps, curState.s(), action) * (r + (discount * maxQ) - curQ.q);
			curQ.q = curQ.q + vv;//this.learningRate.pollLearningRate(this.totalNumberOfSteps, curState.s(), action) * (r + (discount * maxQ) - curQ.q);
			double deltaQ = Math.abs(oldQ - curQ.q);
			
			if(deltaQ > maxQChangeInLastEpisode){
				maxQChangeInLastEpisode = deltaQ;
			}

			//move on polling environment for its current state in case it changed during processing
			curState = this.stateHash(env.currentObservation());
			this.totalNumberOfSteps++;
		}
		//System.out.println("End of an episode");
		System.out.println("Epsilon value = "+ this.epsilongr);
		this.epsilongr = this.epsilongr*0.9;  // multiply with a value <1 to decay the epsilon value
		System.out.println("Decay Epsilo Value : End of an episode = "+this.epsilongr);
		//initialize learningpolicy with new reduced epsilon value in order to reduce exploration after each episode
		this.learningPolicy= new EpsilonGreedy(this, this.epsilongr);
		
		return ea;
	}
	
	public void printFinalQtable(PrintStream printStream) {
		printStream.println("\n\n=====================Q-Table========================================");
		printStream.println("Qtable size = "+this.qFunction.keySet().size());
		printStream.println("----------------------------------------------------------------------------");
		for (HashableState key:this.qFunction.keySet()){
			QLearningStateNode node = qFunction.get(key);
			printStream.println("State = "+node.s.s());
			printStream.println("Number of Q value entry= "+ node.qEntry.size());
			for (int i=0; i<node.qEntry.size();i++) {
				printStream.print("action: "+node.qEntry.get(i).a.actionName());
				printStream.print("	value: "+node.qEntry.get(i).q);	
				printStream.print("\n");
				}
			}
		printStream.println("----------------------------------------------------------------------------");
	}  // end of the function

	
	private void printQtable(HashableState curState) {
		QLearningStateNode value = qFunction.get(curState);
		List<QValue> qEntry = value.qEntry;
		for (QValue qvalue : qEntry) {
			System.out.println(qvalue.a.actionName() + " -> " + qvalue.q);
		}
//		System.out.println("=====================printing q table keys========================================");
//		for (HashableState key:this.qFunction.keySet()){
//			for (HashableState key2: qFunction.keySet()) {
//				System.out.println("Hashed state = " + key + "=" + key.hashCode());
//				System.out.println("Hashed state = " + key2 + "=" + key2.hashCode());
//				if (key.hashCode() == key2.hashCode() && key.toString().equalsIgnoreCase(key2.toString())
//						&& !key.equals(key2)) {
//					System.err.println("Anomalous case");
//					key.equals(key2);
//				}
//			}
//		}		
//			System.out.println("===================================================================================");	
//			if (qFunction.containsKey(curState)){
//				System.out.println("Found  state : "+curState);
//				}
//			else
//				System.out.println("NOt found"+curState);
	}


	private boolean checkhashentry(HashableState curState) {
		// TODO Auto-generated method stub
		
		boolean flag=false;
		for (HashableState key:this.qFunction.keySet()){ {
			if (key.equals(curState)) {
			//if (key.hashCode()==curState.hashCode()){
				//System.out.println("state is : "+ curState.s().toString());
				//System.out.println("Check:this state already exists "+ key.hashCode() + curState.hashCode());
				flag=true;//return true;
				break;
				}
			}
		}
		if (flag==false) {
			//System.out.println("state is : "+ curState.s().toString());
			//System.out.println("Check:this state does not exist");
		}
		assert (this.qFunction.containsKey(curState) == flag);
		
		return flag;
		//return false;
		/*if (qFunction.containsKey(curState)){
			System.out.println("this key exists");
			return true;			
		}
		else
			return false;*/
		
		
	}


	/*---------------------Test QLearning Agent------------------------------------------------------------------------*/
	public Episode testQLearingAgent(Environment env, int maxSteps) {	
		System.out.println("---------------------------------------------------------------\n Test  QLearning agent");
		env.resetEnvironment();
		State initialState = env.currentObservation();		
		Episode episode = new Episode(initialState);
		HashableState curState = this.stateHash(initialState);
		
		System.out.println("Hashed state = " +curState+ "hash value  = "+ curState.hashCode());
		//System.out.println("In runLearningEpisode() - Starting while() loop");
		eStepCounter = 0;

		//maxQChangeInLastEpisode = 0.;
		while(!env.isInTerminalState() && (eStepCounter < maxSteps || maxSteps == -1)){
			Action action = getMaxValuedAction(curState);//learningPolicy.action(curState.s());
			if (action != null) {
				QValue curQ = this.getQ(curState, action);
				
				EnvironmentOutcome eo;
				if(!(action instanceof Option)){
					eo = env.executeAction(action);
				}
				else{
					eo = ((Option)action).control(env, this.gamma);
				}
				System.out.println("Action executed, returned in TestQLearingAgent()"+ action.actionName());
				curState = this.stateHash(eo.op);
				
				// update Episode object
				if(!(action instanceof Option) || !this.shouldDecomposeOptions){
					episode.transition(action, curState.s(), eo.r);
				}
				else{
					throw new RuntimeException("Should not reach here");
					//episode.appendAndMergeEpisodeAnalysis(((EnvironmentOptionOutcome)eo).episode);
				}
				
				//System.out.println("In TestQLearingAgent() ,hashed current state = "+curState.hashCode()+"  \n  next state = "+nextState.hashCode());
				
				// TODO code below is commented because it's not necessary. currentObservation() is already called in executeAction()
				//move on polling environment for its current state in case it changed during processing
				//curState = this.stateHash(env.currentObservation());
				//System.out.println("TestQLearingAgent()  -- checking cur state  after action execution = "+ curState.s().toString());
				//this.totalNumberOfSteps++;
			}else {
				// FIXME this is added to avoid an NPE, not sure it's the correct way
				System.out.println("No action available from state: " + curState.s().toString());
				break; 
			}
		}
		return episode;
	}


	// Testing Q-learning agent
	public Action getMaxValuedAction(HashableState curstate) {	
		System.out.println("getmaxvalueaction ()  - Q table size = "+ this.qFunction.size());
//		Action acc = null;
		double max = Double.NEGATIVE_INFINITY;
		Set<Action> optimalActions = new HashSet<>();
		if (qFunction.containsKey(curstate)) {
			QLearningStateNode node = qFunction.get(curstate);
			System.out.println("In getMaxQ , got state ="+curstate.s());
			List <QValue> qs = node.qEntry;//this.getQs(s);		
			for(QValue q : qs){
				if(q.q > max){
					max = q.q;
//					acc = q.a;
					optimalActions.clear();
					optimalActions.add(q.a);
				}else if (q.q == max) {
					optimalActions.add(q.a);
				}
			}	
		}				
		System.out.println("Choosing one among: " + optimalActions.size() + " actions");
		return Utils.choice(optimalActions);		
	}
		
	// Testing Q-learning agent
	public Action _getMaxValuedAction(HashableState curstate) {	
		System.out.println("getmaxvalueaction ()  - Q table size = "+ this.qFunction.size());
		Action acc = null;
		double max = Double.NEGATIVE_INFINITY;
		if (qFunction.containsKey(curstate)) {
			QLearningStateNode node = qFunction.get(curstate);
			System.out.println("In getMaxQ , got state ="+curstate.s());
			List <QValue> qs = node.qEntry;//this.getQs(s);		
			for(QValue q : qs){
				if(q.q >= max){
					max = q.q;
					acc = q.a;
				}
			}	
		}				
//		System.out.println("Choosing one among: " + optimalActions.size() + " actions");
		return acc;		
	}
	
	@Override
	public void resetSolver(){
		this.qFunction.clear();
		this.eStepCounter = 0;
		this.maxQChangeInLastEpisode = Double.POSITIVE_INFINITY;
	}


	/**
	 * Writes the q-function table stored in this object to the specified file path.
	 * Uses a standard YAML approach, which means the HashableState and underlying Domain states
	 * must have JavaBean like properties; i.e., have a default constructor and getters and setters (or public data
	 * members) for all relevant fields.
	 * @param path the path to write the value function
	 */
	public void writeQTable(String path){
		Yaml yaml = new Yaml();
		try {
			yaml.dump(this.qFunction, new BufferedWriter(new FileWriter(path)));
		} catch(IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Loads the q-function table located on disk at the specified path. Expects the file to be a Yaml
	 * representation of a Java {@link Map} from {@link HashableState} to {@link QLearningStateNode}.
	 * @param path the path to the save value function table
	 */
	public void loadQTable(String path){
		Yaml yaml = new Yaml();
		try {
			this.qFunction = (Map<HashableState, QLearningStateNode>)yaml.load(new FileInputStream(path));
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void serializeQTable (String path) throws FileNotFoundException {
		SerializationUtil.saveQTable(qFunction, path);
	}

	public void deserializeQTable (String path) throws FileNotFoundException {
		this.qFunction = SerializationUtil.loadQTable(path);
	}

}
