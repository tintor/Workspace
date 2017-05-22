package tintor.sokoban.test;

import tintor.common.Log;
import tintor.sokoban.Level;

public class LevelLoader {
	public static void main(String[] args) {
		String files = "microban1 microban2 microban3 microban4 microban5 original spiros test 100k_moves_only";
		for (int j = 1; j <= 11; j++)
			files += " sasquatch_" + j;

		for (String filename : files.split("\\s"))
			for (Level level : Level.loadAll(filename))
				Log.raw("%s cells:%d alive:%d boxes:%d state_space:%s", level.name, level.cells.length,
						level.alive.length, level.num_boxes, level.state_space());
	}
}