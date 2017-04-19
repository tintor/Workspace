package tintor.sokoban;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import tintor.common.ParallelParametrized;

@RunWith(ParallelParametrized.class)
public class SolverTest {
	static ArrayList<Object[]> tests = new ArrayList<Object[]>();

	static void test(String filename, int expected_dist) {
		if (expected_dist == -1)
			return;
		tests.add(new Object[] { filename, expected_dist });
	}

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		test("microban:1", 33);
		test("microban:2", 16);
		test("microban:3", 41);
		test("microban:4", 23);
		test("microban:5", 25);
		test("microban:6", 107);
		test("microban:7", 26);
		test("microban:8", 97);
		test("microban:9", 30);
		test("microban:10", 89);
		test("microban:11", 78);
		test("microban:12", 49);
		test("microban:13", 52);
		test("microban:14", 51);
		test("microban:15", 37);
		test("microban:16", 100);
		test("microban:17", 25);
		test("microban:18", 71);
		test("microban:19", 41);
		test("microban:20", 50);
		test("microban:21", 17);
		test("microban:22", 47);
		test("microban:23", 56);
		test("microban:24", 35);
		test("microban:25", 29);
		test("microban:26", 41);
		test("microban:27", 50);
		test("microban:28", 33);
		test("microban:29", 104);
		test("microban:30", 21);
		test("microban:31", 17);
		test("microban:32", 35);
		test("microban:33", 41);
		test("microban:34", 30);
		test("microban:35", 77);
		test("microban:36", 156);
		test("microban:37", 71);
		test("microban:38", 37);
		test("microban:39", 85);
		test("microban:40", 20);
		test("microban:41", 50);
		test("microban:42", 47);
		test("microban:43", 61);
		test("microban:44", 1);
		test("microban:45", 45);
		test("microban:46", 47);
		test("microban:47", 83);
		test("microban:48", 64);
		test("microban:49", 82);
		test("microban:50", 76);
		test("microban:51", 34);
		test("microban:52", 26);
		test("microban:53", 37);
		test("microban:54", 82);
		test("microban:55", 64);
		test("microban:56", 23);
		test("microban:57", 60);
		test("microban:58", 44);
		test("microban:59", 178);
		test("microban:60", 169);
		test("microban:61", 100);
		test("microban:62", 64);
		test("microban:63", 101);
		test("microban:64", 95);
		test("microban:65", 138);
		test("microban:66", 69);
		test("microban:67", 37);
		test("microban:68", 98);
		test("microban:69", 125);
		test("microban:70", 78);
		test("microban:71", 120);
		test("microban:72", 105);
		test("microban:73", 102);
		test("microban:74", 117);
		test("microban:75", 92);
		test("microban:76", 181);
		test("microban:77", 189);
		test("microban:78", 135);
		test("microban:79", 48);
		test("microban:80", 131);
		test("microban:81", 46);
		test("microban:82", 52);
		test("microban:83", 164);
		test("microban:84", 201);
		test("microban:85", 155);
		test("microban:86", 105);
		test("microban:87", 149);
		test("microban:88", 195);
		test("microban:89", 146);
		test("microban:90", 64);
		test("microban:91", 45);
		test("microban:92", 126);
		test("microban:93", -1);
		test("microban:94", 83);
		test("microban:95", 25);
		test("microban:96", 92);
		test("microban:97", 164);
		test("microban:98", 269);
		test("microban:99", 349);
		test("microban:100", 155);
		test("microban:101", 79);
		test("microban:102", 149);
		test("microban:103", 35);
		test("microban:104", 79);
		test("microban:106", 205);
		test("microban:107", 38);
		test("microban:108", 238);
		test("microban:109", 177);
		test("microban:110", 51);
		test("microban:111", 166);
		test("microban:112", 261);
		test("microban:113", 162);
		test("microban:114", 227);
		test("microban:115", 110);
		test("microban:116", 63);
		test("microban:117", 178);
		test("microban:118", 172);
		test("microban:119", 131);
		test("microban:120", 183);
		test("microban:121", 125);
		test("microban:122", 245);
		test("microban:123", 296);
		test("microban:124", 245);
		test("microban:125", 125);
		test("microban:126", 87);
		test("microban:127", 106);
		test("microban:128", 88);
		test("microban:129", 99);
		test("microban:130", 102);
		test("microban:131", 76);
		test("microban:132", 155);
		test("microban:133", 155);
		test("microban:134", 244);
		test("microban:135", 135);
		test("microban:136", 134);
		test("microban:137", 177);
		test("microban:138", 193);
		test("microban:139", 335);
		test("microban:140", 290);
		test("microban:141", 134);
		test("microban:142", 76);
		test("microban:143", 212);
		test("microban:144", -1);
		test("microban:145", -1);
		test("microban:146", -1);
		test("microban:147", 146);
		test("microban:148", 197);
		test("microban:149", 94);
		test("microban:150", 135);
		test("microban:151", 125);
		test("microban:152", 233);
		test("microban:153", -1);
		test("microban:154", 429);
		test("microban:155", 282);
		return tests;
	}

	@Parameter(0)
	public String filename;
	@Parameter(1)
	public int expected;

	@Test
	public void solve() {
		solve(new MatchingModel());
	}

	void solve(Model model) {
		Level level = new Level("data/sokoban/" + filename);
		model.init(level);
		int h = model.evaluate(level.start, null);
		if (h > Short.MAX_VALUE)
			throw new Error();
		level.start.total_dist = (short) h;
		Deadlock deadlock = new Deadlock(level);

		State[] solution = Solver.solve_Astar(level, level.start, model, deadlock, new Solver.Context());
		Assert.assertTrue(solution != null);
		Assert.assertEquals(expected, solution.length);
	}
}