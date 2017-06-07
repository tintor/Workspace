package tintor.sokoban.test;

import org.junit.Assert;
import org.junit.Test;

import tintor.sokoban.Cell;
import tintor.sokoban.Cell.Dir;
import tintor.sokoban.Deadlock;
import tintor.sokoban.Level;
import tintor.sokoban.LevelUtil;
import tintor.sokoban.State;

public class DeadlockTest {
	@Test
	public void test() {
		Level level = Level.load("frozen_boxes.test");
		Deadlock d = new Deadlock(level, false);
		Assert.assertTrue(d.checkFull(level.start));
		Assert.assertEquals(1, d.patternIndex.size());
		Assert.assertTrue(d.checkFull(level.start)); // second check will match a pattern
	}

	@Test
	public void deadlockWithBoxesFrozenOnGoal() {
		Level level = Level.load("test:12");
		Deadlock d = new Deadlock(level, false);
		Assert.assertTrue(d.checkFull(level.start));
	}

	private void deadlock_tunnel(int level_no, boolean expected) {
		Level level = Level.load("test:" + level_no);
		State s = level.start;
		for (Cell b : level.cells)
			if (b.box(s.box))
				Assert.assertEquals(expected, LevelUtil.is_tunnel_deadlock(level.cells[s.agent], b, s.box));
	}

	@Test
	public void deadlock_tunnel_20() {
		deadlock_tunnel(20, true);
	}

	@Test
	public void deadlock_tunnel_21() {
		deadlock_tunnel(21, true);
	}

	@Test
	public void deadlock_tunnel_22() {
		deadlock_tunnel(22, false);
	}

	@Test
	public void deadlock_tunnel_23() {
		deadlock_tunnel(23, false);
	}

	private void deadlock_2x3(int level_no, boolean expected) {
		Level level = Level.load("test:" + level_no);
		State s = level.start;
		s = level.start.push(level.cells[s.agent].move(Dir.Down), level, false, 0, s.agent);
		for (Cell b : level.cells)
			if (b.box(s.box))
				Assert.assertEquals(expected, LevelUtil.is_2x3_deadlock(b, s.box));
	}

	@Test
	public void deadlock_2x3_24() {
		deadlock_2x3(24, true);
	}

	@Test
	public void deadlock_2x3_25() {
		deadlock_2x3(25, true);
	}

	@Test
	public void deadlock_2x3_26() {
		deadlock_2x3(26, false);
	}
}