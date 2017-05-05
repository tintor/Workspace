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
	static ArrayList<Object> tests = new ArrayList<Object>();

	static void load(String name, int levels) {
		for (int i = 1; i <= levels; i++)
			tests.add(name + ":" + i);
	}

	@Parameters(name = "{0}")
	public static Collection<Object> data() {
		load("original", 90);

		load("microban1", 155);
		load("microban2", 135);
		load("microban3", 101);
		load("microban4", 102);
		load("microban5", 26);

		for (int j = 1; j <= 11; j++)
			load("sasquatch_" + j, 50);
		return tests;
	}

	@Parameter
	public String filename;

	@Test(timeout = 800)
	public void solve() {
		try {
			Level.load(filename);
		} catch (Level.MoreThan256CellsError e) {
		}
	}
}