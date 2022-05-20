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
public class JaccardDistance implements StateDistance {

	/**
	 * 
	 */
	public JaccardDistance() {
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
			similaritymeasure = numcommonFeature/totalFeature;
		System.out.println("total feature = "+ totalFeature+"  intersect = "+ numcommonFeature+"  sim = "+similaritymeasure);
		return similaritymeasure;
	}
	
	@Override
	public boolean subsume(State s1, State s2) {  // if state s1 is subsumed in state s2
		boolean flagsubsume=false;
		double totalFeatureS1=0;
		double numcommonFeature =0;
		
		LabRecruitsState labRecruitsState1 = (LabRecruitsState)s1;
		LabRecruitsState labRecruitsState2 = (LabRecruitsState)s2;
		//System.out.println("in subsume()-s1 = "+ labRecruitsState1.toString());
		//System.out.println("in subsume()-s2 = "+ labRecruitsState2.toString());
		//totalFeatureS1 = labRecruitsState1.getObjectsMap().keySet().size();//+ labRecruitsState2.getObjectsMap().keySet().size();
		for (String k : labRecruitsState1.getObjectsMap().keySet()) 
		{
			LabRecruitsEntityObject entity1 = (LabRecruitsEntityObject) labRecruitsState1.getObjectsMap().get(k);
			if (entity1.getLabRecruitsEntity().type =="Switch") 
				totalFeatureS1 = totalFeatureS1+1;
			
			if (labRecruitsState2.getObjectsMap().containsKey(k)) 
			{				
				LabRecruitsEntityObject entity2 = (LabRecruitsEntityObject) labRecruitsState2.getObjectsMap().get(k);
				//System.out.println("in subsume()-entity id= "+entity1.getLabRecruitsEntity().id+"   type="+entity1.getLabRecruitsEntity().type);
				if (entity1.getLabRecruitsEntity().type =="Switch") 
				{
					//System.out.println("inside loop-entity is switch= "+entity1.getLabRecruitsEntity().type);
					String entitybooleanproperty = getbooleanpropertystring(entity1.getLabRecruitsEntity().type);
					if (entity1.getLabRecruitsEntity().getBooleanProperty(entitybooleanproperty)== entity2.getLabRecruitsEntity().getBooleanProperty(entitybooleanproperty)) 
					{
						numcommonFeature++;
					}
				}
			}			
			//System.out.println(labRecruitsState1.getObjectsMap().get(k).name());
		}
		if(numcommonFeature>0 && totalFeatureS1>0) 
		{
			if(numcommonFeature == totalFeatureS1) {
				flagsubsume = true;
			}
		}
		//if(numcommonFeature==0)
		//	System.out.println("subsume()- no common entities");
		
		//System.out.println("subsume() - total feature in sq = "+ totalFeatureS1+"  intersect = "+ numcommonFeature+"  subsumeFag = "+flagsubsume);
		
		return flagsubsume;
	}
	
	
	
	
	private String getbooleanpropertystring(String entitytype) {
		String booleanpropertyname="";
		if (entitytype=="Door")
			booleanpropertyname ="isOpen";
		if (entitytype=="Switch")
			booleanpropertyname ="isOn";
		return booleanpropertyname;		
	}

}
