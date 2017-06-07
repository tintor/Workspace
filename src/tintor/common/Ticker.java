package tintor.common;

public final class Ticker {
	public volatile boolean tick;
	private int period_ms;
	private final Thread thread;

	public Ticker(int period_ms) {
		this(period_ms, true);
	}

	public Ticker(int period_ms, boolean start) {
		this.period_ms = period_ms;
		if (start) {
			thread = new Thread(this::worker);
			thread.setDaemon(true);
			thread.start();
		} else {
			thread = null;
		}
	}

	@Override
	protected void finalize() {
		if (thread != null)
			thread.interrupt();
	}

	private void worker() {
		while (true) {
			try {
				Thread.sleep(period_ms);
			} catch (InterruptedException e) {
				return;
			}
			tick = true;
		}
	}
}