package tintor.sokoban;

import tintor.common.Log;
import tintor.common.Timer;

public class ScanAllLevels {
	static final int MinStateSpace = 25;
	static final int MaxStateSpace = 0;
	static Timer timer = new Timer();
	static int cells, alive, errors;
	static int solved, no_solution, out_of_memory;

	public static void scan(String prefix, int levels) {
		for (int i = 1; i <= levels; i++) {
			String[] s = prefix.split("/");
			String name = s[s.length - 1];
			timer.start();
			Level level = Level.load(prefix + i);
			timer.stop();
			cells += level.cells;
			alive += level.alive;
			int state_space = level.state_space();
			Log.info("%s%d cells:%d alive:%d boxes:%d state_space:%s time:%s", name, i, level.cells, level.alive,
					level.num_boxes, state_space, timer.human());
			level.print(level.start);
			Log.info("bottleneck");
			level.low.print(p -> level.bottleneck[p] ? '.' : ' ');
			if (MinStateSpace <= state_space && state_space <= MaxStateSpace) {
				AStarSolver solver = new AStarSolver(level);
				solver.trace = 1;
				try {
					if (solver.solve() != null)
						solved += 1;
					else
						no_solution += 1;
				} catch (OutOfMemoryError e) {
					out_of_memory += 1;
				}
			}
			timer.total = 0;
		}
	}

	public static void main(String[] args) {
		Log.raw = true;
		scan("microban:", 155);
		scan("original:", 90);
		for (int i = 1; i <= 11; i++)
			scan("sasquatch_" + i + ":", 50);

		Log.info("TOTAL cells:%d alive:%d errors:%d", cells, alive, errors);
		Log.info("solved:%d no_solution:%d out_of_memory:%d", solved, no_solution, out_of_memory);
	}
}