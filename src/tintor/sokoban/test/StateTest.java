package tintor.sokoban.test;

import org.junit.Test;

import tintor.common.Array;
import tintor.sokoban.Cell.Dir;
import tintor.sokoban.Level;

public class StateTest {
	@Test
	public void move_macro() {
		assertMove(Dir.Down, 8, "test:8", Dir.Up);
	}

	@Test
	public void push_macro_simple() {
		assertMove(Dir.Right, 5, "test:9");
	}

	@Test
	public void push_macro_box_on_degree3_bottleneck() {
		assertMove(Dir.Up, 4, "test:10");
	}

	@Test
	public void push_macro_box_on_goal_bottleneck_tunnel() {
		assert Array.find(Level.load("test:11").cells, c -> c.xy == 11).bottleneck;
		assertMove(Dir.Right, 3, "test:11");
	}

	void assertMove(Dir dir, int steps, String filename) {
		assertMove(dir, steps, filename, dir);
	}

	void assertMove(Dir dir, int steps, String filename, Dir exitDir) {
		Level level = Level.load(filename);

		/*State s = level.start.move(dir, level, false);
		Assert.assertEquals(steps, s.dist());
		Assert.assertEquals(exitDir, s.dir);
		
		State m = s.prev(level);
		Assert.assertEquals(0, m.dist());
		Assert.assertEquals(-1, m.dir);
		Assert.assertTrue(level.start.equals(m));*/
	}
}