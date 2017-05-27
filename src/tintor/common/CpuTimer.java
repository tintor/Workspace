package tintor.common;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public final class CpuTimer {
	private static final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
	private static final long threadId = Thread.currentThread().getId();

	public long time_ns;

	// TODO which one is faster getThreadCpuTime(threadId) or getCurrentThreadCpuTime()
	public CpuTimer open() {
		assert threadId == Thread.currentThread().getId();
		time_ns -= threadMxBean.getThreadCpuTime(threadId);
		return this;
	}

	public void close() {
		assert threadId == Thread.currentThread().getId();
		time_ns += threadMxBean.getThreadCpuTime(threadId);
	}

	@Override
	public String toString() {
		return WallTimer.format(time_ns);
	}
}