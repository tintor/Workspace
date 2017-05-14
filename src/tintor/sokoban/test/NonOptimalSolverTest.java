package tintor.sokoban.test;

import org.junit.Assert;
import org.junit.Test;

import tintor.sokoban.AStarSolver;
import tintor.sokoban.Level;
import tintor.sokoban.State;

public class NonOptimalSolverTest {
	@Test
	public void solve_test_1() {
		solve("test:1");
	}

	@Test
	public void solve_original_1() {
		solve("original:1");
	}

	@Test
	public void solve_original_2() {
		solve("original:2");
	}

	@Test
	public void solve_original_3() {
		solve("original:3");
	}

	@Test
	public void solve_original_6() {
		solve("original:6");
	}

	@Test
	public void solve_original_7() {
		solve("original:7");
	}

	@Test
	public void solve_original_8() {
		solve("original:8");
	}

	@Test
	public void solve_original_17() {
		solve("original:17");
	}

	private void solve(String filename) {
		Level level = Level.load(filename);
		AStarSolver solver = new AStarSolver(level, false);
		State end = solver.solve();
		Assert.assertTrue(end != null);
	}
}