package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import burlap.mdp.core.state.State;
import eu.fbk.iv4xr.rlbt.StateEncoder;
import eu.iv4xr.framework.spatial.Vec3;

/** Encodes an {@link ExplorerState} as four concatenated blocks. Every feature has a label:
 *   blocks     per block target, a one-hot of its name (the names of its domain plus "other"),
 *              then per dynamic property one feature if boolean, a one-hot of its values otherwise
 *              (a property shared by several names of the target takes its features once)
 *   hand       one-hot of the held item, over the selectable items and the empty hand
 *   inventory  per item, its count over the count at the start
 *   entities   per tagged entity and per type of untagged one: present, health over the health
 *              at the start, x and z over the level box, moving
 *  The position of the agent is left out. */
class ExplorerStateEncoder implements StateEncoder {

	static final String OTHER = "other";

	private final List<String> labels = new ArrayList<>();
	private final Map<String, Integer> index = new HashMap<>();
	private final int blocksSize, handSize, inventorySize, entitiesSize;

	private final Collection<String> targets;
	private final List<String> items;
	private final List<String> entityTags = new ArrayList<>();
	private final List<String> entityTypes = new ArrayList<>();   // of the untagged ones

	private final Map<String, Float> startInventory;
	private final Map<String, Float> startHealth = new HashMap<>();   // by entity slot
	private float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

	/**
	 * @param domains      the domain of each block target
	 * @param items        the items the agent can hold
	 * @param start        the state at the start of the level: its entities get a slot, its
	 *                     inventory counts and entity health are the reference values
	 * @param tagPositions the tagged positions, whose box bounds the entity coordinates
	 */
	ExplorerStateEncoder(Map<String, TargetDomain> domains, List<String> items, ExplorerState start,
			Map<String, Vec3> tagPositions) {
		this.targets = domains.keySet();
		this.items = items;
		this.startInventory = start.inventory;

		for (TargetDomain domain : domains.values()) {
			for (String name : domain.names.keySet())
				add(domain.tag + "=" + name);
			add(domain.tag + "=" + OTHER);
			// the values of each property, over all the names that have it
			Map<String, TreeSet<String>> properties = new LinkedHashMap<>();
			Map<String, Boolean> isBoolean = new HashMap<>();
			for (Map<String, List<Object>> ofName : domain.names.values())
				for (Map.Entry<String, List<Object>> p : ofName.entrySet())
					for (Object value : p.getValue()) {
						properties.computeIfAbsent(p.getKey(), k -> new TreeSet<>()).add(String.valueOf(value));
						isBoolean.merge(p.getKey(), value instanceof Boolean, Boolean::logicalAnd);
					}
			for (Map.Entry<String, TreeSet<String>> p : properties.entrySet())
				if (isBoolean.get(p.getKey()))
					add(domain.tag + "." + p.getKey());
				else
					for (String value : p.getValue())
						add(domain.tag + "." + p.getKey() + "=" + value);
		}
		blocksSize = labels.size();

		for (String item : items)
			add("hand=" + item);
		add("hand=" + MineExplorer.EMPTY_HAND);
		handSize = labels.size() - blocksSize;

		for (String item : items)
			add("inv:" + item);
		inventorySize = labels.size() - blocksSize - handSize;

		TreeSet<String> types = new TreeSet<>();
		for (Map.Entry<String, ExplorerState.Entity> e : start.entities.entrySet())
			if (e.getValue() == null || e.getValue().tag != null)
				entityTags.add(e.getKey());
			else
				types.add(e.getValue().type);
		entityTypes.addAll(types);
		for (String slot : entitySlots()) {
			for (String feature : List.of("present", "health", "x", "z", "moving"))
				add(slot + "." + feature);
			ExplorerState.Entity entity = entity(start, slot);
			if (entity != null && entity.properties.get("health") instanceof Number)
				startHealth.put(slot, ((Number) entity.properties.get("health")).floatValue());
		}
		entitiesSize = labels.size() - blocksSize - handSize - inventorySize;

		for (Vec3 p : tagPositions.values()) {
			minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
			minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
		}
	}

	private void add(String label) {
		if (index.put(label, labels.size()) != null)
			throw new IllegalStateException("Duplicate feature " + label);
		labels.add(label);
	}

	private List<String> entitySlots() {
		List<String> slots = new ArrayList<>(entityTags);
		slots.addAll(entityTypes);
		return slots;
	}

	/** The entity of a slot: the tagged one, or the first untagged one of that type. */
	private ExplorerState.Entity entity(ExplorerState s, String slot) {
		if (entityTags.contains(slot))
			return s.entities.get(slot);
		for (ExplorerState.Entity entity : s.entities.values())
			if (entity != null && entity.tag == null && entity.type.equals(slot))
				return entity;
		return null;
	}

	/** Sets a feature, if the layout has it: a value outside the domains has no feature. */
	private void set(float[] features, String label, float value) {
		Integer i = index.get(label);
		if (i != null)
			features[i] = value;
	}

	private static float scale(float value, float min, float max) {
		return max <= min ? 0f : Math.max(0f, Math.min(1f, (value - min) / (max - min)));
	}

	@Override
	public int inputSize() {
		return labels.size();
	}

	@Override
	public float[] features(State state) {
		ExplorerState s = (ExplorerState) state;
		float[] features = new float[labels.size()];

		for (String tag : targets) {
			String name = tag + "=" + s.blockNames.get(tag);
			set(features, index.containsKey(name) ? name : tag + "=" + OTHER, 1f);
			for (Map.Entry<String, Object> p : s.blockProperties.get(tag).entrySet()) {
				String property = tag + "." + p.getKey(), value = String.valueOf(p.getValue());
				if (index.containsKey(property))
					set(features, property, value.equals("true") ? 1f : 0f);
				else
					set(features, property + "=" + value, 1f);
			}
		}

		set(features, "hand=" + (s.heldItem == null ? MineExplorer.EMPTY_HAND : s.heldItem), 1f);

		for (String item : items)
			set(features, "inv:" + item,
					s.inventory.getOrDefault(item, 0f) / Math.max(1f, startInventory.getOrDefault(item, 0f)));

		for (String slot : entitySlots()) {
			ExplorerState.Entity entity = entity(s, slot);
			if (entity == null)
				continue;
			set(features, slot + ".present", 1f);
			Object health = entity.properties.get("health");
			if (health instanceof Number && startHealth.containsKey(slot))
				set(features, slot + ".health", scale(((Number) health).floatValue(), 0f, startHealth.get(slot)));
			set(features, slot + ".x", scale(entity.position.x, minX, maxX));
			set(features, slot + ".z", scale(entity.position.z, minZ, maxZ));
			set(features, slot + ".moving", entity.moving ? 1f : 0f);
		}
		return features;
	}

	@Override
	public String describe() {
		return "Minecraft exploration: blocks(" + blocksSize + ") + hand(" + handSize + ") + inventory("
				+ inventorySize + ") + entities(" + entitiesSize + ") = " + labels.size();
	}

	/** The features that are not zero, by label: alone when 1, with its value otherwise. */
	String active(float[] features) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < features.length; i++)
			if (features[i] != 0f)
				sb.append(sb.length() == 0 ? "" : " ").append(labels.get(i))
						.append(features[i] == 1f ? "" : ":" + features[i]);
		return sb.toString();
	}

	/** Every label with the features it takes, e.g. for the block targets of a level. */
	Map<String, Integer> sizes() {
		Map<String, Integer> sizes = new TreeMap<>();
		for (String label : labels)
			sizes.merge(label.split("[=.:]")[0], 1, Integer::sum);
		return sizes;
	}
}