package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

public class StateTest {
	@Test
	public void move_macro() {
		assertMove(Level.Down, 8, "test:8", Level.Up);
	}

	@Test
	public void push_macro_simple() {
		assertMove(Level.Right, 5, "test:9");
	}

	@Test
	public void push_macro_box_on_degree3_bottleneck() {
		assertMove(Level.Up, 4, "test:10");
	}

	@Test
	public void push_macro_box_on_goal_bottleneck_tunnel() {
		assert Level.load("test:11").bottleneck[1];
		assertMove(Level.Right, 3, "test:11");
	}

	void assertMove(int dir, int steps, String filename) {
		assertMove(dir, steps, filename, dir);
	}

	void assertMove(int dir, int steps, String filename, int exitDir) {
		Level level = Level.load(filename);

		State s = level.start.move(dir, level, false);
		Assert.assertEquals(steps, s.dist());
		Assert.assertEquals(exitDir, s.dir);

		State m = s.prev(level);
		Assert.assertEquals(0, m.dist());
		Assert.assertEquals(-1, m.dir);
		Assert.assertTrue(level.start.equals(m));
	}
}