package tintor.sokoban;

import org.junit.Assert;

public class NonOptimalSolverTest {
	//@Test(timeout = 3000)
	public void solve_test_1() {
		solve("test:1", 550);
	}

	//@Test(timeout = 6000)
	public void solve_original_2() {
		solve("original:2", 492);
	}

	private void solve(String filename, int expected) {
		Level level = Level.load(filename);
		AStarSolver solver = new AStarSolver(level);
		State end = solver.solve();
		Assert.assertTrue(end != null);
		Assert.assertEquals(expected, end.dist());
	}
}