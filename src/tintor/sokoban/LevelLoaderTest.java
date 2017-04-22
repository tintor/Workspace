package tintor.sokoban;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import tintor.common.ParallelParameterized;

// Makes sure all levels can load quickly
@RunWith(ParallelParameterized.class)
public class LevelLoaderTest {
	@Parameters(name = "{0}")
	public static Collection<Object> data() {
		ArrayList<Object> tests = new ArrayList<Object>();
		for (int i = 1; i <= 155; i++)
			tests.add("microban:" + i);
		for (int i = 1; i <= 90; i++)
			tests.add("original:" + i);
		for (int j = 1; j <= 11; j++)
			for (int i = 1; i <= 50; i++)
				tests.add("sasquatch_" + j + ":" + i);
		return tests;
	}

	@Parameter
	public String filename;

	@Test(timeout = 1200)
	public void solve() {
		try {
			new Level(filename);
		} catch (Level.MoreThan128AliveCellsError e) {
		}
	}
}