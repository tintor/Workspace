package tintor.sokoban;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import tintor.common.ParallelParameterized;

@RunWith(ParallelParameterized.class)
public class SolverTest {
	static ArrayList<Object[]> tests = new ArrayList<Object[]>();

	static void test(String filename, int expected_dist) {
		// don't run slow tests
		if (expected_dist <= 0)
			return;
		tests.add(new Object[] { filename, expected_dist });
	}

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		test("microban1:98", 269);
		test("microban1:117", 178);
		test("microban1:1", 33);
		test("microban1:2", 16);
		test("microban1:3", 41);
		test("microban1:4", 23);
		test("microban1:5", 25);
		test("microban1:6", 107);
		test("microban1:7", 26);
		test("microban1:8", 97);
		test("microban1:9", 30);
		test("microban1:10", 89);
		test("microban1:11", 78);
		test("microban1:12", 49);
		test("microban1:13", 52);
		test("microban1:14", 51);
		test("microban1:15", 37);
		test("microban1:16", 100);
		test("microban1:17", 25);
		test("microban1:18", 71);
		test("microban1:19", 41);
		test("microban1:20", 50);
		test("microban1:21", 17);
		test("microban1:22", 47);
		test("microban1:23", 56);
		test("microban1:24", 35);
		test("microban1:25", 29);
		test("microban1:26", 41);
		test("microban1:27", 50);
		test("microban1:28", 33);
		test("microban1:29", 104);
		test("microban1:30", 21);
		test("microban1:31", 17);
		test("microban1:32", 35);
		test("microban1:33", 41);
		test("microban1:34", 30);
		test("microban1:35", 77);
		test("microban1:36", 156);
		test("microban1:37", 71);
		test("microban1:38", 37);
		test("microban1:39", 85);
		test("microban1:40", 20);
		test("microban1:41", 50);
		test("microban1:42", 47);
		test("microban1:43", 61);
		test("microban1:44", 1);
		test("microban1:45", 45);
		test("microban1:46", 47);
		test("microban1:47", 83);
		test("microban1:48", 64);
		test("microban1:49", 82);
		test("microban1:50", 76);
		test("microban1:51", 34);
		test("microban1:52", 26);
		test("microban1:53", 37);
		test("microban1:54", 82);
		test("microban1:55", 64);
		test("microban1:56", 23);
		test("microban1:57", 60);
		test("microban1:58", 44);
		test("microban1:59", 178);
		test("microban1:60", 169);
		test("microban1:61", 100);
		test("microban1:62", 64);
		test("microban1:63", 101);
		test("microban1:64", 95);
		test("microban1:65", 138);
		test("microban1:66", 69);
		test("microban1:67", 37);
		test("microban1:68", 98);
		test("microban1:69", 125);
		test("microban1:70", 78);
		test("microban1:71", 120);
		test("microban1:72", 105);
		test("microban1:73", 102);
		test("microban1:74", 117);
		test("microban1:75", 92);
		test("microban1:76", 181);
		test("microban1:77", 189);
		test("microban1:78", 135);
		test("microban1:79", 48);
		test("microban1:80", 131);
		test("microban1:81", 46);
		test("microban1:82", 52);
		test("microban1:83", 164);
		test("microban1:84", 201);
		test("microban1:85", 155);
		test("microban1:86", 105);
		test("microban1:87", 149);
		test("microban1:88", 195);
		test("microban1:89", 146);
		test("microban1:90", 64);
		test("microban1:91", 45);
		test("microban1:92", 126);
		test("microban1:93", -1);
		test("microban1:94", 83);
		test("microban1:95", 25);
		test("microban1:96", 92);
		test("microban1:97", 164);
		test("microban1:99", -349);
		test("microban1:100", 155);
		test("microban1:101", 79);
		test("microban1:102", 149);
		test("microban1:103", 35);
		test("microban1:104", 79);
		test("microban1:106", 205);
		test("microban1:107", 38);
		test("microban1:108", 238);
		test("microban1:109", 177);
		test("microban1:110", 51);
		test("microban1:111", -166);
		test("microban1:112", -261);
		test("microban1:113", 162);
		test("microban1:114", -227);
		test("microban1:115", -110);
		test("microban1:116", 63);
		test("microban1:118", 172);
		test("microban1:119", 131);
		test("microban1:120", 183);
		test("microban1:121", 125);
		test("microban1:122", -245);
		test("microban1:123", -296);
		test("microban1:124", 245);
		test("microban1:125", 125);
		test("microban1:126", -87);
		test("microban1:127", 106);
		test("microban1:128", 88);
		test("microban1:129", 99);
		test("microban1:130", -102);
		test("microban1:131", 76);
		test("microban1:132", 155);
		test("microban1:133", -155);
		test("microban1:134", 244);
		test("microban1:135", 135);
		test("microban1:136", -134);
		test("microban1:137", 177);
		test("microban1:138", -193);
		test("microban1:139", -335);
		test("microban1:140", 290);
		test("microban1:141", 134);
		test("microban1:142", 76);
		test("microban1:143", -212);
		test("microban1:144", 0);
		test("microban1:145", 0);
		test("microban1:146", 0);
		test("microban1:147", 146);
		test("microban1:148", 197);
		test("microban1:149", 94);
		test("microban1:150", -135);
		test("microban1:151", -125);
		test("microban1:152", -233);
		test("microban1:153", -1);
		test("microban1:154", -429);
		//test("microban1:155", 282); // >128 alive cells (before tunnel compaction)
		return tests;
	}

	@Parameter(0)
	public String filename;
	@Parameter(1)
	public int expected;

	@Test(timeout = 12000)
	public void solve() {
		Level level = Level.load(filename);
		State[] solution = Solver.solve_Astar(level, false);
		Assert.assertTrue(solution != null);
		// Assert.assertEquals(expected, solution[solution.length - 1].dist());
	}
}