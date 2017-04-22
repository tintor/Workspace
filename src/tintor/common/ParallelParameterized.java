package tintor.common;

import java.util.ArrayList;
import java.util.concurrent.Future;

import org.junit.runners.Parameterized;
import org.junit.runners.model.RunnerScheduler;

public class ParallelParameterized extends Parameterized {
	private static class ThreadPoolScheduler implements RunnerScheduler {
		final ArrayList<Future<?>> tasks = new ArrayList<Future<?>>();

		@Override
		public void finished() {
			for (Future<?> task : tasks)
				try {
					task.get();
				} catch (Exception e) {
					throw new Error(e);
				}
		}

		@Override
		public void schedule(Runnable childStatement) {
			tasks.add(ThreadPool.executor.submit(childStatement));
		}
	}

	public ParallelParameterized(Class<?> klass) throws Throwable {
		super(klass);
		setScheduler(new ThreadPoolScheduler());
	}
}