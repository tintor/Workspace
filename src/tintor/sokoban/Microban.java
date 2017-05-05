package tintor.sokoban;

import java.util.ArrayList;

import tintor.common.AutoTimer;
import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;

// [20, 23]: 105.5s for 68 levels (with AreAllGoalsReachable.run)
// [20, 23]: 57.1s for 68 levels

// [27]: 12s for 5 levels
// [28]: 41s for 10 levels
// [29]: 123s for 6 levels
// [30]: 138s for 8 levels

// TODO: print cumulative timer breakdown for all levels

// Run all Microban levels up to a certain complexity, one at a time
public class Microban {
	static Timer timer = new Timer();
	static int solved = 0, unsolved = 0;

	public static void main(String[] args) {
		long totalDist = 0, totalClosed = 0, totalOpen = 0;
		ArrayList<Level> levels = Level.loadAll("microban1");
		levels.addAll(Level.loadAll("microban2"));
		levels.addAll(Level.loadAll("microban3"));
		levels.addAll(Level.loadAll("microban4"));
		levels.addAll(Level.loadAll("microban5"));
		@SuppressWarnings("unchecked")
		ArrayList<Level>[] space = new ArrayList[110];
		for (Level level : levels) {
			int s = level.state_space();
			if (space[s] == null)
				space[s] = new ArrayList<Level>();
			space[s].add(level);
		}

		for (int i = 31; i < space.length; i++)
			if (space[i] != null)
				for (Level level : space[i])
					try {
						AutoTimer.reset();
						Log.raw("%s cells:%d alive:%d boxes:%d state_space:%s", level.low.name, level.cells,
								level.alive, level.num_boxes, level.state_space());
						AStarSolver solver = new AStarSolver(level);
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
							Log.raw("solved in %d steps! %s", end.dist(), timer.human());
							totalDist += end.dist();
						}
						totalClosed += solver.closed.size();
						totalOpen += solver.open.size();
						Log.raw("closed:%s open:%s patterns:%s", solver.closed.size(), solver.open.size(),
								Util.human(solver.deadlock.patterns));
						AutoTimer.report();
						Log.raw("");
					} catch (OutOfMemoryError e) {
						Log.raw("exception after %s", timer.human());
						System.out.println(e);
						e.printStackTrace();
						unsolved += 1;
					}
		Log.raw("solved %d, unsolved %d, DIST %d, CLOSED %d, OPEN %d", solved, unsolved, totalDist, totalClosed,
				totalOpen);
		Log.raw("snapshot [52.729 Nightly:58 I solved 390, unsolved 0, DIST 40830, CLOSED 20262253, OPEN 438244");
	}
}