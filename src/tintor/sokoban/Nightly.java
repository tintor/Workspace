package tintor.sokoban;

import org.junit.Assert;

import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;

public class Nightly {
	static Timer timer = new Timer();

	public static void main(String[] args) {
		int solved = 0;
		for (int i = 1; i <= 90; i++) {
			try {
				Level level = new Level("original:" + i);
				Log.info("original:%d cells:%d alive:%d boxes:%d state_space:%s", i, level.cells, level.alive,
						level.num_boxes, level.state_space());
				Deadlock deadlock = new Deadlock(level);
				Solver.Context context = new Solver.Context();
				context.trace = 1;

				timer.start();
				State[] solution = Solver.solve_Astar(level, level.start, new Heuristic(level), deadlock, context);
				timer.stop();
				if (solution == null) {
					Log.info("no solution! %s", timer.human());
				} else {
					solved += 1;
					Log.info("solved in %d steps! %s", solution.length, timer.human());
				}
				Log.info("closed:%s open:%s patterns:%s", context.closed_set_size, context.open_set_size,
						Util.human(deadlock.patterns));
			} catch (Exception e) {
				Log.info("exception after %s", timer.human());
				System.out.println(e);
			}
			timer.total = 0;
		}
		Log.info("Solved %d out of 90 problems!", solved);
	}

	public static void mainz(String[] args) {
		for (Object[] params : SolverTest.data()) {
			String filename = (String) params[0];
			int expected = (int) params[1];

			try {
				Level level = new Level(filename);
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
			Level level = new Level("original:" + n);
			Log.info("%d cells:%d alive:%d boxes:%d state_space:%s\n", n, level.cells, level.alive, level.num_boxes,
					level.state_space());
		}
	}
}