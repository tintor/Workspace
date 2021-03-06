package tintor.sokoban.test;

import java.io.FileWriter;

import lombok.SneakyThrows;
import tintor.common.AutoTimer;
import tintor.common.CpuTimer;
import tintor.common.Flags;
import tintor.sokoban.AStarSolver;
import tintor.sokoban.Level;
import tintor.sokoban.Sokoban;
import tintor.sokoban.State;

// Run all Original levels up to a certain complexity, one at a time
public class Original {
	static CpuTimer timer = new CpuTimer();
	static int solved = 0, unsolved = 0;
	static FileWriter file;

	@SneakyThrows
	public static void raw(String format, Object... args) {
		String s = String.format(format + "\n", args);
		System.out.print(s);
		file.write(s);
	}

	private final static Flags.Int closed_size_limit = new Flags.Int("closed_size_limit", 1_000_000);
	private final static Flags.Int min_speed = new Flags.Int("min_speed", 750);

	@SneakyThrows
	public static void main(String[] args) {
		args = Sokoban.init(args, 0, 0);
		file = new FileWriter("results.txt");
		raw("closed_size_limit:%s min_speed:%s", closed_size_limit.value, min_speed.value);
		long totalDist = 0, totalClosed = 0, totalOpen = 0;
		for (Level level : Level.loadAll("original")) {
			try {
				raw("START %s", level.name);
				raw("cells:%d alive:%d boxes:%d state_space:%s", level.cells.length, level.alive.length,
						level.num_boxes, level.state_space());
				AStarSolver solver = new AStarSolver(level, true);
				solver.trace = 2;
				//solver.max_cpu_time = 10 * 60 * AutoTimer.Second;
				timer.time_ns = 0;
				AutoTimer.reset();
				timer.open();
				State end = solver.solve();
				timer.close();

				/*if (timer.time_ns > 10 * 60 * AutoTimer.Second) {
					raw("out of time");
					unsolved += 1;
				} else*/ if (end == null) {
					unsolved += 1;
					raw("no solution!");
				} else {
					solved += 1;
					int pushes = 0;
					for (State s : solver.extractPath(end))
						pushes += s.pushes;
					raw("solved in %d pushes!", pushes);
					totalDist += end.dist;
				}
				totalClosed += solver.closed.size();
				totalOpen += solver.open.size();
			} catch (AStarSolver.ExpiredError e) {
				timer.close();
				raw("out of time");
				unsolved += 1;
			} catch (OutOfMemoryError e) {
				timer.close();
				raw("out of memory");
				unsolved += 1;
			}

			raw(AutoTimer.report(new StringBuilder()).toString());
			raw("Elapsed %s", timer);
			raw("END %s", level.name);
			raw("");
			System.out.flush();
			file.flush();

			System.gc();
			Thread.sleep(10);
			System.gc();
			Thread.sleep(10);
			System.gc();
			Thread.sleep(10);
		}
		raw("solved %d, unsolved %d, DIST %d, CLOSED %d, OPEN %d", solved, unsolved, totalDist, totalClosed, totalOpen);
	}
}
