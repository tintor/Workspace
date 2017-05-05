package tintor.sokoban;

import java.util.ArrayList;

import org.junit.Assert;

import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;

public class Nightly {
	static Timer timer = new Timer();
	static int solved = 0, unsolved = 0;

	public static void main(String[] args) {
		long totalDist = 0, totalClosed = 0, totalOpen = 0;
		ArrayList<Level> levels = Level.loadAll("microban1");
		levels.addAll(Level.loadAll("microban2"));
		levels.addAll(Level.loadAll("microban3"));
		levels.addAll(Level.loadAll("microban4"));
		levels.addAll(Level.loadAll("microban5"));
		//ArrayList<Level> levels = Level.loadAll("original");

		for (Level level : levels)
			try {
				if (level.state_space() > 23)
					continue;
				Log.info("%s cells:%d alive:%d boxes:%d state_space:%s", level.low.name, level.cells, level.alive,
						level.num_boxes, level.state_space());
				Deadlock deadlock = new Deadlock(level);
				Solver.Context context = new Solver.Context();
				context.trace = 0;
				context.optimal_macro_moves = false;

				timer.total = 0;
				timer.start();
				State[] solution = Solver.solve_Astar(level, level.start, new Heuristic(level), deadlock, context);
				timer.stop();
				if (solution == null) {
					unsolved += 1;
					Log.info("no solution! %s", timer.human());
				} else {
					solved += 1;
					Log.info("solved in %d steps! %s", solution[solution.length - 1].dist(), timer.human());
					totalDist += solution[solution.length - 1].dist();
				}
				totalClosed += context.closed_set_size;
				totalOpen += context.open_set_size;
				Log.info("closed:%s open:%s patterns:%s", context.closed_set_size, context.open_set_size,
						Util.human(deadlock.patterns));
			} catch (OutOfMemoryError e) {
				Log.info("exception after %s", timer.human());
				System.out.println(e);
				e.printStackTrace();
				unsolved += 1;
			}
		Log.info("solved %d, unsolved %d, DIST %d, CLOSED %d, OPEN %d", solved, unsolved, totalDist, totalClosed,
				totalOpen);
		Log.info("snapshot [52.729 Nightly:58 I solved 390, unsolved 0, DIST 40830, CLOSED 20262253, OPEN 438244");
	}

	public static void mainz(String[] args) {
		for (Object[] params : SolverTest.data()) {
			String filename = (String) params[0];
			int expected = (int) params[1];

			try {
				Level level = Level.load(filename);
				timer.start();
				State[] solution = Solver.solve_Astar(level, false);
				timer.stop();
				Assert.assertTrue(solution != null);
				Assert.assertEquals(expected, solution.length);
			} catch (Exception e) {
				System.out.println(e);
			}
			Log.info("%s %s", filename, timer.total / 1000000);
			timer.total = 0;
		}
	}

	public static void main2(String[] args) {
		for (int n = 1; n <= 90; n++) {
			Level level = Level.load("original:" + n);
			Log.info("%d cells:%d alive:%d boxes:%d state_space:%s\n", n, level.cells, level.alive, level.num_boxes,
					level.state_space());
		}
	}
}