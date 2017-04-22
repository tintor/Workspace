package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

import tintor.sokoban.Solver.Context;

public class NonOptimalSolverTest {
	@Test(timeout = 1500)
	public void solve_test_1() {
		solve("test:1", 550);
	}

	@Test(timeout = 1500)
	public void solve_original_2() {
		solve("original:2", 492);
	}

	private void solve(String filename, int expected) {
		Level level = new Level(filename);
		Context context = new Context();
		context.optimal_macro_moves = false;
		context.greedy_score = true;
		State[] solution = Solver.solve_Astar(level, level.start, new Heuristic(level), new Deadlock(level), context);
		Assert.assertTrue(solution != null);
		Assert.assertEquals(expected, solution[solution.length - 1].dist());
	}
}