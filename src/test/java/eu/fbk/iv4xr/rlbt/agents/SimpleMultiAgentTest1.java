package eu.fbk.iv4xr.rlbt.agents;

import static agents.TestSettings.USE_INSTRUMENT;
import static eu.iv4xr.framework.Iv4xrEDSL.assertTrue_;
import static eu.iv4xr.framework.Iv4xrEDSL.testgoal;
import static nl.uu.cs.aplib.AplibEDSL.SEQ;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Scanner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import agents.LabRecruitsTestAgent;
import agents.TestSettings;
import agents.tactics.GoalLib;
import agents.tactics.TacticLib;
import environments.LabRecruitsConfig;
import environments.LabRecruitsEnvironment;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.TestGoal;
import game.LabRecruitsTestServer;
import logger.JsonLoggerInstrument;
import nl.uu.cs.aplib.mainConcepts.Environment;
import nl.uu.cs.aplib.multiAgentSupport.ComNode;
import world.BeliefState;

class SimpleMultiAgentTest1 {

	private static LabRecruitsTestServer labRecruitsTestServer;

    @BeforeAll
    static void start() {
    	//TestSettings.USE_SERVER_FOR_TEST = false ;
    	// Uncomment this to make the game's graphic visible:
    	 TestSettings.USE_GRAPHICS = false ;
    	String labRecruitesExeRootDir = System.getProperty("user.dir") ;
    	labRecruitsTestServer = TestSettings.start_LabRecruitsTestServer(labRecruitesExeRootDir) ;
    }

    @AfterAll
    static void close() { if(labRecruitsTestServer!=null) labRecruitsTestServer.close(); }

    void instrument(Environment env) {
    	env.registerInstrumenter(new JsonLoggerInstrument()).turnOnDebugInstrumentation();
    }

    /**
     * A single test implementing the demo.
     */
    @Test
    public void testColorSwitchDemo() throws InterruptedException{

        var env = new LabRecruitsEnvironment(new LabRecruitsConfig("CLRSWTCH"));
        if(USE_INSTRUMENT) instrument(env) ;

//        if(TestSettings.USE_GRAPHICS) {
//    		System.out.println("You can drag then game window elsewhere for beter viewing. Then hit RETURN to continue.") ;
//    		new Scanner(System.in) . nextLine() ;
//    	}

        // creating two test agents, Butty and Screeny:
        ComNode communication = new ComNode();
        var butty   =  new LabRecruitsTestAgent("0","")
                       . attachState(new BeliefState())
                       . attachEnvironment(env)
        		       . registerTo(communication) ;
        var screeny = new LabRecruitsTestAgent("1","")
                       . attachState(new BeliefState())
                       . attachEnvironment(env)
 		               . registerTo(communication)
 		               . setTestDataCollector(new TestDataCollector()) ;

        // defining the task for agent Butty:
        var buttyTask = SEQ(
        		GoalLib.entityInteracted("CB3"), //move to the red button and interact with it
                GoalLib.pingSent("0", "1").lift(), //send a ping to the other agent
                GoalLib.entityInteracted("CB1"), //move to the blue button and interact with it
                GoalLib.pingSent("0", "1").lift(), //send a ping to the other agent
                GoalLib.entityInteracted("CB2"), //move to the green button and interact with it
                GoalLib.pingSent("0", "1").lift() //send a ping to the other agent
                ) ;
        // and the testing task for agent Screeny:
        var screenyTask = (SEQ(
        		colorIsVerified(screeny,"red","1.0/0.0/0.0").lift(),
        		colorIsVerified(screeny,"purple","1.0/0.0/1.0").lift(),
        		colorIsVerified(screeny,"white","1.0/1.0/1.0").lift()
         )) ;
        // set these goals:
        butty.setGoal(buttyTask) ;
        screeny.setGoal(screenyTask) ;

        // press play in Unity
        if (! env.startSimulation())
            throw new InterruptedException("Unity refuses to start the Simulation!");

        int tick = 0;
        // run the agent until it solves its goal:
        while (!(butty.success() && screeny.success())){
        //while (!(butty.success())){
            System.out.print("** " + tick + ":");
            if (!butty.success()) {
            	butty.update();
            	System.out.print(" agent Butty @" + butty.state().worldmodel.position) ;
                if (butty.success()) {
                	System.out.print(" Butty DONE.") ;
                	butty.printStatus() ;
                }
            }

            if (!screeny.success()) {
            	screeny.update();
            	System.out.print(" agent Screeny sees " + screeny.state().worldmodel.getElement("SCS1").getProperty("color")) ;
            	if (screeny.success()) {
            		System.out.print(" Screeny DONE.") ;
            		screeny.printStatus() ;
            	}
            }
            System.out.println("") ;

            if (tick > 200) {
            	break ;
            }
            Thread.sleep(30);
            tick++;
        }

        buttyTask.printGoalStructureStatus();

        //check that no verdict failed
        assertEquals(0, screeny.getTestDataCollector().getNumberOfFailVerdictsSeen());
        //check if all verdicts succeeded
        assertEquals(3, screeny.getTestDataCollector().getNumberOfPassVerdictsSeen());

        if (!env.close())
            throw new InterruptedException("Unity refuses to close the Simulation!");
    }


    // Wait for the ping to check the color screen with the specified color
	// Red    : 1.0/0.0/0.0
    // Purple : 1.0/0.0/1.0
    // White  : 1.0/1.0/1.0
    static TestGoal colorIsVerified(LabRecruitsTestAgent agent, String colorName, String colorCode) {

        TestGoal g = testgoal("Wait for ping")
        		. toSolve((BeliefState b) -> {
                     if(b.receivedPing){
                        b.receivedPing = false;//reset the ping
                        return true;
                     }
                        return false;
                   })

        		. invariant(agent, (BeliefState b) ->
        		        assertTrue_("",
        		        		    "Check if the color screen is " + colorName,
        		             b.evaluateEntity("SCS1", e -> e.getStringProperty("color").toString().equals(colorCode)))
        		        )
        		. withTactic(TacticLib.receivePing());

        return g ;
    }

}
