package tintor.sokoban.test;

import org.junit.Assert;
import org.junit.Test;

import tintor.sokoban.CellLevel;
import tintor.sokoban.Deadlock;

public class DeadlockTest {
	@Test
	public void test() {
		CellLevel level = CellLevel.load("frozen_boxes.test");
		Deadlock d = new Deadlock(level);
		Assert.assertTrue(d.checkFull(level.start));
		Assert.assertEquals(1, d.patternIndex.size());
		Assert.assertTrue(d.checkFull(level.start)); // second check will match a pattern
	}

	@Test
	public void deadlockWithBoxesFrozenOnGoal() {
		CellLevel level = CellLevel.load("test:12");
		Deadlock d = new Deadlock(level);
		Assert.assertTrue(d.checkFull(level.start));
	}
}