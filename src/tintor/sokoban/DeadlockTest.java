package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

public class DeadlockTest {
	@Test
	public void test() {
		Level level = new Level("data/frozen_boxes.test");
		Deadlock d = new Deadlock(level);
		Assert.assertTrue(d.check(level.start));
		Assert.assertTrue(d.check(level.start)); // second check will match a pattern
	}
}