package tintor.common;

public final class WallTimer {
	public long time_ns;

	public WallTimer open() {
		time_ns -= System.nanoTime();
		return this;
	}

	public void close() {
		time_ns += System.nanoTime();
	}

	public long clear() {
		long a = time_ns;
		time_ns = 0;
		return a;
	}

	public static String format(long time_ns) {
		final long Microsec = 1000l;
		final long Millisec = 1000 * Microsec;
		final long Second = 1000 * Millisec;
		final long Minute = 60 * Second;

		if (time_ns <= 5 * Microsec)
			return String.format("%dns", time_ns);
		if (time_ns <= 5 * Millisec)
			return String.format("%dus", (time_ns + Microsec / 2) / Microsec);
		if (time_ns <= 5 * Second)
			return String.format("%dms", (time_ns + Millisec / 2) / Millisec);

		int time_sec = (int) ((time_ns + Second / 2) / Second);
		if (time_sec < 60)
			return String.format("%ds", time_sec);

		int time_min = (int) ((time_ns + Minute / 2) / Minute);
		if (time_min < 60) {
			int s = time_sec % 60;
			if (s == 0)
				return String.format("%dm", time_sec / 60);
			return String.format("%dm%ds", time_sec / 60, s);
		}

		int m = time_min % 60;
		if (m == 0)
			return String.format("%dh", time_min / 60);
		return String.format("%dh%dm", time_min / 60, m);
	}

	@Override
	public String toString() {
		return format(time_ns);
	}
}