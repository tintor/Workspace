package tintor.common;

public final class Timer implements AutoCloseable {
	public long total;
	private long start;

	public Timer start() {
		start = System.nanoTime();
		return this;
	}

	public void stop() {
		total += System.nanoTime() - start;
	}

	public void close() {
		stop();
	}

	public long clear() {
		long a = total;
		total = 0;
		return a;
	}

	public static String human(long total) {
		if (total <= 5000)
			return String.format("%dns", total);
		if (total <= 5000000)
			return String.format("%dus", (total + 500) / 1000);
		if (total <= 5000000000l)
			return String.format("%dms", (total + 500000) / 1000000);
		if (total <= 5 * 60 * 1000000000l)
			return String.format("%ds", (total + 500000000) / 1000000000);
		return String.format("%dm", (total + 30000000000l) / 60000000000l);
	}

	public String human() {
		long a = total;
		total = 0;
		return human(a);
	}
}