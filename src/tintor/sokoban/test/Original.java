package tintor.sokoban.test;

import java.io.FileWriter;

import lombok.SneakyThrows;
import tintor.common.AutoTimer;
import tintor.common.Timer;
import tintor.sokoban.AStarSolver;
import tintor.sokoban.Level;
import tintor.sokoban.Sokoban;
import tintor.sokoban.State;

// Run all Original levels up to a certain complexity, one at a time
public class Original {
	static Timer timer = new Timer();
	static int solved = 0, unsolved = 0;
	static FileWriter file;

	@SneakyThrows
	public static void raw(String format, Object... args) {
		String s = String.format(format + "\n", args);
		System.out.print(s);
		file.write(s);
	}

	@SneakyThrows
	public static void main(String[] args) {
		args = Sokoban.init(args, 0, 0);
		file = new FileWriter("results.txt");
		long totalDist = 0, totalClosed = 0, totalOpen = 0;
		for (Level level : Level.loadAll("original")) {
			try {
				raw("%s\ncells:%d alive:%d boxes:%d state_space:%s", level.name, level.cells.length, level.alive.length,
						level.num_boxes, level.state_space());
				AStarSolver solver = new AStarSolver(level);
				solver.trace = 2;
				solver.closed_size_limit = 1_000_000;
				solver.min_speed = 750;
				timer.total = 0;
				AutoTimer.reset();
				timer.start();
				State end = solver.solve();
				timer.stop();
				if (end == null) {
					unsolved += 1;
					raw("no solution!");
				} else {
					solved += 1;
					solver.extractPath(end);
					raw("solved in %d steps!", end.dist);
					totalDist += end.dist;
				}
				totalClosed += solver.closed.size();
				totalOpen += solver.open.size();
			} catch (AStarSolver.ClosedSizeLimitError e) {
				timer.stop();
				raw("out of closed nodes");
				unsolved += 1;
			} catch (AStarSolver.SpeedTooLow e) {
				timer.stop();
				raw("speed too low");
				unsolved += 1;
			} catch (OutOfMemoryError e) {
				timer.stop();
				raw("out of memory");
				unsolved += 1;
			}

			raw(AutoTimer.report(new StringBuilder()).toString());
			raw("Elapsed %s", timer.human());
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