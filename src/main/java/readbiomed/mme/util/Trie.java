package readbiomed.mme.util;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Trie<T> extends gov.nih.nlm.nls.utils.Trie<T> {
	private static final long serialVersionUID = 7252238938341206601L;

	public int size() {
		return map.size();
	}

	private Map<String, T> map = new HashMap<>();

	public void insert(String key, T value) {
		map.put(key, value);
	}

	public T get(String key) {
		return map.get(key);
	}

	public void traverse() {
		map.entrySet().stream().forEach(e -> System.out.println(e));

	}

	public Map<T, String> getTermMap() {
		return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
	}
}