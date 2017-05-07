package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

public class NonOptimalSolverTest {
	@Test
	public void solve_test_1() {
		solve("test:1", 550);
	}

	@Test
	public void solve_original_2() {
		solve("original:2", 492);
	}

	private void solve(String filename, int expected) {
		Level level = Level.load(filename);
		AStarSolver solver = new AStarSolver(level, false);
		State end = solver.solve();
		Assert.assertTrue(end != null);
		Assert.assertTrue(expected * 1.25 >= end.dist);
	}
}