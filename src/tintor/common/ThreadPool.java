package tintor.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPool {
	public static final int NumThreads = Runtime.getRuntime().availableProcessors();
	public static final ExecutorService executor = new ThreadPoolExecutor(NumThreads, NumThreads, 0, TimeUnit.SECONDS,
			new SynchronousQueue<Runnable>() {
				public boolean offer(Runnable runnable) {
					try {
						super.put(runnable);
					} catch (InterruptedException e) {
						throw new Error(e);
					}
					return true;
				}

				private static final long serialVersionUID = 1L;
			}, Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
}