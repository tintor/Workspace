package tintor.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.runners.Parameterized;
import org.junit.runners.model.RunnerScheduler;

public class ParallelParametrized extends Parameterized {
	private static class ThreadPoolScheduler implements RunnerScheduler {
		private ExecutorService executor;

		public ThreadPoolScheduler() {
			int numThreads = 8;
			executor = Executors.newFixedThreadPool(numThreads);
		}

		@Override
		public void finished() {
			executor.shutdown();
			try {
				executor.awaitTermination(60, TimeUnit.MINUTES);
			} catch (InterruptedException exc) {
				throw new RuntimeException(exc);
			}
		}

		@Override
		public void schedule(Runnable childStatement) {
			executor.submit(childStatement);
		}
	}

	public ParallelParametrized(Class<?> klass) throws Throwable {
		super(klass);
		setScheduler(new ThreadPoolScheduler());
	}
}