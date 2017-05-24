package tintor.sokoban.test;

import java.util.ArrayList;

import lombok.SneakyThrows;
import lombok.val;
import tintor.common.Array;
import tintor.common.Log;
import tintor.common.Timer;
import tintor.sokoban.AStarSolver;
import tintor.sokoban.Level;
import tintor.sokoban.Sokoban;
import tintor.sokoban.State;

// Run all Microban levels up to a certain complexity, one at a time
public class Microban {
	static Timer timer = new Timer();
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
					Log.raw("%s cells:%d alive:%d boxes:%d state_space:%s", level.name, level.cells.length,
							level.alive.length, level.num_boxes, level.state_space());

					AStarSolver solver = new AStarSolver(level);
					solver.trace = 2;
					timer.total = 0;
					timer.start();
					State end = solver.solve();
					timer.stop();
					if (end == null) {
						unsolved += 1;
						Log.raw("no solution! %s", timer.human());
					} else {
						solved += 1;
						solver.extractPath(end);
						Log.raw("solved in %d steps! %s", end.dist, timer.human());
						totalDist += end.dist;
					}
					totalClosed += solver.closed.size();
					totalOpen += solver.open.size();
					Log.raw("");
				} catch (OutOfMemoryError e) {
					Log.raw("exception after %s", timer.human());
					System.out.println(e);
					e.printStackTrace();
					unsolved += 1;
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