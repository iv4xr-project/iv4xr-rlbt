/**
 * 
 */
package eu.fbk.iv4xr.rlbt.labrecruits.distance;

import burlap.mdp.core.state.State;
import eu.fbk.iv4xr.rlbt.distance.StateDistance;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsEntityObject;
import eu.fbk.iv4xr.rlbt.labrecruits.LabRecruitsState;
import eu.fbk.iv4xr.rlbt.labrecruits.RlbtSimpleHashableState;

/**
 * @author kifetew
 *
 */
public class SorensenDiceIndex implements StateDistance {

	/**
	 * 
	 */
	public SorensenDiceIndex() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public double distance(State s1, State s2) {
		double similaritymeasure=0;
		double numcommonFeature =0;
		double totalFeature=0;
		
		LabRecruitsState labRecruitsState1 = (LabRecruitsState)s1;
		LabRecruitsState labRecruitsState2 = (LabRecruitsState)s2;
		//System.out.println("s1 = "+ labRecruitsState1.toString());
		//System.out.println("s2 = "+ labRecruitsState2.toString());
		totalFeature = labRecruitsState1.getObjectsMap().keySet().size() + labRecruitsState2.getObjectsMap().keySet().size();
		for (String k : labRecruitsState1.getObjectsMap().keySet()) {
			if (labRecruitsState2.getObjectsMap().containsKey(k)) {
				LabRecruitsEntityObject entity1 = (LabRecruitsEntityObject) labRecruitsState1.getObjectsMap().get(k);
				LabRecruitsEntityObject entity2 = (LabRecruitsEntityObject) labRecruitsState2.getObjectsMap().get(k);
				String entitybooleanproperty = getbooleanpropertystring(entity1.getLabRecruitsEntity().type);
				if (entitybooleanproperty=="") {					
					numcommonFeature++;
				}
				else {
					if (entity1.getLabRecruitsEntity().getBooleanProperty(entitybooleanproperty)== entity2.getLabRecruitsEntity().getBooleanProperty(entitybooleanproperty)) {
						numcommonFeature++;
					}					
				}	
			}
			//labRecruitsState1.get(k);
			//System.out.println(labRecruitsState1.getObjectsMap().get(k).name());
		}
			
		totalFeature = totalFeature - numcommonFeature;  // get the union
		if (totalFeature >0 )
			similaritymeasure = (2*numcommonFeature)/totalFeature;
		System.out.println("total feature = "+ totalFeature+"  intersect = "+ numcommonFeature+"  sim = "+similaritymeasure);
		return similaritymeasure;
	}
	
	private String getbooleanpropertystring(String entitytype) {
		String booleanpropertyname="";
		if (entitytype=="Door")
			booleanpropertyname ="isOpen";
		if (entitytype=="Switch")
			booleanpropertyname ="isOn";
		return booleanpropertyname;		
	}

	@Override
	public boolean subsume(State s1, State s2) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double statesimilarity(State s1, State s2) {
		// TODO Auto-generated method stub
		return 0;
	}
	

}
