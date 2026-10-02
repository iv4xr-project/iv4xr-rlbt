package eu.fbk.iv4xr.rlbt.minecraft;

import burlap.mdp.core.oo.state.ObjectInstance;
import eu.iv4xr.framework.mainConcepts.WorldEntity;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class MinecraftEntityObject implements ObjectInstance, Serializable {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    WorldEntity minecraftEntity;

    String objectName;
    String className;

    public static final String ENTITY_KEY = "MC_ENTITY";
    private final List<Object> keys = Arrays.<Object>asList(ENTITY_KEY);

    public MinecraftEntityObject() { }

    public MinecraftEntityObject(WorldEntity worldEntity) {
        super();

        minecraftEntity = worldEntity;
        className = worldEntity.getClass().getName();
        objectName = worldEntity.id;
    }

    @Override
    public List<Object> variableKeys() {
        return keys;
    }

    @Override
    public Object get(Object variableKey) {
        return minecraftEntity;
    }

    @Override
    public MinecraftEntityObject copy() {
        try {
            return new MinecraftEntityObject(this.minecraftEntity.deepclone());
        } catch (ClassNotFoundException | IOException e) {
            // deepclone() serialises the entity; a failure here means the state
            // cannot be snapshotted, so the caller gets a null copy back
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
        return new MinecraftEntityObject(this.minecraftEntity);
    }

    public WorldEntity getMinecraftEntity() {return minecraftEntity;}


    @Override
    public String toString() {
        return name();
    }

}
