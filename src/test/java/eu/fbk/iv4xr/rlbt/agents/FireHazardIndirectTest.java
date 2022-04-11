package eu.fbk.iv4xr.rlbt.agents;

import static agents.TestSettings.USE_INSTRUMENT;
import static nl.uu.cs.aplib.AplibEDSL.SEQ;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Scanner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import agents.LabRecruitsTestAgent;
import agents.TestSettings;
import agents.tactics.GoalLib;
import environments.LabRecruitsConfig;
import environments.LabRecruitsEnvironment;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import game.LabRecruitsTestServer;
import logger.JsonLoggerInstrument;
import nl.uu.cs.aplib.mainConcepts.Environment;
import world.BeliefState;

class FireHazardIndirectTest {
/**
 * A simple test to demonstrate using iv4xr agents to test the Lab Recruits game.
 * The testing task is to verify that the path to the fire extinguisher behind door1 is reachable from
 * the player's initial position by going upstairs and hence avoiding much damage from the fire.
 */

    private static LabRecruitsTestServer labRecruitsTestServer;

    @BeforeAll
    static void start() {
    	// TestSettings.USE_SERVER_FOR_TEST = false ;
    	// Uncomment this to make the game's graphic visible:
    	// TestSettings.USE_GRAPHICS = true ;
    	String labRecruitesExeRootDir = System.getProperty("user.dir") ;
    	labRecruitsTestServer = TestSettings.start_LabRecruitsTestServer(labRecruitesExeRootDir) ;
    }

    @AfterAll
    static void close() { 
    	if(labRecruitsTestServer!=null) labRecruitsTestServer.close(); 
    }

    void instrument(Environment env) {
    	env.registerInstrumenter(new JsonLoggerInstrument()).turnOnDebugInstrumentation();
    }

    /**
     * A test to verify that the fire-free path to the extinguisher is reachable.
     */
    @Test
    public void safePathTest() throws InterruptedException {


        // Create an environment
    	var config = new LabRecruitsConfig("HZRDIndirect") ;
    	config.light_intensity = 0.3f ;
    	var environment = new LabRecruitsEnvironment(config);
        if(USE_INSTRUMENT) {
        	instrument(environment) ;
        }

        try {
//        	if(TestSettings.USE_GRAPHICS) {
//        		System.out.println("You can drag then game window elsewhere for beter viewing. Then hit RETURN to continue.") ;
//        		new Scanner(System.in) . nextLine() ;
//        	}

	        // create a test agent
	        var testAgent = new LabRecruitsTestAgent("agent1") // matches the ID in the CSV file
        		    . attachState(new BeliefState())
        		    . attachEnvironment(environment);

	        // define the testing-task:
	        var testingTask = SEQ(
	            GoalLib.entityInteracted("b4.1"),
                GoalLib.entityStateRefreshed("d4"),
	        	GoalLib.entityInvariantChecked(testAgent,
	            		"d4",
	            		"door4 should be open",
	            		(WorldEntity e) -> e.getBooleanProperty("isOpen")),

	        	GoalLib.entityInteracted("b7.1"),
	        	GoalLib.entityStateRefreshed("d7"),
	        	GoalLib.entityInvariantChecked(testAgent,
	            		"d7",
	            		"door7 should be open",
	            		(WorldEntity e) -> e.getBooleanProperty("isOpen")),
	        	GoalLib.entityInteracted("b8.2"),
	        	GoalLib.entityStateRefreshed("d8"),
	        	GoalLib.entityInvariantChecked(testAgent,
	            		"d8",
	            		"door8 should be open",
	            		(WorldEntity e) -> e.getBooleanProperty("isOpen")),
	        	
	        	GoalLib.entityInteracted("b5.1"),
	        	GoalLib.entityStateRefreshed("d5"),
	        	GoalLib.entityInvariantChecked(testAgent,
	            		"d5",
	            		"door5 should be open",
	            		(WorldEntity e) -> e.getBooleanProperty("isOpen")),
	        	
	        	GoalLib.entityInteracted("b1.1"),
	        	GoalLib.entityStateRefreshed("d1"),
	        	GoalLib.entityInvariantChecked(testAgent,
	            		"d1",
	            		"door1 should be open",
	            		(WorldEntity e) -> e.getBooleanProperty("isOpen")),
	        	GoalLib.entityInCloseRange("d1")
	        	
	        	
	        );
	        // attaching the goal and testdata-collector
	        var dataCollector = new TestDataCollector();
	        testAgent . setTestDataCollector(dataCollector) . setGoal(testingTask) ;


	        environment.startSimulation(); // this will press the "Play" button in the game for you
	        //goal not achieved yet
	        assertFalse(testAgent.success());

	        int i = 0 ;
	        // keep updating the agent
	        while (testingTask.getStatus().inProgress()) {
	        	System.out.println("*** " + i + ", " + testAgent.state().id + " @" + testAgent.state().worldmodel.position) ;
	            Thread.sleep(20);
	            i++ ;
	        	testAgent.update();
	        	if (i>1000) {
	        		break ;
	        	}
	        }
	        testingTask.printGoalStructureStatus();

	        // check that we have passed both tests above:
	        assertTrue(dataCollector.getNumberOfPassVerdictsSeen() == 5) ;
	        // goal status should be success
	        assertTrue(testAgent.success());
	        // close
	        testAgent.printStatus();
        }
        finally {
        	environment.close(); 
        }
    }
    
    /**
     * A test to verify that the "shortest" but with high risk path to the extinguisher is reachable.
     */
    @Test
    public void riskyPathTest() throws InterruptedException {


        // Create an environment
    	var config = new LabRecruitsConfig("HZRDIndirect") ;
    	config.light_intensity = 0.3f ;
    	var environment = new LabRecruitsEnvironment(config);
        if(USE_INSTRUMENT) {
        	instrument(environment) ;
        }

        try {
//        	if(TestSettings.USE_GRAPHICS) {
//        		System.out.println("You can drag then game window elsewhere for beter viewing. Then hit RETURN to continue.") ;
//        		new Scanner(System.in) . nextLine() ;
//        	}

	        // create a test agent
	        var testAgent = new LabRecruitsTestAgent("agent1") // matches the ID in the CSV file
        		    . attachState(new BeliefState())
        		    . attachEnvironment(environment);

	        // define the testing-task:
	        var testingTask = SEQ(
	        	GoalLib.entityInteracted("b1.1"),
	        	GoalLib.entityStateRefreshed("d1"),
	        	GoalLib.entityInvariantChecked(testAgent,
	            		"d1",
	            		"door1 should be open",
	            		(WorldEntity e) -> e.getBooleanProperty("isOpen")),
	        	GoalLib.entityInCloseRange("d1")
	        	
	        	
	        );
	        // attaching the goal and testdata-collector
	        var dataCollector = new TestDataCollector();
	        testAgent . setTestDataCollector(dataCollector) . setGoal(testingTask) ;


	        environment.startSimulation(); // this will press the "Play" button in the game for you
	        //goal not achieved yet
	        assertFalse(testAgent.success());

	        int i = 0 ;
	        // keep updating the agent
	        while (testingTask.getStatus().inProgress()) {
	        	System.out.println("*** " + i + ", " + testAgent.state().id + " @" + testAgent.state().worldmodel.position) ;
	            Thread.sleep(20);
	            i++ ;
	        	testAgent.update();
	        	if (i>1000) {
	        		break ;
	        	}
	        }
	        testingTask.printGoalStructureStatus();

	        // check that we have passed both tests above:
	        assertTrue(dataCollector.getNumberOfPassVerdictsSeen() == 1) ;
	        // goal status should be success
	        assertTrue(testAgent.success());
	        // close
	        testAgent.printStatus();
        }
        finally {
        	environment.close(); 
        }
    }
}
