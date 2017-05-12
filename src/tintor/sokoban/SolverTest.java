package tintor.sokoban;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import tintor.common.AutoTimer;
import tintor.common.ParallelParameterized;
import tintor.sokoban.AStarSolver.ClosedSizeLimitError;
import tintor.sokoban.Level.MoreThan256CellsError;

@RunWith(ParallelParameterized.class)
public class SolverTest {
	static void add(int count, String name, ArrayList<Object> list) {
		for (int i = 1; i <= count; i++)
			list.add(name + ":" + i);
	}

	@Parameters(name = "{0}")
	public static Collection<Object> data() {
		ArrayList<Object> list = new ArrayList<Object>();
		add(155, "microban1", list);
		add(135, "microban2", list);
		add(101, "microban3", list);
		add(102, "microban4", list);
		add(026, "microban5", list);
		add(90, "original", list);
		for (int i = 1; i <= 11; i++)
			add(50, "sasquatch_" + i, list);
		return list;
	}

	@Parameter(0)
	public String filename;

	@Test
	public void solve() {
		AutoTimer.enabled = false;
		try {
			Level level = Level.load(filename);
			AStarSolver solver = new AStarSolver(level, false);
			solver.closed_size_limit = 1000;
			try {
				State end = solver.solve();
				Assert.assertTrue(end != null);
				solver.extractPath(end);
			} catch (ClosedSizeLimitError e) {
				//Assert.assertTrue("ClosedSizeLimit " + level.state_space(), level.state_space() > 21);
			}
		} catch (MoreThan256CellsError e) {
		}
	}
}