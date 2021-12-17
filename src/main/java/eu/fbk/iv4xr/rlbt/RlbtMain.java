/**
 * 
 */
package eu.fbk.iv4xr.rlbt;


import burlap.behavior.singleagent.auxiliary.performance.LearningAlgorithmExperimenter;
import burlap.behavior.singleagent.auxiliary.performance.PerformanceMetric;
import burlap.behavior.singleagent.auxiliary.performance.TrialMode;
import burlap.behavior.singleagent.learning.LearningAgent;
import burlap.behavior.singleagent.learning.LearningAgentFactory;
import burlap.behavior.singleagent.learning.tdmethods.SarsaLam;
import burlap.debugtools.DPrint;
import burlap.mdp.auxiliary.DomainGenerator;
import burlap.mdp.singleagent.SADomain;import burlap.statehashing.simple.SimpleHashableStateFactory;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsDomainGenerator;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsRLEnvironment;
import eu.fbk.iv4xr.rlbt.labrecruits.RlbtHashableStateFactory;
import eu.fbk.iv4xr.rlbt.labrecruits.distance.JaccardDistance;
import burlap.behavior.singleagent.Episode;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kifetew
 *
 */

public class RlbtMain{
	
	static String level = "buttons_doors_1";	/*labrecruits level name*/
	static int maxUpdateCycles = 400;			/*max update cycles*/
	static int numOfEpisodes =1;				/*number of episodes for Q-learning training*/
	
	
	private static void labRecruitsExample() throws InterruptedException {
		LabRecruitsRLEnvironment labRecruitsRlEnvironment = new LabRecruitsRLEnvironment(maxUpdateCycles, level, new JaccardDistance());
		labRecruitsRlEnvironment.startAgentEnvironment();
				
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();

		final double qinit = 0;
		final double lr = 0.85;
		LearningAgentFactory qLearningFactory = new LearningAgentFactory() {

			public String getAgentName() {
				return "Q-Learning";
			}


			public LearningAgent generateAgent() {
				return new QLearningRL(domain, 0.99, new SimpleHashableStateFactory(), qinit, lr);
			}
		};
		
		LearningAgentFactory sarsaLamLearningFactory = new LearningAgentFactory() {

			public String getAgentName() {
				return "SARSA";
			}


			public LearningAgent generateAgent() {
				return new SarsaLam(domain, 0.99, new SimpleHashableStateFactory(), 0.0, 0.1, .1);
			}
		};
		LearningAgentFactory[] learningAgentFactories = {sarsaLamLearningFactory, qLearningFactory};
		LearningAlgorithmExperimenter exp = new LearningAlgorithmExperimenter(labRecruitsRlEnvironment, 10, 10, false, learningAgentFactories  );
		exp.setUpPlottingConfiguration(500, 300, 2, 800, TrialMode.MOST_RECENT_AND_AVERAGE, PerformanceMetric.CUMULATIVE_REWARD_PER_EPISODE);

		exp.startExperiment();
		String expData = "expdata.csv";
		exp.writeStepAndEpisodeDataToCSV(expData);
		
		labRecruitsRlEnvironment.stopAgentEnvironment();
	}

	/**************************************************************************************************
	 * Reinforcement Learning - Q-Learning testing- by Raihana
	 **************************************************************************************************/
	private static void labrecruitRLEx() throws InterruptedException {
		/*initialize RL environment*/
		LabRecruitsRLEnvironment labRecruitsRlEnvironment = new LabRecruitsRLEnvironment(maxUpdateCycles, level, new JaccardDistance());
//		labRecruitsRlEnvironment.startAgentEnvironment();
		
		DPrint.ul("Initializing domain. Opening level :"+level);
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();
		
		final double qinit = 0;   
		final double lr = 0.85;
				
		/*create Reinforcement Learning (Q-learning) agent*/
		QLearningRL agent = new QLearningRL(domain, 0.99, new RlbtHashableStateFactory(), qinit, lr);
		//QLearningRLAlgo agent = new QLearningRLAlgo(domain, 0.99, new SimpleHashableStateFactory(), qinit, lr);
		List<Episode> episodes = new ArrayList<Episode>(1000);	//list to store results from Q-learning episodes
		long startTime = System.currentTimeMillis();
		
		/*------------Training - start running episodes------------------------*/
		for(int i = 0; i < numOfEpisodes; i++){
			labRecruitsRlEnvironment.resetEnvironment();  /*reset environment*/
			episodes.add(agent.runLearningEpisode(labRecruitsRlEnvironment));
			}
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Time - Training : "+estimatedTime);
		//agent.writeQTable(outpath);
		agent.PrintFinalQtable();
		/*------------Testing - using the optimized Q-table------------------------*/
		agent.testQLearingAgent(labRecruitsRlEnvironment, 1900);
//		labRecruitsRlEnvironment.EvaluateQLearningAgent(agent.qFunction);
		
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
	}

	/**
	 * @param args
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException {
		//labRecruitsExample ();
		labrecruitRLEx();

	}  /*End of main()*/
} /*end*/
