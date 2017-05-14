package tintor.sokoban.test;

import java.util.ArrayList;

import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;
import tintor.sokoban.AStarSolver;
import tintor.sokoban.Level;
import tintor.sokoban.State;

// Run all Original levels up to a certain complexity, one at a time
public class Original {
	static Timer timer = new Timer();
	static int solved = 0, unsolved = 0;

	public static void main(String[] args) {
		long totalDist = 0, totalClosed = 0, totalOpen = 0;
		ArrayList<Level> levels = Level.loadAll("original");
		@SuppressWarnings("unchecked")
		ArrayList<Level>[] space = new ArrayList[110];
		for (Level level : levels) {
			int s = level.state_space();
			if (space[s] == null)
				space[s] = new ArrayList<Level>();
			space[s].add(level);
		}

		//for (int i = 0; i < space.length; i++)
		//if (space[i] != null)
		for (Level level : levels) {
			try {
				Log.raw("%s cells:%d alive:%d boxes:%d state_space:%s", level.low.name, level.cells, level.alive,
						level.num_boxes, level.state_space());
				AStarSolver solver = new AStarSolver(level, false);
				solver.trace = 2;
				solver.closed_size_limit = 100000;
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
			} catch (AStarSolver.ClosedSizeLimitError e) {
				timer.stop();
				Log.raw("out of closed nodes after %s", timer.human());
				System.out.println(e);
				System.out.flush();
				e.printStackTrace();
				unsolved += 1;
			} catch (OutOfMemoryError e) {
				timer.stop();
				Log.raw("out of memory after %s", timer.human());
				System.out.println(e);
				System.out.flush();
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
	}
}