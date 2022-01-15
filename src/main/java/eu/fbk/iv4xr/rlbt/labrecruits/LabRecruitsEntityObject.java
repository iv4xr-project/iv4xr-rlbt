package eu.fbk.iv4xr.rlbt.labrecruits;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import burlap.mdp.core.oo.state.ObjectInstance;
import eu.iv4xr.framework.mainConcepts.WorldEntity;

public class LabRecruitsEntityObject implements ObjectInstance, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2765476123303490377L;

	WorldEntity labRecruitsEntity;
	
	String objectName;
	String className;
	
	public static final String ENTITY_KEY = "LAB_ENTITY";
	private final List<Object> keys = Arrays.<Object>asList(ENTITY_KEY);
	
	public LabRecruitsEntityObject() {
				
	}

	public LabRecruitsEntityObject(WorldEntity worldEntity) {
		super();
		
		labRecruitsEntity = worldEntity;
		
		className = worldEntity.getClass().getName();
		objectName = worldEntity.id;
		
	}

	@Override
	public List<Object> variableKeys() {
		return keys;
	}

	@Override
	public Object get(Object variableKey) {
		return labRecruitsEntity;
	}

	@Override
	public LabRecruitsEntityObject copy() {
		try {
			return new LabRecruitsEntityObject(this.labRecruitsEntity.deepclone());
		} catch (ClassNotFoundException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public String className() {
		return className;
	}

	@Override
	public String name() {
		return objectName;
	}
	
	
	public void setclassName(String cname) {
		className =  cname;
	}

	
	public void setname(String obname) {
		objectName= obname;
	}
	
	
	@Override
	public ObjectInstance copyWithName(String objectName) {
		return new LabRecruitsEntityObject(this.labRecruitsEntity);
	}

	/**
	 * @return the labRecruitsEntity
	 */
	public WorldEntity getLabRecruitsEntity() {
		return labRecruitsEntity;
	}

	
	@Override
	public String toString() {
		return name();
	}
}
