package tintor.sokoban.test;

import java.util.ArrayList;

import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;
import tintor.sokoban.AStarSolver;
import tintor.sokoban.CellLevel;
import tintor.sokoban.State;

// Run all Microban levels up to a certain complexity, one at a time
public class Microban {
	static Timer timer = new Timer();
	static int solved = 0, unsolved = 0;

	public static void main(String[] args) {
		long totalDist = 0, totalClosed = 0, totalOpen = 0;
		ArrayList<CellLevel> levels = CellLevel.loadAll("microban1");
		levels.addAll(CellLevel.loadAll("microban2"));
		levels.addAll(CellLevel.loadAll("microban3"));
		levels.addAll(CellLevel.loadAll("microban4"));
		levels.addAll(CellLevel.loadAll("microban5"));
		@SuppressWarnings("unchecked")
		ArrayList<CellLevel>[] space = new ArrayList[110];
		for (CellLevel level : levels) {
			int s = level.state_space();
			if (space[s] == null)
				space[s] = new ArrayList<>();
			space[s].add(level);
		}

		for (int i = 0; i < space.length; i++)
			if (space[i] != null)
				for (CellLevel level : space[i]) {
					try {
						if (level.state_space() >= 30)
							break;
						Log.raw("%s cells:%d alive:%d boxes:%d state_space:%s", level.name, level.cells.length,
								level.alive, level.num_boxes, level.state_space());
						AStarSolver solver = new AStarSolver(level, false);
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
					Util.sleep(10);
					System.gc();
					Util.sleep(10);
					System.gc();
					Util.sleep(10);
				}
		Log.raw("solved %d, unsolved %d, DIST %d, CLOSED %d, OPEN %d", solved, unsolved, totalDist, totalClosed,
				totalOpen);
		Log.raw("snapshot [52.729 Nightly:58 I solved 390, unsolved 0, DIST 40830, CLOSED 20262253, OPEN 438244");
	}
}