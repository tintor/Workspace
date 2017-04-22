package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

import tintor.common.Util;

public class LevelTest {
	@Test
	public void unreachableFrozenBoxesOnGoals() {
		Level level = new Level("test:3");
		Assert.assertEquals(1, level.num_boxes);
		Assert.assertEquals(2, level.alive);
		Assert.assertEquals(3, level.cells);
	}

	@Test
	public void removeUselessAliveCells() {
		Assert.assertEquals(9, new Level("test:6").alive);
		Assert.assertEquals(11, new Level("test:7").alive);
	}

	@Test
	public void bottleneck() {
		Level level = new Level("test:5");
		Assert.assertEquals(0x3E, Util.compress(level.bottleneck));
	}

	@Test
	public void fail() {
		boolean assertOn = false;
		assert assertOn = true;
		Assert.assertTrue(assertOn);
	}
}