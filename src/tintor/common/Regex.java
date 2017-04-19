package tintor.common;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Regex {
	public static boolean matches(String text, String pattern) {
		Pattern p = cache.get().get(pattern);
		if (p == null) {
			p = Pattern.compile(pattern);
			cache.get().put(pattern, p);
		}
		m.set(p.matcher(text));
		return m.get().find();
	}

	public static String group(int index) {
		return m.get().group(index);
	}

	private static final ThreadLocal<Map<String, Pattern>> cache = new ThreadLocal<Map<String, Pattern>>() {
		protected Map<String, Pattern> initialValue() {
			return new HashMap<String, Pattern>();
		}
	};
	private static final ThreadLocal<Matcher> m = new ThreadLocal<Matcher>();
}