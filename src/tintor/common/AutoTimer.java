package tintor.common;

import java.util.ArrayList;
import java.util.Collections;

public final class AutoTimer implements AutoCloseable {
	public static final long Second = 1000000000l;

	private final String name;
	private long total;
	private AutoTimer parent;

	private AutoTimer() {
		name = null;
	}

	public AutoTimer(String name) {
		assert name != null;
		this.name = name;
		synchronized (list) {
			list.add(this);
		}
	}

	public AutoTimer open() {
		if (enabled) {
			assert parent == null;
			long now = System.nanoTime();
			current.total += now;
			parent = current;
			current = this;
			total -= now;
		}
		return this;
	}

	public void close() {
		if (enabled) {
			assert parent != null;
			long now = System.nanoTime();
			total += now;
			current = parent;
			parent = null;
			current.total -= now;
		}
	}

	private static final ArrayList<AutoTimer> list = new ArrayList<>();
	private static AutoTimer current = new AutoTimer();
	public static boolean enabled = true;

	public static void reset() {
		synchronized (list) {
			current.total = 0;
			for (AutoTimer t : list)
				t.total = 0;
		}
	}

	public static void report() {
		synchronized (list) {
			assert current.name == null;
			Collections.sort(list, (a, b) -> Long.compare(b.total, a.total));
			StringBuilder s = new StringBuilder();
			for (AutoTimer t : list) {
				double p = 100.0 * t.total / -current.total;
				if (p < 1)
					break;
				if (s.length() > 0)
					s.append(' ');
				s.append(t.name);
				s.append(':');
				s.append((int) (p * 10));
			}
			System.out.println(s);
		}
	}

	public static long total() {
		assert current.name == null;
		return -current.total;
	}
}