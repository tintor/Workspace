package tintor.sokoban.test;

import org.junit.Assert;
import org.junit.Test;

import tintor.common.Array;
import tintor.common.Util;
import tintor.sokoban.CellLevel;

public class LevelTest {
	@Test
	public void unreachableFrozenBoxesOnGoals() {
		CellLevel level = CellLevel.load("test:3");
		Assert.assertEquals(1, level.num_boxes);
		Assert.assertEquals(2, level.alive);
		Assert.assertEquals(3, level.cells);
	}

	@Test
	public void removeUselessAliveCells() {
		Assert.assertEquals(9, CellLevel.load("test:6").alive);
		Assert.assertEquals(11, CellLevel.load("test:7").alive);
	}

	@Test
	public void bottleneck() {
		CellLevel level = CellLevel.load("test:5");
		boolean[] bottleneck = Array.ofBool(level.cells.length, i -> level.cells[i].bottleneck);
		Assert.assertEquals(0x3E, Util.compress(bottleneck));
	}

	@Test
	public void fail() {
		boolean assertOn = false;
		assert assertOn = true;
		Assert.assertTrue(assertOn);
	}
}