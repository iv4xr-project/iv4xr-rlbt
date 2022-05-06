/**
 * 
 */
package eu.fbk.iv4xr.rlbt;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import burlap.behavior.singleagent.Episode;
import burlap.mdp.auxiliary.DomainGenerator;
import burlap.mdp.singleagent.SADomain;
import eu.fbk.iv4xr.rlbt.configuration.BurlapConfiguration;
import eu.fbk.iv4xr.rlbt.configuration.LRConfiguration;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsDomainGenerator;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsRLEnvironment;
import eu.fbk.iv4xr.rlbt.labrecruits.RlbtHashableStateFactory;
import eu.fbk.iv4xr.rlbt.labrecruits.distance.JaccardDistance;
import eu.fbk.iv4xr.rlbt.utils.SerializationUtil;

/**
 * @author kifetew
 *
 */

public class RlbtMain{

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
	static String lrConfigFile = currentDir+"/src/test/resources/configurations/buttons_doors_1.config";

	// root folder for writing output
	static String outputDir = currentDir + File.separator + "rlbt-files"+ File.separator + System.nanoTime();
	
	// Configurations
	static BurlapConfiguration burlapConfiguration = new BurlapConfiguration();
	static LRConfiguration lrConfiguration = new LRConfiguration();
	
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
			System.out.println("epsilon val ="+epsilonval+ "  decay = "+calculatedDecayVal);
		}
		if (rewardtp.equalsIgnoreCase("Sparse")) {
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
		labRecruitsRlEnvironment.startAgentEnvironment();
		for(int i = 0; i < numEpisodes; i++){			
			labRecruitsRlEnvironment.resetStateMemory();   // reset state buffer at the beginning of an episode
			long startTime = System.currentTimeMillis();
			episodes.add(agent.runLearningEpisode(labRecruitsRlEnvironment, maxActionsPerEpisode));
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
		
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
		return episodes;
	}
	
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

	/*execute training with pure random explore*/
	private static List<Episode> executeRandomTrainingOnLabRecruits() throws InterruptedException, FileNotFoundException {
		
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
		// check algorithm and execute corresponding method
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");
		if (alg.equalsIgnoreCase(BurlapAlgorithm.QLearning.toString())) {
			List<Episode> episodes = executeQLearningTrainingOnLabRecruits();
			return episodes;
		}else {
			throw new RuntimeException("Algorithm "+alg+" not supported");
		}
		
		
	}
	
	
	/**
	 * Execute a random training agent with parameters
	 * @param line
	 * @param options
	 * @throws InterruptedException 
	 * @throws FileNotFoundException 
	 */
	private List<Episode> executeRandom (CommandLine line, Options options) throws FileNotFoundException, InterruptedException {
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
	 */
	private Episode executeTesting (CommandLine line, Options options) throws FileNotFoundException {
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
	 */
	private Episode executeQLearningTestingOnLabRecruits () throws FileNotFoundException {
		/*initialize RL environment*/
		LabRecruitsRLEnvironment labRecruitsRlEnvironment = new LabRecruitsRLEnvironment(lrConfiguration, new JaccardDistance());
		
		System.out.println("Initializing domain. Opening level :"+lrConfiguration.getParameterValue("labrecruits.level_name"));
		DomainGenerator lrDomainGenerator = new LabRecruitsDomainGenerator();
		final SADomain domain = (SADomain) lrDomainGenerator.generateDomain();
		
		/*create Reinforcement Learning (Q-learning) agent*/
		QLearningRL agent = new QLearningRL(domain, 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.gamma"), 
				new RlbtHashableStateFactory(), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.qinit"), 
				(double)burlapConfiguration.getParameterValue("burlap.qlearning.lr"));
		long startTime = System.currentTimeMillis();
		
		String qtablePath = (String)burlapConfiguration.getParameterValue("burlap.qlearning.out_qtable");
		agent.deserializeQTable(qtablePath );
		agent.printFinalQtable(System.out);
		
		labRecruitsRlEnvironment.resetStateMemory();   // reset state buffer at the beginning of an episode
		Episode episode = agent.testQLearingAgent(labRecruitsRlEnvironment, 1900);
		
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Time - Testing : "+estimatedTime);

		
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
		
		String episodeBaseName = outputDir + "episode";
		SerializationUtil.serializeEpisode(episode, episodeBaseName );
		
		return episode;
	}



	
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
				.desc("burlap config file")
				.build();
		
		Option sutConfig   = Option.builder("sutConfig")
				.hasArg(true)
				.required(false)
				.type(String.class)
				.desc("SUT config file")
				.build();
		
		Option trainingMode = Option.builder("trainingMode")
				.required(false)
				.type(String.class)
				.desc("Execute training phase")
				.build();

		Option testingMode = Option.builder("testingMode")
				.required(false)
				.type(String.class)
				.desc("Execute testing phase")
				.build();

		Option randomMode = Option.builder("randomMode")
				.required(false)
				.type(String.class)
				.desc("Execute a random learning agent")
				.build();
		
		options.addOption(help);
		options.addOption(sutConfig);
		options.addOption(burlapConfig);
		options.addOption(trainingMode);
		options.addOption(testingMode);
		options.addOption(randomMode);
		
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
		updateParameters = lrConfiguration.updateParameters(lrConfigFile);
		return updateParameters;
		
		
	}
	

	/**
	 * @param args
	 * @throws InterruptedException 
	 * @throws FileNotFoundException 
	 */

	public static void main(String[] args) throws InterruptedException, FileNotFoundException {
	    try {
	    	RlbtMain main = new RlbtMain ();
			Options options = main.buildCommandLineOptions() ;
			
			// parse command line arguments, if any
			CommandLineParser parser = new DefaultParser();
			// parse the command line arguments
	        CommandLine line = parser.parse( options , args );
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
						main.executeRandom(line, options);
					}else {
						System.err.println("Must specify  -trainingMode or -testingMode");	
					}
					// save configurations in output directory for reproducibility
					saveConfigurations();
	        	}else {
	        		System.err.println("Quitting because unable to create output directory: " + outputDir);
	        	}
			}else {
				System.err.println( "Fail to load parameter files. Quitting!");
			}
	        
	        saveConfigurations();
	    }
	    catch( ParseException exp ) {
	        System.err.println( "Parsing command line failed.  Reason: " + exp.getMessage() );
	    }
	}


	private static void saveConfigurations() {
		lrConfiguration.writeToFile(outputDir + File.separator + "sut.config");
		burlapConfiguration.writeToFile(outputDir + File.separator + "burlap.config"); 
	}
} /*end*/
