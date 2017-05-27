package tintor.sokoban.test;

import java.util.ArrayList;

import lombok.SneakyThrows;
import lombok.val;
import tintor.common.Array;
import tintor.common.CpuTimer;
import tintor.common.Log;
import tintor.sokoban.AStarSolver;
import tintor.sokoban.Level;
import tintor.sokoban.Sokoban;
import tintor.sokoban.State;

// Run all Microban levels up to a certain complexity, one at a time
public class Microban {
	static CpuTimer timer = new CpuTimer();
	static int solved = 0, unsolved = 0;

	@SneakyThrows
	public static void main(String[] args) {
		args = Sokoban.init(args, 0, 0);
		long totalDist = 0, totalClosed = 0, totalOpen = 0;
		val space = Array.make(110, e -> new ArrayList<Level>());
		for (String file : "microban1 microban2 microban3 microban4 microban5".split("\\s+"))
			for (Level level : Level.loadAll(file))
				space[level.state_space()].add(level);

		for (int i = 0; i < space.length; i++)
			for (Level level : space[i]) {
				try {
					Log.raw("START %s", level.name);
					Log.raw("cells:%d alive:%d boxes:%d state_space:%s", level.cells.length, level.alive.length,
							level.num_boxes, level.state_space());

					AStarSolver solver = new AStarSolver(level);
					solver.trace = 2;
					timer.time_ns = 0;
					timer.open();
					State end = solver.solve();
					timer.close();
					if (end == null) {
						unsolved += 1;
						Log.raw("no solution! %s", timer);
					} else {
						solved += 1;
						solver.extractPath(end);
						Log.raw("solved in %d steps! %s", end.dist, timer);
						totalDist += end.dist;
					}
					totalClosed += solver.closed.size();
					totalOpen += solver.open.size();
				} catch (OutOfMemoryError e) {
					timer.close();
					Log.raw("exception after %s", timer);
					System.out.println(e);
					e.printStackTrace();
					unsolved += 1;
				} finally {
					Log.raw("END %s", level.name);
					Log.raw("");
				}

				System.gc();
				Thread.sleep(10);
				System.gc();
				Thread.sleep(10);
				System.gc();
				Thread.sleep(10);
			}
		Log.raw("solved %d, unsolved %d, DIST %d, CLOSED %d, OPEN %d", solved, unsolved, totalDist, totalClosed,
				totalOpen);
		Log.raw("snapshot [52.729 Nightly:58 I solved 390, unsolved 0, DIST 40830, CLOSED 20262253, OPEN 438244");
	}
}