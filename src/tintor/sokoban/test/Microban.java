package tintor.sokoban.test;

import static tintor.common.Util.print;

import java.util.ArrayList;

import lombok.SneakyThrows;
import lombok.val;
import tintor.common.Array;
import tintor.common.CpuTimer;
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
					print("START %s\n", level.name);
					print("cells:%s alive:%s ", level.cells.length, level.alive.length);
					print("boxes:%s state_space:%s\n", level.num_boxes, level.state_space());

					AStarSolver solver = new AStarSolver(level, true);
					solver.trace = 2;
					timer.time_ns = 0;
					timer.open();
					State end = solver.solve();
					timer.close();
					if (end == null) {
						unsolved += 1;
						print("no solution! %s\n", timer);
					} else {
						solved += 1;
						solver.extractPath(end);
						print("solved in %s steps! %s\n", end.dist, timer);
						totalDist += end.dist;
					}
					totalClosed += solver.closed.size();
					totalOpen += solver.open.size();
				} catch (OutOfMemoryError e) {
					timer.close();
					print("exception after %s\n", timer);
					print("%s\n", e);
					e.printStackTrace();
					unsolved += 1;
				} finally {
					print("END %s\n", level.name);
					print("\n");
				}

				System.gc();
				Thread.sleep(10);
				System.gc();
				Thread.sleep(10);
				System.gc();
				Thread.sleep(10);
			}
		print("solved %s, unsolved %s, DIST %s, CLOSED %s, OPEN %s\n", solved, unsolved, totalDist, totalClosed,
				totalOpen);
		print("snapshot [52.729 Nightly:58 I solved 390, unsolved 0, DIST 40830, CLOSED 20262253, OPEN 438244\n");
	}
}