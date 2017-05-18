package tintor.common;

public final class Timer implements AutoCloseable {
	public long total;

	public Timer start() {
		total -= System.nanoTime();
		return this;
	}

	public void stop() {
		total += System.nanoTime();
	}

	public void close() {
		stop();
	}

	public long clear() {
		long a = total;
		total = 0;
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
		if (time_min < 60)
			return String.format("%dm%ds", time_sec / 60, time_sec % 60);

		return String.format("%dh%dm", time_min / 60, time_min % 60);
	}

	public String human() {
		long a = total;
		total = 0;
		return format(a);
	}
}