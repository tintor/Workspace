package tintor.common;

public final class OnlineEstimator {
	private long size = 0;
	private double mean = 0;
	private double var_sum = 0;

	public void add(double x) {
		size += 1;
		double next = mean + (x - mean) / size;
		var_sum += (x - mean) * (x - next);
		mean = next;
	}

	public void remove(double x) {
		if (size == 0)
			throw new IllegalStateException();
		if (size == 1) {
			size = 0;
			mean = 0;
			var_sum = 0;
			return;
		}
		double prev = (size * mean - x) / (size - 1);
		var_sum -= (x - mean) * (x - prev);
		mean = prev;
		size -= 1;
	}

	public long size() {
		return size;
	}

	public double mean() {
		return mean;
	}

	public double variance() {
		return size > 1 ? var_sum / size : 0;
	}

	public double stdev() {
		return Math.sqrt(variance());
	}

	@Override
	public String toString() {
		return String.format("OnlineEstimator(mean=%f stdev=%f size=%f)", mean(), stdev(), size());
	}
}