package tintor.common;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

// Move mouse pointer every 30 seconds to prevent computer from falling to sleep
@UtilityClass
public class KeepAwake {
	@SneakyThrows
	private static void run() {
		final Robot robot = new Robot();
		while (true) {
			robot.delay(30_000); // 30 seconds
			Point p = MouseInfo.getPointerInfo().getLocation();
			robot.mouseMove(p.x + 1, p.y + 1);
			robot.mouseMove(p.x - 1, p.y - 1);
			robot.mouseMove(p.x, p.y);
		}
	}

	public static void enable() {
		new Thread(KeepAwake::run).setDaemon(true);
	}
}