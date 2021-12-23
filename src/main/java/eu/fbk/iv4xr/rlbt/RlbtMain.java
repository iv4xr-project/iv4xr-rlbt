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

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kifetew
 *
 */

public class RlbtMain{
	
	//TODO expose these parameters as command line options
	static String level = "buttons_doors_1";	/*labrecruits level name*/
	static int maxUpdateCycles = 300;			/*max update cycles*/
	static int numOfEpisodes =3;				/*number of episodes for Q-learning training*/
	static boolean testTrainedAgent = false;
	
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
	 * @throws FileNotFoundException 
	 **************************************************************************************************/
	private static void labrecruitRLEx() throws InterruptedException, FileNotFoundException {
		/*initialize RL environment*/
		LabRecruitsRLEnvironment labRecruitsRlEnvironment = new LabRecruitsRLEnvironment(maxUpdateCycles, level, new JaccardDistance());
		
		DPrint.ul("Initializing domain. Opening level :"+level);
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();
		
		final double qinit = 0;   
		final double lr = 0.85;
				
		/*create Reinforcement Learning (Q-learning) agent*/
		QLearningRL agent = new QLearningRL(domain, 0.99, new RlbtHashableStateFactory(), qinit, lr);
		List<Episode> episodes = new ArrayList<Episode>(1000);	//list to store results from Q-learning episodes
		long startTime = System.currentTimeMillis();
		
		labRecruitsRlEnvironment.startAgentEnvironment();
		/*------------Training - start running episodes------------------------*/
		for(int i = 0; i < numOfEpisodes; i++){
			episodes.add(agent.runLearningEpisode(labRecruitsRlEnvironment));
			labRecruitsRlEnvironment.resetEnvironment();  /*reset environment*/
		}
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Time - Training : "+estimatedTime);
//		agent.writeQTable("qtable.yaml");
		agent.serializeQTable("qtable.ser");
		agent.printFinalQtable();

		/*------------Testing - using the optimized Q-table------------------------*/
		if (testTrainedAgent) {
			agent.testQLearingAgent(labRecruitsRlEnvironment, 1900);
		}
		
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
	}

	
	static void labrecruitRLTest(String qTablePath) throws FileNotFoundException {
		/*initialize RL environment*/
		LabRecruitsRLEnvironment labRecruitsRlEnvironment = new LabRecruitsRLEnvironment(maxUpdateCycles, level, new JaccardDistance());
		
		DPrint.ul("Initializing domain. Opening level :"+level);
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();
		
		final double qinit = 0;   
		final double lr = 0.85;
				
		/*create Reinforcement Learning (Q-learning) agent*/
		QLearningRL agent = new QLearningRL(domain, 0.99, new RlbtHashableStateFactory(), qinit, lr);
		long startTime = System.currentTimeMillis();
		
//		agent.loadQTable(qTablePath);
		agent.deserializeQTable(qTablePath);
		agent.printFinalQtable();
		agent.testQLearingAgent(labRecruitsRlEnvironment, 1900);
		
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Time - Testing : "+estimatedTime);

		
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
	}
	
	/**
	 * @param args
	 * @throws InterruptedException 
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws InterruptedException, FileNotFoundException {
		
		//labRecruitsExample ();
		labrecruitRLEx();

		
		String qTablePath = "qtable.ser";
		labrecruitRLTest(qTablePath);
		
	}  /*End of main()*/
} /*end*/
