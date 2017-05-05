package tintor.common;

import java.util.ArrayList;
import java.util.Collections;

public final class AutoTimer implements AutoCloseable, Comparable<AutoTimer> {
	private final String name;
	private long total;
	private AutoTimer parent;

	private AutoTimer() {
		name = null;
	}

	public AutoTimer(String name) {
		assert name != null;
		this.name = name;
		list.add(this);
	}

	public AutoTimer open() {
		assert parent == null;
		long now = System.nanoTime();
		current.total += now;
		parent = current;
		current = this;
		total -= now;
		return this;
	}

	public void close() {
		assert parent != null;
		long now = System.nanoTime();
		total += now;
		current = parent;
		parent = null;
		current.total -= now;
	}

	@Override
	public int compareTo(AutoTimer o) {
		if (o.total > total)
			return 1;
		if (o.total < total)
			return -1;
		return 0;
	}

	// static

	public static final long Second = 1000000000l;
	private static final ArrayList<AutoTimer> list = new ArrayList<>();
	private static AutoTimer current = new AutoTimer();

	public static void reset() {
		current.total = 0;
		list.clear();
	}

	public static void report() {
		assert current.name == null;
		Collections.sort(list);
		for (AutoTimer t : list) {
			double p = 100.0 * t.total / -current.total;
			if (p < 1)
				break;
			System.out.printf("%s:%d ", t.name, (int) (p * 10));
		}
		System.out.println();
	}

	public static long total() {
		assert current.name == null;
		return -current.total;
	}
}