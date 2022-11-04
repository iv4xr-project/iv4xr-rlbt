/*
This program has been developed by students from the bachelor Computer Science
at Utrecht University within the Software and Game project course.

Â©Copyright Utrecht University (Department of Information and Computing Sciences)
*/


package eu.fbk.iv4xr.rlbt.utils;

import static nl.uu.cs.aplib.AplibEDSL.*;
import static eu.iv4xr.framework.Iv4xrEDSL.* ;
import nl.uu.cs.aplib.mainConcepts.Goal;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import nl.uu.cs.aplib.mainConcepts.Tactic;
import eu.iv4xr.framework.spatial.Vec3;
import eu.iv4xr.framework.mainConcepts.ObservationEvent.VerdictEvent;
import eu.iv4xr.framework.mainConcepts.TestAgent;
import eu.iv4xr.framework.mainConcepts.WorldEntity;

import world.BeliefState;
import world.LabEntity;

import java.util.function.Predicate;



/**
 * This class provide a set of standard useful sub-goals/sub-goal-structures
 * to be used by test agents to test Lab Recruits.
 */
public class GoalLib {
	
	
	/*
	public static Goal justObserve() {
		Goal g = new Goal("Just making an observation") 
				. toSolve(b -> true) 
				.withTactic(TacticLib.observe()) ;
		return g ;
	}
	*/
	
    /**
     * This method will construct a goal in which the agent will move to a known position.
     * The goal is solved if the agent's floor-position is within 0.4 distance from the
     * specified position.
     * 
     * The agent will fail the goal if it no longer believes the position to be reachable.
     */
    public static Goal positionInCloseRange(Vec3 goalPosition) {
        //define the goal
        Goal goal = new Goal("This position is in-range: " + goalPosition.toString())
        		    . toSolve((BeliefState belief) -> {
                        //check if the agent is close to the goal position
        		    	System.out.println("positionInCloseRange" +", "+ goalPosition +", "+ Vec3.dist(goalPosition,belief.worldmodel().getFloorPosition()));
        		    	
        		    	
        		    	return Vec3.dist(goalPosition,belief.worldmodel().getFloorPosition()) < 0.4 ;
                    });
        //define the goal structure
        Goal g = goal.withTactic(
        		 FIRSTof(//the tactic used to solve the goal
                   TacticLib.navigateTo(goalPosition),//move to the goal position
                   TacticLib.explore(), //explore if the goal position is unknown
                   ABORT())) ;
        return g;
    }
    

    /**
     * This method will return a goal structure in which the agent will sequentially move along 
     * specified positions. The agent does not literally have to be at each of these position;
     * being in the close-range (0.2 distance) is enough.
     * 
     * The goal-structure will fail if one of its position fails, which happen if the agent no
     * longer believes that the position is reachable.
     */
    public static GoalStructure positionsVisited(Vec3... positions) {
        GoalStructure[] subGoals = new GoalStructure[positions.length];

        for (int i = 0; i < positions.length; i++) {
            subGoals[i] = positionInCloseRange(positions[i]).lift();
        }
        return SEQ(subGoals);
    }
    
    
    /**
     * This method will construct a goal in which the agent will go a reachable position nearby
     * the given entity. Positions east/west/south/north in the distance of 1.0 from
     * the entity will be tried. Actually, the used tactic will try to reach a position
     * in the distance of 0.7 from the entity, but this goal will be achieved when the
     * agent is within 1.0 radius.
     * 
     * Becareful when you tweak these distances. There is some inaccuracy in the underlying
     * navmesh reasoning that may come up with a nearby location "inside" a door, which is
     * obviously unreachable. If the threshold above is too small, we might end up with such
     * a case.
     * 
     * The agent will fail the goal if it no longer believes the position to be reachable.
     */
    public static GoalStructure entityInCloseRange(String entityId) {
    	//define the goal
        Goal goal = new Goal("This entity is closeby: " + entityId)
        		    . toSolve((BeliefState belief) -> {
                        //check if the agent is close to the goal position
        		    	var e = belief.worldmodel().getElement(entityId) ;
        		    	if (e == null) return false ;
                        return Vec3.dist(belief.worldmodel().getFloorPosition(),e.getFloorPosition()) <= 1 ;
                    });
        //define the goal structure
        return goal.withTactic(
        		 FIRSTof(//the tactic used to solve the goal
                   TacticLib.navigateToCloseByPosition(entityId),//move to the goal position
                   TacticLib.explore(), //explore if the goal position is unknown
                   ABORT())) 
        	  . lift();
    }
    
    /**
     * This method will construct a goal where the agent will go to the location of the given
     * entity. It will try to get within the distance of the specified delta from the center
     * position of the entity.
     * 
     * <p>If the flag addObserve is true, two additional update-cycles are added at the end 
     * to ensure the agent's state is updated. E.g. if the target is a goal flag, this makes
     * sure that the point obtained from reaching the goal flag (first time) is observed by
     * the Java agent.
     * 
     * <p>The entity must be an entity that can be targeted by the pathfinder, such as button or
     * goalflag. 
     * 
     * <p>Note that doors cannot be targeted with this goal. A door is an obstacle, so literally 
     * its center point will always appear as unreachable to the pathfinder. Furniture like
     * table and chair cannot be targetted either. They are put outside the navigation mesh, so
     * the pathfinder won't be able to find a path to their center.
     */
    public static GoalStructure atBGF(String entityId, float delta, boolean addObserve) {
        //the first goal is to navigate to the entity:
    	float deltaSq = delta*delta ;
        var g1 = 
        	  goal(String.format("The agent is at: [%s]", entityId))
        	  . toSolve((BeliefState belief) -> {
        		  var e = (LabEntity) belief.worldmodel.getElement(entityId) ;
        		  // bug .. .should be distsq:
        		  // return e!=null && Vec3.dist(belief.worldmodel.getFloorPosition(), e.getFloorPosition()) < 0.35 ;
        		  // System.out.print("entityinteracted: navigate to" + e);
        		  return e!=null && Vec3.sub(belief.worldmodel().getFloorPosition(), e.getFloorPosition()).lengthSq() <= deltaSq ;
        	    })
        	  . withTactic(
                    FIRSTof( //the tactic used to solve the goal
                    TacticLib.navigateTo(entityId), //try to move to the entity
                    TacticLib.explore(), //find the entity
                    ABORT())) 
              ;
        
        if (addObserve) 
        	return SEQ(g1.lift(), SUCCESS("just observing")) ;
        else 
        	return g1.lift() ;
    }
    

    /**
     * Construct a goal structure that will make an agent to move towards the given entity,
     * until it is in the interaction-distance with the entity; and then interacts with it.
     * Currently the used tactic is not smart enough to handle a moving entity, in particular 
     * if it moves while the agent is entering the interaction distance.
     * 
     * The goal fails if the agent no longer believes that the entity is reachable, or when it fails to 
     * interact with it.
     *
     * @param entityId The entity to walk to and interact with
     * @return A goal structure
     * @Incomplete: this goal should check if the object has a given desired state, and perhaps include a position check in the goal predicate itself
     */
    public static GoalStructure entityInteracted(String entityId) {
        //the first goal is to navigate to the entity:
        var goal1 = 
        	  goal(String.format("This entity is in interaction distance: [%s]", entityId))
        	  . toSolve((BeliefState belief) -> {
        		  var e = (LabEntity) belief.worldmodel.getElement(entityId) ;
        		  // bug .. .should be distsq:
        		  // return e!=null && Vec3.dist(belief.worldmodel.getFloorPosition(), e.getFloorPosition()) < 0.35 ;
        		  // System.out.print("entityinteracted: navigate to" + e);
        		  return e!=null && Vec3.sub(belief.worldmodel().getFloorPosition(), e.getFloorPosition()).lengthSq() <= 1 ;
        	    })
        	  . withTactic(
                    FIRSTof( //the tactic used to solve the goal
                    TacticLib.navigateTo(entityId), //try to move to the entity
                    TacticLib.explore(), //find the entity
                    ABORT())) 
              . lift();

        // then, the 2nd goal is to interact with the object:
        var goal2 = 
        	  goal(String.format("This entity is interacted: [%s]", entityId))
        	  . toSolve((BeliefState belief) -> true) 
              . withTactic(
        		   FIRSTof( //the tactic used to solve the goal
                   TacticLib.interact(entityId),// interact with the entity
                   ABORT())) // observe the objects
              . lift();

        return SEQ(goal1, goal2, SUCCESS());// adding success() to force observation after the interaction
    }
    

	/**
	 * This goal will make agent to navigate towards the given entity, and make sure that
	 * the agent has the latest observation of the entity. Getting the entity within
	 * sight is enough to complete this goal.
	 * 
	 * This goal fails if the agent no longer believes that the entity is reachable.
	 */
    public static GoalStructure entityStateRefreshed(String id){
        var g =  goal("The belief on this entity is refreshed: " + id)
                .toSolve((BeliefState b) -> {
                	
                /*	System.out.println("entity state refresh: " + 
                b.evaluateEntity(id, e -> b.age(e) == 0)
                	+"get goal location "+ b.getGoalLocation()
                		); */
                	
                  var entity = b.worldmodel.getElement(id);
                  return   (b.evaluateEntity(id, e -> b.age(e) == 0)
                		  
                		  );
                  
                })
                .withTactic(FIRSTof(
                        TacticLib.navigateToClosestReachableNode(id),              		
                        TacticLib.explore(),
                        ABORT()))
                .lift() ;
        	
       		 return g;
        	// g.maxbudget(8);
       		// return FIRSTof(g, SUCCESS()) ;
    }

    /**
     * This goal will make agent to navigate to the given entity, and make sure that the state of the
     * entity satisfies the given predicate.
     * 
     * The goal fail if the agent no longer believes that the entity is reachable, or when the
     * predicate is not satisfied when it is observed.
   
     * @param id: entityId
     * @return Goal
     */
    public static GoalStructure entityInspected(String id, Predicate<WorldEntity> predicate){
        return SEQ(
            entityStateRefreshed(id),
            goal("This entity is inspected: " + id)
                .toSolve((BeliefState b) -> b.evaluateEntity(id, predicate))
                .withTactic(
                    SEQ(
                    TacticLib.observe(),
                    ABORT()))
                .lift()
        );
    }
    
    /**
     * Create a test-goal to check the state of an in-game entity, whether it satisfies the given predicate.
     * Internally, this goal will first spend one tick to get a fresh observation, then at the next tick it
     * will do the checking.
     * 
     * @param agent  The test agent to do the checking.
     * @param id     The id of the in-game entity to check.
     * @param info   Some string describing the check.
     * @param predicate  The predicate that is expected to hold on the entity.
     * @return
     */
    public static GoalStructure entityInvariantChecked(TestAgent agent, String id, String info, Predicate<WorldEntity> predicate){
        return SEQ(
            entityStateRefreshed(id),
            testgoal("Invariant check " + id, agent)
            . toSolve((BeliefState b) -> true) // nothing to solve
            . invariant(agent,                 // something to check :)
            		(BeliefState b) -> {
            			if (b.evaluateEntity(id, predicate))
            			   return new VerdictEvent("Object-check " + id, info, true) ;
            			else 
            			   return new VerdictEvent("Object-check " + id, info, false) ;
            			
            		}
            		)
            .withTactic(TacticLib.observe())
            .lift()
        );
    }
    
    /**
     * Create a test-goal to check the state of the game, whether it satisfies the given predicate.
     * Internally, this goal will first spend one tick to get a fresh observation, then at the next tick it
     * will do the checking.
     */ 
    public static GoalStructure invariantChecked(TestAgent agent, String info, Predicate<BeliefState> predicate){
        return SEQ(
            testgoal("Evaluate " + info, agent)
            .toSolve((BeliefState b) -> true) // nothing to solve
            .invariant(agent,                 // something to check :)
            		(BeliefState b) -> {
            			if (predicate.test(b))
            			   return new VerdictEvent("Inv-check", info, true) ;
            			else 
            			   return new VerdictEvent("Inv-check" , info, false) ;
            		    }
            		)
            .withTactic(SEQ(
                    TacticLib.observe(),
                    ABORT())).lift()
        );
    }

    /**
     * This goal structure will cause the agent to share its memory once with all connected agents in the broadcast
     *
     * @param id: The id of the sending agent
     * @return A goal structure which will be concluded when the agent shared its memory once
     */
    public static GoalStructure observationSent(String id){
        return goal("Map is shared").toSolve((BeliefState belief) -> true).withTactic(
                TacticLib.shareObservation(id)
        ).lift();
    }

    /**
     * This goal structure will cause the agent to send a ping to the target agent
     * @param idFrom: The id of the sending agent
     * @param idTo: The id of the receiving agent
     * @return A goal structure which will be concluded when the agent send a ping to the target agent
     */
    public static Goal pingSent(String idFrom, String idTo){
        return new Goal("Send ping").toSolve((BeliefState belief) -> true).withTactic(
                TacticLib.sendPing(idFrom, idTo)
        );
    }
    
}
