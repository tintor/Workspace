package tintor.sokoban;

import org.junit.Assert;

import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;

public class Nightly {
	static Timer timer = new Timer();

	public static void main(String[] args) {
		Log.info("Hello!");
		for (Object[] params : SolverTest.data()) {
			String filename = (String) params[0];
			int expected = (int) params[1];

			try {
				Level level = new Level("data/" + filename);
				Model model = new MatchingModel();
				model.init(level);
				int h = model.evaluate(level.start, null);
				if (h > Short.MAX_VALUE)
					throw new Error();
				level.start.total_dist = (short) h;
				Deadlock deadlock = new Deadlock(level);

				timer.start();
				State[] solution = Solver.solve_Astar(level, level.start, model, deadlock, new Solver.Context());
				timer.stop();

				Assert.assertTrue(solution != null);
				Assert.assertEquals(expected, solution.length);
			} catch (Exception e) {
				System.out.println(e);
			}
			Log.info("%s %s", filename, timer.human());
		}
	}

	public static void main2(String[] args) {
		for (int n = 1; n <= 90; n++) {
			Level level = new Level("data/original:" + n);
			System.out.printf("%d cells:%d alive:%d boxes:%d state_space:%s\n", n, level.cells, level.alive,
					Util.count(level.start.box), level.state_space());
		}
	}
}