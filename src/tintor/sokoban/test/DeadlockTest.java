package tintor.sokoban.test;

import org.junit.Assert;
import org.junit.Test;

import tintor.sokoban.Deadlock;
import tintor.sokoban.Level;

public class DeadlockTest {
	@Test
	public void test() {
		Level level = Level.load("frozen_boxes.test");
		Deadlock d = new Deadlock(level);
		Assert.assertTrue(d.checkFull(level.start));
		Assert.assertEquals(1, d.patternIndex.size());
		Assert.assertTrue(d.checkFull(level.start)); // second check will match a pattern
	}

	@Test
	public void deadlockWithBoxesFrozenOnGoal() {
		Level level = Level.load("test:12");
		Deadlock d = new Deadlock(level);
		Assert.assertTrue(d.checkFull(level.start));
	}
}