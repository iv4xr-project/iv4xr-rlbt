/**
 * 
 */
package eu.fbk.iv4xr.rlbt;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;

import agents.LabRecruitsTestAgent;
import agents.TestSettings;
import burlap.behavior.singleagent.Episode;
import burlap.mdp.auxiliary.DomainGenerator;
import burlap.mdp.singleagent.SADomain;
import environments.LabRecruitsConfig;
import environments.LabRecruitsEnvironment;
import eu.fbk.iv4xr.rlbt.RlbtMain.BurlapAlgorithm;
import eu.fbk.iv4xr.rlbt.configuration.BurlapConfiguration;
import eu.fbk.iv4xr.rlbt.configuration.Configuration;
import eu.fbk.iv4xr.rlbt.configuration.LRConfiguration;
import eu.fbk.iv4xr.rlbt.configuration.LRMultiAgentConfiguration;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsDomainGenerator;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsRLEnvironment;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsRLMultiAgentEnvironment;
import eu.fbk.iv4xr.rlbt.labrecruits.RLActionToTestCaseEncoder;
import eu.fbk.iv4xr.rlbt.labrecruits.RlbtHashableStateFactory;
import eu.fbk.iv4xr.rlbt.labrecruits.distance.JaccardDistance;
import eu.fbk.iv4xr.rlbt.utils.SerializationUtil;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import game.LabRecruitsTestServer;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import world.BeliefState;

/**
 * @author kifetew
 *
 */

public class RlbtMultiAgentMain{

	public enum BurlapAlgorithm {
		QLearning
	}

	public enum SearchMode {
		GoalOriented, CoverageOriented
	}
	
	public enum RewardType{
		Sparse, CuriousityDriven
	}
	
	// Predefined configuration file
	static String currentDir = System.getProperty("user.dir");
	static String burlapConfigFile =  currentDir+"/src/test/resources/configurations/burlap_test.config";
	static String lrConfigFile = currentDir+"/src/test/resources/configurations/lrLevelSingleAgent.config"; 
	static String lrmultiagentConfigFile = currentDir+"/src/test/resources/configurations/lrLevelMultiAgent.config"; //multi-agent configuration 

	// root folder for writing output
	public static String outputDir = currentDir + File.separator + "rlbt-files"+ File.separator + "results" + File.separator + System.nanoTime();
	
	// Configurations
	static BurlapConfiguration burlapConfiguration = new BurlapConfiguration();
	static LRConfiguration lrConfiguration = new LRConfiguration();  // for single agent architecture
	static LRMultiAgentConfiguration lrmultiagentConfiguration = new LRMultiAgentConfiguration();  // for multi-agent arichitecture
	
/*=========================================================================================================================
 * 						Single Agent architecture/methods
 * =======================================================================================================================*/
	
	private static List<Episode> executeQLearningTrainingOnLabRecruits() throws InterruptedException, FileNotFoundException {
		LabRecruitsRLEnvironment labRecruitsRlEnvironment = new LabRecruitsRLEnvironment(lrConfiguration, new JaccardDistance());
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();
				
		int numEpisodes = (int)burlapConfiguration.getParameterValue("burlap.num_of_episodes");
		
		String rewardtp = (String)lrConfiguration.getParameterValue("labrecruits.rewardtype");
		if (rewardtp.equalsIgnoreCase("CuriousityDriven")) {
			double epsilonval = (double)burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval");
			double calculatedDecayVal = (double)(epsilonval/numEpisodes);
			calculatedDecayVal=calculatedDecayVal/2;
			burlapConfiguration.setParameterValue("burlap.qlearning.decayedepsilonstep", Double.toString(calculatedDecayVal));  // set calculated decayed value according to number of episodes
			System.out.println("Curiosity Driven Approach - epsilon val ="+epsilonval+ "  decay = "+calculatedDecayVal);
		}
		if (rewardtp.equalsIgnoreCase("Sparse")) {
			System.out.println("Sparse RL");
			burlapConfiguration.setParameterValue("burlap.qlearning.decayedepsilonstep", Double.toString(0));			
		}

		/*create Reinforcement Learning (Q-learning) agent*/
		QLearningRL agent = new QLearningRL(domain, 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.gamma"), 
				new RlbtHashableStateFactory(), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.qinit"), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.lr"),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval"),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep"),
				numEpisodes);
		
		List<Episode> episodes = new ArrayList<Episode>(numEpisodes);	//list to store results from Q-learning episodes
		List<Double> episodeCoverage =  new ArrayList<Double>(numEpisodes);
		List<Long> episodeTime =  new ArrayList<Long>(numEpisodes);

		int maxActionsPerEpisode = (int)lrConfiguration.getParameterValue("labrecruits.max_actions_per_episode");
		/*------------Training - start running episodes------------------------*/
		labRecruitsRlEnvironment.startAgentEnvironment();
		for(int i = 0; i < numEpisodes; i++){	
			System.out.println("Episode = "+(i+1)+" Starting");
			labRecruitsRlEnvironment.resetStateMemory();   // reset state buffer at the beginning of an episode
			long startTime = System.currentTimeMillis();
			episodes.add(agent.runLearningEpisode(labRecruitsRlEnvironment, maxActionsPerEpisode));
			long estimatedTime = System.currentTimeMillis() - startTime;
			System.out.println("Episode = "+(i+1)+" Finished, Time required  : "+estimatedTime);
			double episodecov = labRecruitsRlEnvironment.CalculateEpisodeCoverage();  /*calculate coverage after finishing an episode*/
			/*store time and coverage per episode*/
			episodeCoverage.add(episodecov);
			episodeTime.add(estimatedTime);
			
			labRecruitsRlEnvironment.GlobalCoveragePerEpisode();
			labRecruitsRlEnvironment.resetEnvironment();  /*reset environment*/
		}
		
		labRecruitsRlEnvironment.CalculateGlobalCoverageAfterTraining();
		/*------------Save------------------------*/
		agent.printFinalQtable(System.out);
		String qtableOutputFile = outputDir + File.separator + "qtable.ser"; // (String)burlapConfiguration.getParameterValue("burlap.qlearning.out_qtable");
		agent.serializeQTable(qtableOutputFile);
		agent.printFinalQtable(new PrintStream(qtableOutputFile + ".txt"));
		String episodesummaryfile = outputDir + File.separator + "episodeSummary.txt"; 
		SaveEpisodeSummary(episodes, episodesummaryfile, episodeCoverage, episodeTime);  // store episode summary (number of actions and reward per episode)
				
		String episodesBaseName = outputDir + File.separator + "episode";
		SerializationUtil.serializeEpisodes(episodes, episodesBaseName );
		
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
		return episodes;
	}
	
	/**
	 * Execute training accordingly with parameters
	 * @param line
	 * @param options
	 * @throws InterruptedException 
	 * @throws FileNotFoundException 
	 */
	private List<Episode> executeTraining (CommandLine line, Options options) throws FileNotFoundException, InterruptedException {
		System.out.println("--------------------------Single Reinforcement Learning Agent architecture---------------------");
		// check algorithm and execute corresponding method
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");
		if (alg.equalsIgnoreCase(BurlapAlgorithm.QLearning.toString())) {
			List<Episode> episodes = executeQLearningTrainingOnLabRecruits();
			return episodes;
		}else {
			throw new RuntimeException("Algorithm "+alg+" not supported");
		}		
	}
	
	/*execute training with pure random explore*/
	private static List<Episode> executeRandomTrainingOnLabRecruits() throws InterruptedException, FileNotFoundException {
		System.out.println("------------RANDOM ALGORITHM-----------------------------------------");
		LabRecruitsRLEnvironment labRecruitsRlEnvironment = new LabRecruitsRLEnvironment(lrConfiguration, new JaccardDistance());
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();
				
		int numEpisodes = (int)burlapConfiguration.getParameterValue("burlap.num_of_episodes");

		/*create Reinforcement Learning (Q-learning) agent*/
		QLearningRL agent = new QLearningRL(domain, 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.gamma"), 
				new RlbtHashableStateFactory(), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.qinit"), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.lr"),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval"),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep"),
				numEpisodes);
		
		List<Episode> episodes = new ArrayList<Episode>(numEpisodes);	//list to store results from Q-learning episodes
		List<Double> episodeCoverage =  new ArrayList<Double>(numEpisodes);
		List<Long> episodeTime =  new ArrayList<Long>(numEpisodes);

		//long startTime = System.currentTimeMillis();
		
		int maxActionsPerEpisode = (int)lrConfiguration.getParameterValue("labrecruits.max_actions_per_episode");
		/*------------Training - start running episodes------------------------*/
		labRecruitsRlEnvironment.startAgentEnvironment();
		for(int i = 0; i < numEpisodes; i++){			
			labRecruitsRlEnvironment.resetStateMemory();   // reset state buffer at the beginning of an episode
			long startTime = System.currentTimeMillis();
			episodes.add(agent.runLearningEpisodeRandom(labRecruitsRlEnvironment, maxActionsPerEpisode));
			long estimatedTime = System.currentTimeMillis() - startTime;
			System.out.println("Time for this episode  : "+estimatedTime);
			
			double episodecov = labRecruitsRlEnvironment.CalculateEpisodeCoverage();  /*calculate coverage after finishing an episode*/
			/*store time and coverage per episode*/
			episodeCoverage.add(episodecov);
			episodeTime.add(estimatedTime);
			
			labRecruitsRlEnvironment.GlobalCoveragePerEpisode();			
			labRecruitsRlEnvironment.resetEnvironment();  /*reset environment*/
		}
		
		labRecruitsRlEnvironment.CalculateGlobalCoverageAfterTraining();
		/*------------Save------------------------*/
		agent.printFinalQtable(System.out);
		String qtableOutputFile = outputDir + File.separator + "qtable.ser"; // (String)burlapConfiguration.getParameterValue("burlap.qlearning.out_qtable");
		agent.serializeQTable(qtableOutputFile);
		agent.printFinalQtable(new PrintStream(qtableOutputFile + ".txt"));
		String episodesummaryfile = outputDir + File.separator + "episodeSummary.txt"; 
		SaveEpisodeSummary(episodes, episodesummaryfile, episodeCoverage, episodeTime);  // store episode summary (number of actions and reward per episode)
		
		String episodesBaseName = outputDir + File.separator + "episode";
		SerializationUtil.serializeEpisodes(episodes, episodesBaseName );
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
		return episodes;
	}
	
	/**
	 * Execute a random training agent with parameters
	 * @param line
	 * @param options
	 * @throws InterruptedException 
	 * @throws FileNotFoundException 
	 */
	private List<Episode> executeRandom (CommandLine line, Options options) throws FileNotFoundException, InterruptedException {
		System.out.println("Single Agent architecture - Random approach");
		// check algorithm and execute corresponding method
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");
		if (alg.equalsIgnoreCase(BurlapAlgorithm.QLearning.toString())) {
			// to enable random exploration, set epsilon to 1 (no exploitation)
			burlapConfiguration.setParameterValue("burlap.qlearning.epsilonval", "1.0");
			burlapConfiguration.setParameterValue("burlap.qlearning.decayedepsilonstep", "1.0");
			List<Episode> episodes = executeRandomTrainingOnLabRecruits();
			return episodes;
		}else {
			throw new RuntimeException("Algorithm "+alg+" not supported");
		}		
	}
	
	/**
	 * Execute training accordingly with parameters
	 * @param line
	 * @param options
	 * @throws FileNotFoundException 
	 * @throws InterruptedException 
	 */
	private Episode executeTesting (CommandLine line, Options options) throws FileNotFoundException, InterruptedException {
		// check algorithm and execute corresponding method
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");
		if (alg.equalsIgnoreCase(BurlapAlgorithm.QLearning.toString())) {
			Episode episode = executeQLearningTestingOnLabRecruits();
			return episode;
		}else {
			throw new RuntimeException("Algorithm "+alg+" not supported");
		}			
	}

	
	/**
	 * Execute testing accordingly with parameters
	 * @param line
	 * @param options
	 * @throws FileNotFoundException 
	 * @throws InterruptedException 
	 */
	private Episode executeQLearningTestingOnLabRecruits () throws FileNotFoundException, InterruptedException {
		/*initialize RL environment*/
		LabRecruitsRLEnvironment labRecruitsRlEnvironment = new LabRecruitsRLEnvironment(lrConfiguration, new JaccardDistance());
		
		System.out.println("Initializing domain. Opening level :"+lrConfiguration.getParameterValue("labrecruits.level_name"));
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();

		int maxActionsPerEpisode = (int)lrConfiguration.getParameterValue("labrecruits.max_actions_per_episode");
		/*create Reinforcement Learning (Q-learning) agent*/
		QLearningRL agent = new QLearningRL(domain, 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.gamma"), 
				new RlbtHashableStateFactory(), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.qinit"), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.lr"));
		long startTime = System.currentTimeMillis();
		
		String qtablePath = outputDir + File.separator + "cqtable.ser";//(String)burlapConfiguration.getParameterValue("burlap.qlearning.out_qtable");
		agent.deserializeQTable(qtablePath );
		agent.printFinalQtable(System.out);
		
		System.out.println("Start testing agent");
		int numTestingEpisodes=10;
		for(int i = 0; i < numTestingEpisodes; i++){			
			labRecruitsRlEnvironment.startAgentEnvironment();
			
			Episode episode = agent.testQLearingAgent(labRecruitsRlEnvironment, maxActionsPerEpisode);
			System.out.println("Finished Episode = "+ (i));
			labRecruitsRlEnvironment.CalculateEpisodeCoverage();
			labRecruitsRlEnvironment.CalculateGlobalCoverageAfterTraining();
			labRecruitsRlEnvironment.CalculateConnectionCoverage();
			labRecruitsRlEnvironment.resetStateMemory();   // reset state buffer at the beginning of an episode
			labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
		}
		Episode episode1 = agent.testQLearingAgent(labRecruitsRlEnvironment, maxActionsPerEpisode);
		return episode1;
	}

/*=========================================================================================================================
 * 						MultiAgent architecture (training methods)
 * =======================================================================================================================*/
	private static List<Episode> executeMultiAgentTrainingOnLabRecruits() throws InterruptedException, FileNotFoundException {
		
		LabRecruitsRLMultiAgentEnvironment labRecruitsRlMultiAgentEnv = new LabRecruitsRLMultiAgentEnvironment(lrmultiagentConfiguration, new JaccardDistance());
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();
				
		int numEpisodes = (int)burlapConfiguration.getParameterValue("burlap.num_of_episodes");
		
		String rewardtp = (String)lrConfiguration.getParameterValue("labrecruits.rewardtype");
		if (rewardtp.equalsIgnoreCase("CuriousityDriven")) {
			double epsilonval = (double)burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval");
			double calculatedDecayVal = (double)(epsilonval/numEpisodes);
			calculatedDecayVal=calculatedDecayVal/2;
			burlapConfiguration.setParameterValue("burlap.qlearning.decayedepsilonstep", Double.toString(calculatedDecayVal));  // set calculated decayed value according to number of episodes
			System.out.println("Curiosity Driven approach - epsilon val ="+epsilonval+ "  decay = "+calculatedDecayVal);
		}
		if (rewardtp.equalsIgnoreCase("Sparse")) {
			System.out.println("Sparse RL");
			burlapConfiguration.setParameterValue("burlap.qlearning.decayedepsilonstep", Double.toString(0));			
		}


		/*create Reinforcement Learning (Q-learning) agent*/
		QLearningRL agent = new QLearningRL(domain, 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.gamma"), 
				new RlbtHashableStateFactory(), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.qinit"), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.lr"),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.epsilonval"),
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.decayedepsilonstep"),
				numEpisodes);
		
		List<Episode> episodes = new ArrayList<Episode>(numEpisodes);	//list to store results from Q-learning episodes
		List<Double> episodeCoverage =  new ArrayList<Double>(numEpisodes);
		List<Long> episodeTime =  new ArrayList<Long>(numEpisodes);

		//long startTime = System.currentTimeMillis();
		
		int maxActionsPerEpisode = (int)lrConfiguration.getParameterValue("labrecruits.max_actions_per_episode");
		/*------------Training - start running episodes------------------------*/
		labRecruitsRlMultiAgentEnv.startAgentEnvironment();
		for(int i = 0; i < numEpisodes; i++){	
			System.out.println("Starting Episode = "+(i+1));
			labRecruitsRlMultiAgentEnv.resetStateMemory();   // reset state buffer at the beginning of an episode
			long startTime = System.currentTimeMillis();
			episodes.add(agent.runLearningEpisode(labRecruitsRlMultiAgentEnv, maxActionsPerEpisode));
			long estimatedTime = System.currentTimeMillis() - startTime;
			System.out.println("Finished Episode = "+(i+1)+". Time required  : "+estimatedTime);
			
			double episodecov = labRecruitsRlMultiAgentEnv.CalculateEpisodeCoverage();  //calculate coverage after finishing an episode
			//store time and coverage per episode
			episodeCoverage.add(episodecov);
			episodeTime.add(estimatedTime);
			
			System.out.println("End of episode - Print goal entities explored by active agent");
			labRecruitsRlMultiAgentEnv.printGoalEntities();
			System.out.println("Coverage stat till episode "+(i+1));
			labRecruitsRlMultiAgentEnv.GlobalCoveragePerEpisode();
			labRecruitsRlMultiAgentEnv.resetEnvironment();  //reset environment
		}

		
		labRecruitsRlMultiAgentEnv.CalculateGlobalCoverageAfterTraining();
		//------------Save------------------------
		//long estimatedTime = System.currentTimeMillis() - startTime;
		//System.out.println("Time - Training : "+estimatedTime);
		agent.printFinalQtable(System.out);
		String qtableOutputFile = outputDir + File.separator + "qtable.ser"; // (String)burlapConfiguration.getParameterValue("burlap.qlearning.out_qtable");
		agent.serializeQTable(qtableOutputFile);
		agent.printFinalQtable(new PrintStream(qtableOutputFile + ".txt"));
		String episodesummaryfile = outputDir + File.separator + "episodeSummary.txt"; 
		SaveEpisodeSummary(episodes, episodesummaryfile, episodeCoverage, episodeTime);  // store episode summary (number of actions and reward per episode)
				
		String episodesBaseName = outputDir + File.separator + "episode";
		SerializationUtil.serializeEpisodes(episodes, episodesBaseName );
		
		labRecruitsRlMultiAgentEnv.stopAgentEnvironment();  /*stop RL agent environment*/
		return episodes;
	}
	
	/**
	 * Execute training accordingly with parameters
	 * @param line
	 * @param options
	 * @throws InterruptedException 
	 * @throws FileNotFoundException 
	 */
	private List<Episode> executeMultiAgentTraining (CommandLine line, Options options) throws FileNotFoundException, InterruptedException {
		System.out.println("=========================================================================================");
		System.out.println("----------------MultiAgent Reinforcement Learning Architecture----------------");
		// check algorithm and execute corresponding method
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");
		if (alg.equalsIgnoreCase(BurlapAlgorithm.QLearning.toString())) {
			List<Episode> episodes = executeMultiAgentTrainingOnLabRecruits();
			return episodes;
		}else {
			throw new RuntimeException("Algorithm "+alg+" not supported");
		}
		
		
	}

	
	
	private void executeTests (CommandLine line, Options options) throws InterruptedException, IOException {
		System.out.println("=========================================================================================");
		System.out.println("----------------Executing previously serialized tests----------------");
		
		// get user parameters
		String actionsFile = line.getOptionValue("actionsFile");
		
		String agentNameProp = "labrecruits.agent_id";
		Configuration sutConfig;
		boolean success = true;
		if (line.hasOption("singleAgentTestExecutionMode")) {
			sutConfig = lrConfiguration;
		}else {
			// try multi agent
			sutConfig = lrmultiagentConfiguration;
			agentNameProp = "labrecruits.agentactive_id";
		}
		
		if (!success) {
			String msg = "Please provide a valid SUT configuration file that was used during the training phase. "
					+ "It should be in the rlbt-files folder generated after the training phase.";
			throw new RuntimeException(msg);
		}
		
		// settings we need to start test execution
		String labRecruitsLevel = (String) sutConfig.getParameterValue("labrecruits.level_name");
		String labRecruitsLevelFolder =  (String) sutConfig.getParameterValue("labrecruits.level_folder");
		String agentName =  (String) sutConfig.getParameterValue(agentNameProp);
		int maxCyclesPerAction =  (Integer) sutConfig.getParameterValue("labrecruits.max_ticks_per_action");
		
		// start the LR test server
		TestSettings.USE_GRAPHICS = true ;
	    String labRecruitesExeRootDir = (String) sutConfig.getParameterValue("labrecruits.execution_folder"); // System.getProperty("user.dir") ;
	    LabRecruitsTestServer labRecruitsTestServer = TestSettings.start_LabRecruitsTestServer(labRecruitesExeRootDir) ;
	    labRecruitsTestServer.waitForGameToLoad();
	   
	    // set up the environment
		LabRecruitsConfig gameConfig = new LabRecruitsConfig(labRecruitsLevel,labRecruitsLevelFolder);
		float viewDistance = 20;
		gameConfig.replaceAgentViewDistance(viewDistance);
		gameConfig.agent_speed = 0.13f;
		gameConfig.host = "localhost"; // "192.168.29.120";
		LabRecruitsEnvironment labRecruitsAgentEnvironment = new LabRecruitsEnvironment(gameConfig);
		labRecruitsAgentEnvironment.startSimulation();
	    
	    // set up the agent
	    LabRecruitsTestAgent testAgent = new LabRecruitsTestAgent(agentName) // matches the ID in the CSV file
				. attachState(new BeliefState())
				. attachEnvironment(labRecruitsAgentEnvironment);

	    // attach data collector (verdicts)
		var dataCollector = new TestDataCollector();
		testAgent.setTestDataCollector(dataCollector);
		
		// deserialize action logs to GoalStructures
		String actions = FileUtils.readFileToString(new File(actionsFile ), Charset.defaultCharset());
		List<GoalStructure> goals = RLActionToTestCaseEncoder.serializeActionsToGoals(testAgent, actions);
		
		// execute each GoalStructure through the agent
		for (GoalStructure goal : goals) {
			LabRecruitsRLMultiAgentEnvironment.doAction(goal, maxCyclesPerAction, testAgent);
//			goal.printGoalStructureStatus();
		}
		
		// collect verdicts
//		dataCollector.save(agentName, actionsFile + ".coverage");
		
		
		System.out.println("Failed: " + dataCollector.getNumberOfFailVerdictsSeen());
		System.out.println("Passed: " + dataCollector.getNumberOfPassVerdictsSeen());
		System.out.println("Inconclusive: " + dataCollector.getNumberOfUndecidedVerdictsSeen());
		
		//shut down env and test server
		labRecruitsAgentEnvironment.close();
		if(labRecruitsTestServer != null) { 
			labRecruitsTestServer.close();
		}
	}
	
	
	/**
	 * Save episode summary
	 */
	private static void SaveEpisodeSummary(List<Episode> episodes, String outfile, List<Double> episodeCoverage, List<Long> episodeTime) throws FileNotFoundException {
		PrintStream ps =  new PrintStream(outfile);
		/*print number of actions taken per episode*/
		for (int i=0;i<episodes.size();i++) {
			ps.print(episodes.get(i).actionSequence.size()+",");
		}
		ps.println();
		/*print reward summary per episode*/
		for (int i=0;i<episodes.size();i++) {
			double sumreward=0;
			for (int j=0;j<episodes.get(i).rewardSequence.size();j++) {
				sumreward =sumreward +episodes.get(i).rewardSequence.get(j);
			}
			ps.print(sumreward+", ");
		}
		ps.println();
		/*print coverage per episode*/
		for (int i=0;i<episodes.size();i++) {
			ps.print(episodeCoverage.get(i)+",");
		}
		ps.println();
		
		for (int i=0;i<episodes.size();i++) {
			ps.print(episodeTime.get(i)+",");
		}
		ps.println();
		ps.close();
	} //end of function



	
	/**
	 * Perform post analysis on learning traces 
	 * @param line
	 * @param options
	 * @throws InterruptedException 
	 * @throws FileNotFoundException 
	 */
	private List<Episode> postAnalysisLearningTraces (CommandLine line, Options options) throws FileNotFoundException, InterruptedException {
		// check algorithm and execute corresponding method
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");
		String labRecruitsLevel = (String) lrConfiguration.getParameterValue("labrecruits.level_name");
		if (alg.equalsIgnoreCase(BurlapAlgorithm.QLearning.toString())) {
			List<Episode> episodes = executeQLearningTrainingOnLabRecruits();
			return episodes;
		}else {
			throw new RuntimeException("Algorithm "+alg+" not supported");
		}		
	}




	
	/**
	 * define command line options
	 * @return
	 */
	private Options buildCommandLineOptions() {
		Options options = new Options();
		
		Option help = new Option( "help", "print usage" );
		
		Option burlapConfig   = Option.builder("burlapConfig")
				.hasArg(true)
				.required(false)
				.type(String.class)
				.desc("burlap configuration file")
				.build();
		
		Option sutConfig   = Option.builder("sutConfig")
				.hasArg(true)
				.required(false)
				.type(String.class)
				.desc("SUT configuration file")
				.build();
		
		Option trainingMode = Option.builder("trainingMode")
				.required(false)
				.type(String.class)
				.desc("Execute single-agnet training phase")
				.build();

		Option testingMode = Option.builder("testingMode")
				.required(false)
				.type(String.class)
				.desc("Execute single-agent testing phase")
				.build();

		Option randomMode = Option.builder("randomMode")
				.required(false)
				.type(String.class)
				.desc("Execute a random single learning agent")
				.build();
		
		Option actionsFile  = Option.builder("actionsFile")
				.hasArg(true)
				.required(false)
				.type(String.class)
				.desc("CSV file containing the actions to be executed as tests")
				.build();
		
		Option multiagentTrainingMode = Option.builder("multiagentTrainingMode")
				.required(false)
				.type(String.class)
				.desc("Execute multi-agent training phase")
				.build();
		
		Option singleAgentTestExecutionMode = Option.builder("singleAgentTestExecutionMode")
				.hasArg(false)
				.required(false)
				.type(String.class)
				.desc("Execute tests serialized from a previous single-agent run. Need to provide 'actionsFile' and 'sutConfig' parameters!")
				.build();
		
		Option multiAgentTestExecutionMode = Option.builder("multiAgentTestExecutionMode")
				.hasArg(false)
				.required(false)
				.type(String.class)
				.desc("Execute tests serialized from a previous multi-agent run. Need to provide 'actionsFile' and 'sutConfig' parameters!")
				.build();
		
		options.addOption(help);
		options.addOption(sutConfig);
		options.addOption(burlapConfig);
		options.addOption(trainingMode);
		options.addOption(testingMode);
		options.addOption(randomMode);
		options.addOption(multiagentTrainingMode);
		options.addOption(singleAgentTestExecutionMode);
		options.addOption(multiAgentTestExecutionMode);
		options.addOption(actionsFile);
		
		return options;
	}
	
	/**
	 * Load configuration files and set parameters
	 * @param line
	 * @param options
	 */
	private boolean loadAndSetParameter(CommandLine line, Options options) {
		
		 // has the burlapconfig file argument been passed?
        if( line.hasOption( "burlapConfig" ) ) {
        	burlapConfigFile = line.getOptionValue( "burlapConfig" );
        }
        
        // has the SUT config file argument been passed?
        if( line.hasOption( "sutConfig" ) ) {
        	if( line.hasOption( "multiagentTrainingMode" ) 
        			|| line.hasOption("multiAgentTestExecutionMode"))
        		lrmultiagentConfigFile = line.getOptionValue( "sutConfig" );
        	else
        		lrConfigFile = line.getOptionValue( "sutConfig" );
        }
        
        if (line.hasOption("help")) {
        	String header = "If not arguments are provided, by default: burlapConfig=burlap.config and "
        			+ "sutConfig=buttons_doors_1.config will be used";
        	String footer = "";
        	HelpFormatter formatter = new HelpFormatter();
        	formatter.printHelp("rl", header, options, footer, true);
        }
        
        // update parameters
        boolean updateParameters = false;
		updateParameters = burlapConfiguration.updateParameters(burlapConfigFile);	
		if( line.hasOption( "multiagentTrainingMode" ) 
				|| line.hasOption("multiAgentTestExecutionMode"))
			updateParameters = lrmultiagentConfiguration.updateParameters(lrmultiagentConfigFile);
		else
			updateParameters = lrConfiguration.updateParameters(lrConfigFile);
		return updateParameters;
		
		
	}
	

	/**
	 * @param args
	 * @throws InterruptedException 
	 * @throws IOException 
	 */

	public static void main(String[] args) throws InterruptedException, IOException {
		RlbtMultiAgentMain main = new RlbtMultiAgentMain ();
		Options options = main.buildCommandLineOptions() ;
	    
		CommandLine line = null;
		try {
			// parse command line arguments, if any
			CommandLineParser parser = new DefaultParser();
			// parse the command line arguments
	        line = parser.parse( options , args );
		}catch( ParseException exp ) {
	        System.err.println( "Parsing command line failed.  Reason: " + exp.getMessage() );
	    }
		
        if (line == null || line.hasOption("help") || line.getOptions().length == 0) {
			printCommandLineHelp(options);
		}else {
	        boolean loadAndSetParameter = main.loadAndSetParameter(line,options);
	        // choose testing or training
	        if (loadAndSetParameter) {
	        	// create output folder
	        	File reportDir = new File (outputDir);
	        	if (reportDir.exists() || reportDir.mkdirs()) {
					if (line.hasOption("trainingMode")) {
						main.executeTraining(line, options);
					}else if (line.hasOption("testingMode")){
						main.executeTesting(line, options);
					}else if (line.hasOption("randomMode")) {
						main.executeTraining(line, options);
					}else if (line.hasOption("multiagentTrainingMode")) {
						main.executeMultiAgentTraining(line, options);
					}else if (line.hasOption("singleAgentTestExecutionMode")
							|| line.hasOption("multiAgentTestExecutionMode")) {
						main.executeTests (line, options);
					}else {
						printCommandLineHelp(options);
						System.exit(1);
						//System.err.println("Must specify  -trainingMode or -testingMode");	
					}
					// save configurations in output directory for reproducibility
					saveConfigurations();
	        	}else {
	        		System.err.println("Quitting because unable to create output directory: " + outputDir);
	        		System.exit(1);
	        	}
			}else {
				System.err.println( "Fail to load parameter files. Quitting!");
				System.exit(1);
			}
		}
	        
//	        saveConfigurations();
	    
	}

	/**
	 * display command line help to the user
	 * @param options
	 */
	private static void printCommandLineHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		// Do not sort				
		formatter.setOptionComparator(null);
		// Header and footer strings
		String header = "Reinforcement Learning Based Testing\n\n";
		String footer = "\nPlease report issues at https://github.com/iv4xr-project/iv4xr-rlbt/issues";
		 
		formatter.printHelp("RLbT",header, options, footer , false);
	}


	private static void saveConfigurations() {
		lrmultiagentConfiguration.writeToFile(outputDir + File.separator + "sutmulti.config");
		lrConfiguration.writeToFile(outputDir + File.separator + "sut.config");
		burlapConfiguration.writeToFile(outputDir + File.separator + "burlap.config"); 
	}
} /*end*/
