package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

public class StateTest {
	@Test
	public void move_macro() {
		Level level = new Level("test:8");
		level.print(level.start);
		int a = level.start.agent();
		State s = level.start.move(Level.Down, level);
		// TODO
		//Assert.assertEquals(a + 4, s.agent());
		//Assert.assertEquals(8, s.dist());
		//Assert.assertEquals(Level.Up, s.dir);

		State m = s.prev(level);
		Assert.assertEquals(a, m.agent());
		Assert.assertEquals(0, m.dist());
		Assert.assertEquals(-1, m.dir);
	}

	@Test
	public void push_macro_simple() {
		Level level = new Level("test:9");
		level.print(level.start);
		int a = level.start.agent();
		State s = level.start.move(Level.Right, level);
		// TODO
		//Assert.assertEquals(a + 4, s.agent());
		//Assert.assertEquals(4, s.dist());
		Assert.assertEquals(Level.Right, s.dir);

		State m = s.prev(level);
		Assert.assertEquals(0, m.dist());
		Assert.assertEquals(-1, m.dir);
		Assert.assertEquals(a, m.agent());
	}
}