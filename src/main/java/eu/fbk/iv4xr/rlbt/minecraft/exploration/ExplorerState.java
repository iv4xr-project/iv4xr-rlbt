package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import burlap.mdp.core.state.State;
import eu.iv4xr.framework.spatial.Vec3;

/** What the exploring agent observes of the level: itself (position, held item, inventory),
 *  the block of each block target and the entities of the level. */
class ExplorerState implements State {

	/** An observed entity: a tagged one, or an untagged one that belongs to the level. */
	static class Entity {
		final String tag;   // null when untagged
		final String type;
		final Vec3 position;
		final boolean moving;
		final Map<String, Object> properties;   // without the identifying ones

		Entity(String tag, String type, Vec3 position, boolean moving, Map<String, Object> properties) {
			this.tag = tag;
			this.type = type;
			this.position = position;
			this.moving = moving;
			this.properties = properties;
		}
	}

	final Vec3 agentPosition;
	final String heldItem;   // null with an empty hand

	/** Item -> count. */
	final Map<String, Float> inventory = new TreeMap<>();

	/** Tag -> name of the block observed for it, "air" for an empty cell. */
	final Map<String, String> blockNames = new TreeMap<>();

	/** Tag -> state properties of that block, without the fixed ones. */
	final Map<String, Map<String, Object>> blockProperties = new TreeMap<>();

	/** Tag, or type#id when untagged -> entity. A tagged entity that is not observed any more
	 *  (killed, or out of the entity radius) is kept with a null value. */
	final Map<String, Entity> entities = new TreeMap<>();

	ExplorerState(Vec3 agentPosition, String heldItem) {
		this.agentPosition = agentPosition;
		this.heldItem = heldItem;
	}

	/** The state on three lines: agent, blocks, entities. The inventory is only counted. */
	String describe() {
		StringBuilder blocks = new StringBuilder(), seen = new StringBuilder();
		for (Map.Entry<String, String> b : blockNames.entrySet())
			blocks.append(' ').append(b.getKey()).append('=').append(b.getValue())
					.append(properties(blockProperties.get(b.getKey())));
		for (Map.Entry<String, Entity> e : entities.entrySet()) {
			Entity entity = e.getValue();
			seen.append(' ').append(e.getKey()).append('=').append(entity == null ? "gone"
					: entity.type + "@" + cell(entity.position) + properties(entity.properties));
		}
		return "agent pos=" + cell(agentPosition) + " hand=" + heldItem
				+ " inventory=" + inventory.size() + " item types"
				+ "\n          blocks:" + blocks + "\n          entities:" + seen;
	}

	/** The state of a block target: the name of its block and its dynamic properties. */
	String blockState(String tag) {
		return blockNames.get(tag) + properties(blockProperties.get(tag));
	}

	/** Everything observed but the position of the agent, as name -> value: the held item, the
	 *  count of each item, the block of each target and each entity. An untagged entity is
	 *  named by its type, numbered when there are more of the same. */
	Map<String, String> facts() {
		Map<String, String> facts = new TreeMap<>();
		facts.put("hand", String.valueOf(heldItem));
		inventory.forEach((item, count) -> facts.put("inv:" + item, String.valueOf(count.intValue())));
		for (Map.Entry<String, String> b : blockNames.entrySet())
			facts.put("block:" + b.getKey(), blockState(b.getKey()));
		Map<String, Integer> untagged = new HashMap<>();
		for (Map.Entry<String, Entity> e : entities.entrySet()) {
			Entity entity = e.getValue();
			String name = e.getKey();
			if (entity != null && entity.tag == null) {
				int n = untagged.merge(entity.type, 1, Integer::sum);
				name = entity.type + (n == 1 ? "" : "#" + n);
			}
			facts.put("entity:" + name, entity == null ? "gone" : entity.type + "@" + cell(entity.position)
					+ properties(entity.properties) + (entity.moving ? " moving" : ""));
		}
		return facts;
	}

	/** What differs in a later state, as "name: before -> after". Empty when nothing changed
	 *  but the position of the agent: the action in between had no effect. */
	List<String> changes(ExplorerState after) {
		Map<String, String> was = facts(), is = after.facts();
		TreeSet<String> names = new TreeSet<>(was.keySet());
		names.addAll(is.keySet());
		List<String> changes = new ArrayList<>();
		for (String name : names) {
			String old = was.getOrDefault(name, "none"), now = is.getOrDefault(name, "none");
			if (!old.equals(now))
				changes.add(name + ": " + old + " -> " + now);
		}
		return changes;
	}

	/** The properties as {a=1,b=2}; empty string if there is none. */
	static String properties(Map<String, Object> properties) {
		return properties.isEmpty() ? "" : properties.toString().replace(", ", ",");
	}

	private static String cell(Vec3 p) {
		return "(" + (int) Math.floor(p.x) + "," + (int) Math.floor(p.y) + "," + (int) Math.floor(p.z) + ")";
	}

	@Override
	public List<Object> variableKeys() {
		return List.of("heldItem", "inventory", "blockNames", "blockProperties", "entities");
	}

	@Override
	public Object get(Object variableKey) {
		switch (String.valueOf(variableKey)) {
			case "heldItem": return heldItem;
			case "inventory": return inventory;
			case "blockNames": return blockNames;
			case "blockProperties": return blockProperties;
			case "entities": return entities;
			default: throw new IllegalArgumentException("Unknown variable: " + variableKey);
		}
	}

	@Override
	public State copy() {
		return this;
	}
}
