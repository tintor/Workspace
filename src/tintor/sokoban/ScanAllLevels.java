package tintor.sokoban;

import tintor.common.Log;
import tintor.common.Timer;
import tintor.sokoban.Solver.Context;

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
			try {
				timer.start();
				Level level = new Level(prefix + i);
				timer.stop();
				cells += level.cells;
				alive += level.alive;
				int state_space = level.state_space();
				Log.info("%s%d cells:%d alive:%d boxes:%d state_space:%s time:%s", name, i, level.cells, level.alive,
						level.num_boxes, state_space, timer.human());
				// TODO show tunnels, articulation points
				level.print(level.start);
				//Log.info("alive");
				//level.low.print(p -> p < level.alive ? '.' : ' ');
				Log.info("bottleneck");
				level.low.print(p -> level.bottleneck[p] ? '.' : ' ');
				if (MinStateSpace <= state_space && state_space <= MaxStateSpace) {
					Context context = new Context();
					context.trace = 1;
					try {
						State[] solution = Solver.solve_Astar(level, level.start, new Heuristic(level),
								new Deadlock(level), context);
						if (solution != null)
							solved += 1;
						else
							no_solution += 1;
					} catch (OutOfMemoryError e) {
						out_of_memory += 1;
					}
				}
			} catch (Level.MoreThan128AliveCellsError e) {
				Log.error("%s%d more than 128 alive cells error", name, i);
				errors += 1;
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