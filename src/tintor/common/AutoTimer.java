package tintor.common;

import java.util.ArrayList;
import java.util.Collections;

public final class AutoTimer implements AutoCloseable {
	public static final long Second = 1000000000l;

	private final String name;
	private long total;
	private AutoTimer parent;
	public final Group group;

	private AutoTimer() {
		name = null;
		group = null;
	}

	public AutoTimer(String name) {
		assert name != null;
		this.name = name;
		group = Group.tls.get();
		group.list.add(this);
	}

	public AutoTimer open() {
		assert parent == null;
		long now = System.nanoTime();
		group.current.total += now;
		parent = group.current;
		group.current = this;
		total -= now;
		return this;
	}

	public void close() {
		assert parent != null;
		long now = System.nanoTime();
		total += now;
		group.current = parent;
		parent = null;
		group.current.total -= now;
	}

	public static class Group {
		private static final ThreadLocal<Group> tls = new ThreadLocal<Group>() {
			protected Group initialValue() {
				return new Group();
			}
		};
		private final ArrayList<AutoTimer> list = new ArrayList<>();
		private AutoTimer current = new AutoTimer();

		private Group() {
		}

		public void report() {
			assert current.name == null;
			Collections.sort(list, (a, b) -> Long.compare(b.total, a.total));
			for (AutoTimer t : list) {
				double p = 100.0 * t.total / -current.total;
				if (p < 1)
					break;
				System.out.printf("%s:%d ", t.name, (int) (p * 10));
			}
			System.out.println();
		}

		public long total() {
			assert current.name == null;
			return -current.total;
		}
	}
}