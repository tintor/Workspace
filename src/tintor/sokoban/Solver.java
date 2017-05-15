package tintor.sokoban;

import tintor.common.Log;
import tintor.common.Timer;

// Solved
// 1:3, 6:8, 17

// Unsolved
// original:23 [boxes:18 alive:104 space:73] 5 rooms (1 goal room) in a line with 4 doors between them

// TODO push macro:
// - if box is pushed inside a tunnel with a box already inside (on goal) then keep pushing the box all the way through
// TODO goal cut:
// - if there are multiple pushes from a given State and some of them reduce the number of unreachable goal cells (by pushing into unreachable goal cell) then cut all other pushes
// TODO: separate Timer for partial deadlock.match inside frozen boxes from the global one
// TODO: OpenSet: keep StateArray index in map to avoid garbage (and expensive cleanup)

// Performance:
// TODO: at the start greedily find order to fill goal nodes using matching from Heuristic (and trigger goal macros during search)
// TODO: specialization of OpenAddressingIntArrayHashMap with long[] instead of int[]
// TODO: add timer for growth operation (separate for disk and memory)
// TODO: tweak load factor of OpenAddressingHashMap for max perf
// TODO: try –XX:+UseG1GC
// TODO: keep counter of how many times each deadlock pattern was triggered
// TODO: load patterns.txt at startup
// TODO: remove timers for code sections < 1%
// TODO: web handler for Solver to be able to probe manually into the internals (or just observe search with better UI)
//       - also be able to switch flags as search is running
//       - be able to skip solving a current level (if very slow)
//       - render level in different ways (colors / layers)
//       - browse through deadlock patterns / closed set / open set / each state (all fields + link to prev state)
// TODO: speed up hungarian matching by starting from a good initial match (from the previous state)
// TODO: record all Microban performance runs (also with snapshot of all the code, git branch / tag?)
// TODO: how to teach solver to park boxes in rooms with single entrance (and to find out maximum parking space)?
// TODO: be able to save Solver state and resume it later
// TODO: +++ how to cut same distance (or worse) moves?
// TODO: +++ how to cut low influence moves?
// TODO: +++ be better at detecting deadlocks in far parts of the level

// TODO: store extra data for every level: boxes, alive, cells, state_space,
//       optimal_solution (steps and compute time), some_solution (steps and compute time)

// Misc:
// TODO: move unused common classes into a separate package 

// Deadlock:
// TODO: Take every 2 and 3 box subset from start position and initialize deadlock DB
//       with all REACHABLE deadlock patterns of size 2 and 3. This way ContainsFrozenBoxes can stop when it reaches 2 or 3 boxes.
// TODO: +++ deadlock patterns for goal cells (must keep track of which goals are empty and which are occupied by frozen boxes)
// TODO: +++ regenerate level (recompute alive / walkable / bottleneck cells) once a box becomes frozen on goal.
//       Will reduce number of live cells and make deadlock checking and heuristic faster and more accurate.
// TODO: can avoid calling matchesPattern() inside containsFrozenBoxes() if box push can be reversed
//       (can do a very cheap check here, just try to go around the box)
// TODO: pre-populate PatternIndex with level-independent deadlocks

// Search space reduction:
// TODO: +++ (FIXME) Take advantage of symmetry
// TODO: How to optimize goal room with single entrance? Two entrances?
// TODO: What states can we cut without eliminating: all solutions / optimal solution? (tunnels for example)
// TODO: Split level into sub-levels that can be solved independently

// Memory:
// TODO: +++ when a new deadlock pattern is found with >=3 boxes scan Closed and Open with new pattern to trim them
// TODO: general level loader class, knows only about wall cells, compresses level to only walkable interior cells

// Heuristic:
// TODO: +++ if level has a goal room with single entrance ==> speed up matching by matching from the goal room entrance
// TODO: should be exact if only one box is left?
// TODO: tell MatchingModel which solved boxes are frozen (so that it could potentially find a new deadlock)

// Other:
// TODO: IDA*
// TODO: [Last] How can search be parallelized?
//       - parallel explore(a)
//       - parallel pattern search
// TODO: Nightly mode - run all microban + original levels over night (catching OOMs) and report results
// TODO: Look at the sokoban PhD for more ideas.

public class Solver {
	static void printSolution(Level level, State[] solution) {
		for (int i = 0; i < solution.length; i++) {
			State s = solution[i];
			State n = i == solution.length - 1 ? null : solution[i + 1];
			Cell agent = level.cells[s.agent];
			if (i == solution.length - 1 || agent.dir[s.dir].cell.id != n.agent || s.dir != n.dir) {
				System.out.printf("dist:%s heur:%s\n", s.dist, s.total_dist - s.dist);
				level.print(s);
			}
		}
	}

	static Timer timer = new Timer();

	static char hex(int a) {
		if (a >= 26 || a < 0)
			return '?';
		return (char) (a < 10 ? '0' + a : 'a' + a - 10);
	}

	public static void main(String[] args) throws Exception {
		Level level = Level.load(args[0]);
		Log.info("cells:%d alive:%d boxes:%d state_space:%s has_goal_rooms:%s", level.cells, level.alive,
				level.num_boxes, level.state_space(), level.has_goal_rooms);
		level.print(level.start);
		Log.raw("alive");
		level.print(p -> p.alive ? '.' : ' ');
		Log.raw("tunnels");
		level.print(p -> p.tunnel_entrance() || p.tunnel_interior() ? '.' : ' ');
		Log.raw("rooms");
		level.print(p -> p.room == -1 ? 'x' : hex(p.room));
		AStarSolver solver = new AStarSolver(level, false);
		solver.trace = 2;

		timer.start();
		State end = solver.solve();
		timer.stop();
		if (end == null) {
			Log.info("no solution! %s", timer.human());
		} else {
			State[] solution = solver.extractPath(end);
			for (State s : solution)
				if (solver.deadlock.checkFull(s)) {
					level.print(s);
					throw new Error();
				}
			printSolution(level, solution);
			Log.info("solved in %d steps! %s", end.dist, timer.human());
		}
	}
}