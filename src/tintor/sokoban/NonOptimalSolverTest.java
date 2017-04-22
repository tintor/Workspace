package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

import tintor.sokoban.Solver.Context;

public class NonOptimalSolverTest {
	@Test(timeout = 1500)
	public void solve() {
		Level level = new Level("test:1");
		Context context = new Context();
		context.optimal_macro_moves = false;
		context.greedy_score = true;
		State[] solution = Solver.solve_Astar(level, level.start, new Heuristic(level), new Deadlock(level), context);
		Assert.assertTrue(solution != null);
		Assert.assertEquals(550, solution[solution.length - 1].dist());
	}
}