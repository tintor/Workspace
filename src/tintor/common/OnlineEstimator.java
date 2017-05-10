package tintor.common;

public final class OnlineEstimator {
	private long n = 0;
	private double m = 0;
	private double s = 0;

	public void add(double x) {
		n += 1;
		double next = m + (x - m) / n;
		s += (x - m) * (x - next);
		m = next;
	}

	public void remove(double x) {
		if (n == 0)
			throw new IllegalStateException();
		if (n == 1) {
			n = 0;
			m = 0;
			s = 0;
			return;
		}
		double prev = (n * m - x) / (n - 1);
		s -= (x - m) * (x - prev);
		m = prev;
		n -= 1;
	}

	public long size() {
		return n;
	}

	public double mean() {
		return m;
	}

	public double variance() {
		return n > 1 ? s / n : 0;
	}

	public double stdev() {
		return Math.sqrt(variance());
	}

	@Override
	public String toString() {
		return String.format("OnlineEstimator(mean=%f stdev=%f size=%f)", mean(), stdev(), size());
	}
}