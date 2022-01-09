/**
 * 
 */
package eu.fbk.iv4xr.rlbt;


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
	
	// Predefined configuration file
	static String currentDir = System.getProperty("user.dir");
	static String burlapConfigFile =  currentDir+"/src/test/resources/configurations/burlap_test.config";
	static String lrConfigFile = currentDir+"/src/test/resources/configurations/buttons_doors_1.config";

	// Configurations
	static BurlapConfiguration burlapConfiguration = new BurlapConfiguration();
	static LRConfiguration lrConfiguration = new LRConfiguration();
	
	private static void executeQLearningTrainingOnLabRecruits() throws InterruptedException, FileNotFoundException {
		
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
				numEpisodes);
		
		List<Episode> episodes = new ArrayList<Episode>(numEpisodes);	//list to store results from Q-learning episodes
		long startTime = System.currentTimeMillis();
		
		int maxActionsPerEpisode = (int)lrConfiguration.getParameterValue("labrecruits.max_actions_per_episode");
		/*------------Training - start running episodes------------------------*/
		labRecruitsRlEnvironment.startAgentEnvironment();
		for(int i = 0; i < numEpisodes; i++){
			labRecruitsRlEnvironment.resetStateMemory();   // reset state buffer at the beginning of an episode
			episodes.add(agent.runLearningEpisode(labRecruitsRlEnvironment, maxActionsPerEpisode));
			labRecruitsRlEnvironment.resetEnvironment();  /*reset environment*/
		}
		/*------------Save------------------------*/
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Time - Training : "+estimatedTime);
		agent.printFinalQtable(System.out);
		String qtableOutputFile = (String)burlapConfiguration.getParameterValue("burlap.qlearning.out_qtable");
		agent.serializeQTable(qtableOutputFile);
		agent.printFinalQtable(new PrintStream(qtableOutputFile + ".txt"));
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
	}
	
	
	/**
	 * Execute training accordingly with parameters
	 * @param line
	 * @param options
	 * @throws InterruptedException 
	 * @throws FileNotFoundException 
	 */
	private void executeTraining (CommandLine line, Options options) throws FileNotFoundException, InterruptedException {
		// check algorithm and execute corresponding method
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");
		if (alg.equalsIgnoreCase(BurlapAlgorithm.QLearning.toString())) {
			executeQLearningTrainingOnLabRecruits();
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
	private void executeTesting (CommandLine line, Options options) throws FileNotFoundException {
		// check algorithm and execute corresponding method
		String alg = (String)burlapConfiguration.getParameterValue("burlap.algorithm");
		if (alg.equalsIgnoreCase(BurlapAlgorithm.QLearning.toString())) {
			executeQLearningTestingOnLabRecruits();
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
	private void executeQLearningTestingOnLabRecruits () throws FileNotFoundException {
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
		
		agent.deserializeQTable((String)burlapConfiguration.getParameterValue("burlap.qlearning.out_qtable"));
		agent.printFinalQtable(System.out);
		
		labRecruitsRlEnvironment.resetStateMemory();   // reset state buffer at the beginning of an episode
		agent.testQLearingAgent(labRecruitsRlEnvironment, 1900);
		
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("Time - Testing : "+estimatedTime);

		
		labRecruitsRlEnvironment.stopAgentEnvironment();  /*stop RL agent environment*/
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

		options.addOption(help);
		options.addOption(sutConfig);
		options.addOption(burlapConfig);
		options.addOption(trainingMode);
		options.addOption(testingMode);
		
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

				if (line.hasOption("trainingMode")) {
					main.executeTraining(line, options);
				}else if (line.hasOption("testingMode")){
					main.executeTesting(line, options);
				}else {
					System.err.println("Must specify  -trainingMode or -testingMode");	
				}
			}else {
				System.err.println( "Fail to load parameter files. Quitting!");
			}
	    }
	    catch( ParseException exp ) {
	        System.err.println( "Parsing command line failed.  Reason: " + exp.getMessage() );
	    }
	    


	}
} /*end*/
