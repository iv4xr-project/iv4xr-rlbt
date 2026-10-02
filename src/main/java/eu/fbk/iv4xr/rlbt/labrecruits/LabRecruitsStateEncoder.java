package eu.fbk.iv4xr.rlbt.labrecruits;

import java.util.List;
import java.util.Map;

import burlap.mdp.core.oo.state.ObjectInstance;
import burlap.mdp.core.state.State;
import eu.fbk.iv4xr.rlbt.StateEncoder;
import world.LabEntity;

/** Encodes a LabRecruits state as a feature vector of length 2 * |entityIds|.
 *  Each entity i contributes two features, at positions 2*i and 2*i+1:
 *   - isObserved: 1.0 if the entity has been observed by the agent so far, else 0.0
 *   - value:      1.0 if the entity is active (isOn for switches, isOpen for doors),
 *                 0.0 otherwise (including when not observed, where it is meaningless) */
public class LabRecruitsStateEncoder implements StateEncoder {

    /** Fixed list of all entity IDs in the level, in the order they occupy in the vector. */
    private final List<String> entityIds;

    public LabRecruitsStateEncoder(List<String> entityIds) {
        this.entityIds = entityIds;
    }

    @Override
    public int inputSize() {
        return 2 * entityIds.size();
    }

    @Override
    public float[] features(State s) {
        LabRecruitsState lrs = (LabRecruitsState) s;
        Map<String, ObjectInstance> observedEntities = lrs.getObjectsMap();
        float[] features = new float[inputSize()];

        for (int i = 0; i < entityIds.size(); i++) {
            String id = entityIds.get(i);
            if (!observedEntities.containsKey(id)) {
                features[2 * i] = 0.0f;     // isObserved = false
                features[2 * i + 1] = 0.0f; // value: unused/don't-care
                continue;
            }
            LabRecruitsEntityObject object = (LabRecruitsEntityObject) observedEntities.get(id);
            LabEntity entity = (LabEntity) object.getLabRecruitsEntity();

            features[2 * i] = 1.0f; // isObserved = true
            if (entity.type.equalsIgnoreCase(LabEntity.DOOR))
                features[2 * i + 1] = entity.getBooleanProperty("isOpen") ? 1.0f : 0.0f;
            else if (entity.type.equalsIgnoreCase(LabEntity.SWITCH))
                features[2 * i + 1] = entity.getBooleanProperty("isOn") ? 1.0f : 0.0f;
        }

        return features;
    }

    @Override
    public String describe() {
        return "LabRecruits: 2 features (isObserved, value) x " + entityIds.size()
                + " entities = " + inputSize();
    }
}
