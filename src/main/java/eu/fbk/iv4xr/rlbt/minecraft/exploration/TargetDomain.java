package eu.fbk.iv4xr.rlbt.minecraft.exploration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Coverage domain of a block target: the block names it can take and, for each of them,
 *  the values of its dynamic state properties. A state is a name plus one value per property. */
class TargetDomain {
	static final String AIR = "air";

	final String tag;

	/** Block name -> dynamic property -> possible values, in a fixed order. */
	final Map<String, Map<String, List<Object>>> names = new LinkedHashMap<>();

	TargetDomain(String tag) {
		this.tag = tag;
	}

	/** States of one name: the combinations of its property values, 1 when it has none. */
	int states(String name) {
		int states = 1;
		for (List<Object> values : names.get(name).values())
			states *= values.size();
		return states;
	}

	/** Whether a block with these dynamic properties is one of the states. Values are compared
	 *  as text: the observed integers of a block arrive as strings. */
	boolean contains(String name, Map<String, Object> properties) {
		Map<String, List<Object>> domain = names.get(name);
		if (domain == null || !domain.keySet().equals(properties.keySet()))
			return false;
		for (Map.Entry<String, Object> p : properties.entrySet()) {
			boolean known = false;
			for (Object value : domain.get(p.getKey()))
				known |= String.valueOf(value).equals(String.valueOf(p.getValue()));
			if (!known)
				return false;
		}
		return true;
	}

	int states() {
		int total = 0;
		for (String name : names.keySet())
			total += states(name);
		return total;
	}

	/** Every state of the domain, written as ExplorerState.blockState writes an observed one. */
	List<String> allStates() {
		List<String> all = new ArrayList<>();
		for (Map.Entry<String, Map<String, List<Object>>> name : names.entrySet()) {
			List<Map<String, Object>> combinations = new ArrayList<>();
			combinations.add(new TreeMap<>());
			for (Map.Entry<String, List<Object>> property : name.getValue().entrySet()) {
				List<Map<String, Object>> extended = new ArrayList<>();
				for (Map<String, Object> combination : combinations)
					for (Object value : property.getValue()) {
						Map<String, Object> next = new TreeMap<>(combination);
						next.put(property.getKey(), value);
						extended.add(next);
					}
				combinations = extended;
			}
			for (Map<String, Object> combination : combinations)
				all.add(name.getKey() + ExplorerState.properties(combination));
		}
		return all;
	}

	/** E.g. "10 states = cake 7 {bites=[0, 1, 2, 3, 4, 5, 6]} + candle_cake 2 {lit=[true, false]} + air 1". */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Map<String, List<Object>>> name : names.entrySet()) {
			sb.append(sb.length() == 0 ? "" : " + ").append(name.getKey()).append(' ').append(states(name.getKey()));
			if (!name.getValue().isEmpty())
				sb.append(' ').append(name.getValue());
		}
		return states() + " states = " + sb;
	}
}
