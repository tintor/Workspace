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

	public int scan(String prefix, int levels) {
		int errors = 0;
		for (int i = 1; i <= levels; i++)
			try {
				new Level(prefix + i);
			} catch (Level.MoreThan128AliveCellsError e) {
				errors += 1;
			}
		return errors;
	}

	@Test
	public void loadLevels1() {
		Assert.assertEquals(1, scan("microban:", 155));
		Assert.assertEquals(1, scan("original:", 90));
	}

	@Test
	public void loadLevels2() {
		Assert.assertEquals(6, scan("sasquatch_1:", 50));
		Assert.assertEquals(10, scan("sasquatch_2:", 50));
		Assert.assertEquals(12, scan("sasquatch_3:", 50));
	}

	@Test
	public void loadLevels3() {
		Assert.assertEquals(7, scan("sasquatch_4:", 50));
		Assert.assertEquals(9, scan("sasquatch_5:", 50));
		Assert.assertEquals(12, scan("sasquatch_6:", 50));
		Assert.assertEquals(11, scan("sasquatch_7:", 50));
	}

	@Test
	public void loadLevels4() {
		Assert.assertEquals(13, scan("sasquatch_8:", 50));
		Assert.assertEquals(7, scan("sasquatch_9:", 50));
		Assert.assertEquals(6, scan("sasquatch_10:", 50));
		Assert.assertEquals(11, scan("sasquatch_11:", 50));
	}
}