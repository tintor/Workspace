package tintor.sokoban;

import org.junit.Assert;

import tintor.common.Log;
import tintor.common.Timer;

public class Nightly {
	static Timer timer = new Timer();

	public static void main(String[] args) {
		for (Object[] params : SolverTest.data()) {
			String filename = (String) params[0];
			int expected = (int) params[1];

			try {
				Level level = new Level("data/sokoban/" + filename);
				Heuristic model = new MatchingHeuristic();
				model.init(level);
				level.start.set_heuristic(model.evaluate(level.start, null));
				Deadlock deadlock = new Deadlock(level);

				timer.start();
				StateBase[] solution = Solver.solve_Astar(level, level.start, model, deadlock, new Solver.Context());
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
			Level level = new Level("data/sokoban/original:" + n);
			System.out.printf("%d cells:%d alive:%d boxes:%d state_space:%s\n", n, level.cells, level.alive,
					level.num_boxes, level.state_space());
		}
	}
}