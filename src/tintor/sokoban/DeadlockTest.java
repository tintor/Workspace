package tintor.sokoban;

import org.junit.Assert;
import org.junit.Test;

public class DeadlockTest {
	@Test
	public void test() {
		Level level = new Level("frozen_boxes.test");
		Deadlock d = new Deadlock(level);
		Assert.assertTrue(d.check(level.start));
		Assert.assertEquals(1, d.patterns);
		Assert.assertTrue(d.check(level.start)); // second check will match a pattern
	}
}