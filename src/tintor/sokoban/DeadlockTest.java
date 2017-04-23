package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

public class DeadlockTest {
	@Test
	public void test() {
		Level level = Level.load("frozen_boxes.test");
		Deadlock d = new Deadlock(level);
		Assert.assertTrue(d.check(level.start));
		Assert.assertEquals(1, d.patterns);
		Assert.assertTrue(d.check(level.start)); // second check will match a pattern
	}

	@Test
	public void deadlockWithBoxesFrozenOnGoal() {
		Level level = Level.load("test:12");
		Deadlock d = new Deadlock(level);
		Assert.assertTrue(d.check(level.start));
	}
}