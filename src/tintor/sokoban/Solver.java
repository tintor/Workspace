package tintor.sokoban;

import java.util.ArrayDeque;
import java.util.ArrayList;

import tintor.common.AutoTimer;
import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;
import tintor.common.Visitor;

// TODO: try –XX:+UseG1GC

// TODO: store extra data for every level: boxes, alive, cells, state_space,
//       optimal_solution (steps and compute time), some_solution (steps and compute time)

// TODO: re-implement HungarianAlgorithm:
//       - int instead of double
//       - avoid N^2 cost of populating the input matrix

// TODO: estimate upper bound on the remaining time to solve()
//       total_space_size - closed_size * (walkable - boxes) - states_covered_by_every_deadlock_pattern

// Deadlock:
// TODO: +++ if subset of boxes (not on goal) didn't move for a long time (potential deadlock)
//       run full deadlock check of that subset => could solve 
// TODO: how to speed up deadlock pattern lookups?
// TODO: Take every 2 and 3 box subset from start position and initialize deadlock DB
//       with all REACHABLE deadlock patterns of size 2 and 3. This way ContainsFrozenBoxes can stop when it reaches 2 or 3 boxes.
// TODO: ExaustiveDeadlockTest with all boxes on goal + one box not on goal
// TODO: ExaustiveDeadlockTest: with every subset of X boxes, is it solvable with a simple BFS solver?
// TODO: deadlock patterns for goal cells (must keep track of which goals are empty and which are occupied by frozen boxes)
// TODO: regenerate level (recompute alive / walkable / bottleneck cells) once a box becomes frozen on goal.
//       Will reduce number of live cells and make deadlock checking and heuristic faster and more accurate.
// TODO: can avoid calling matchesPattern() inside containsFrozenBoxes() if box push can be reversed
//       (can do a very cheap check here, just try to go around the box)
// TODO: --- store deadlock patterns from deadlocks found by Heuristic

// Search space reduction:
// TODO: only "store" push States (heuristic will not have to worry about distance of agent to box)
// TODO: Always do even number of steps (only store States with distance % 2 == 0)
// TODO: Take advantage of symmetry
//       State.equals and State.hashCode to treat symmetrical states as equal (also deadlock patterns)
//       Compute all symmetry transforms: int->int for use in equals and hashCode.
// TODO: search from end backwards and from start forwards and meet in the middle?
// TODO: How to optimize goal room with single entrance? Two entrances?
// TODO: What states can we cut without eliminating: all solutions / optimal solution? (tunnels for example)
// TODO: Split level into sub-levels that can be solved independently

// Memory:
// TODO: +++ when a new deadlock pattern is found with >=3 boxes scan Closed and Open with new pattern to trim them
// TODO: Change ClosedSet to use memory StateSet and store key/value pairs in file (every add() writes to file, don't store map values in memory)

// Search order:
// TODO: shorten last iteration: once B is found solved, we can remove all >= States from OpenSet (continue search to try to find a better solution)

// Heuristic:
// TODO: +++ if level has a goal room with single entrance ==> speed up matching by matching from the goal room entrance
// TODO: should be exact if only one box is left?
// TODO: tell MatchingModel which solved boxes are frozen (so that it could potentially find a new deadlock)

// Other:
// TODO: IDA*
// TODO: How can search be parallelized?
//       - more exhaustive deadlock check per State
//       - parallel explore(a)
//       - parallel pattern search
// TODO: Nightly mode - run all microban + original levels over night (catching OOMs) and report results
// TODO: Look at the sokoban PhD for more ideas.

class AStarSolver {
	final Level level;
	final OpenSet open;
	final ClosedSet closed;
	final Heuristic heuristic;
	final Deadlock deadlock;

	int closed_size_limit;
	boolean optimal_macro_moves;
	boolean disable_deadlock_check;
	int trace; // 0 to turn off any tracing
	State[] valid;

	AStarSolver(Level level) {
		this.level = level;
		open = new OpenSet(level.alive, level.cells);
		closed = new ClosedSet(level.alive, level.cells);
		heuristic = new Heuristic(level);
		deadlock = new Deadlock(level);

		visitor = new Visitor(level.cells);
		moves = new int[level.cells];
	}

	static class ClosedSizeLimitError extends Error {
		private static final long serialVersionUID = 1L;
	};

	AutoTimer timer_solve = new AutoTimer("solve");

	State solve() {
		int h = heuristic.evaluate(level.start);
		if (h == Integer.MAX_VALUE)
			return null;
		level.start.set_heuristic(h);
		if (level.is_solved_fast(level.start.box))
			return level.start;

		long next_report = 10 * AutoTimer.Second;

		open.add(level.start);
		State a = null;
		while (true) {
			try (AutoTimer t = timer_solve.open()) {
				a = open.remove_min();
				if (a == null || level.is_solved_fast(a.box))
					break;
				closed.add(a);
				if (closed_size_limit != 0 && closed.size() >= closed_size_limit)
					throw new ClosedSizeLimitError();

				explore(a);
			}

			if (trace > 0 && AutoTimer.total() >= next_report) {
				report(a);
				next_report += 10 * AutoTimer.Second;
			}
		}
		if (trace > 0)
			report(a);
		return a;
	}

	boolean solveable(State start) {
		if (level.is_solved_fast(start.box))
			return true;

		final ArrayList<State> stack = new ArrayList<State>();
		final StateMap explored = new StateMap(level.alive, level.cells);
		explored.insert(start);
		stack.add(start);

		while (stack.size() != 0) {
			State a = stack.remove(stack.size() - 1);
			visitor.init(a.agent);
			while (!visitor.done()) {
				int agent = visitor.next();
				for (int p : level.moves[agent]) {
					if (!a.box(p)) {
						if (visitor.visited(p))
							continue;
						visitor.add(p);
						continue;
					}
					State b = a.push(p, level.delta[agent][p], level, false, 0, 0);
					if (b == null || explored.contains(b))
						continue;
					if (level.is_solved_fast(b.box))
						return true;
					stack.add(b);
				}
			}
			if (explored.size() % 1000000 == 0)
				Log.info("%d", explored.size() / 1000000);
		}
		return false;
	}

	private Visitor visitor;
	private int[] moves;

	final AutoTimer timer_moves = new AutoTimer("moves");
	int cutoff = Integer.MAX_VALUE;
	int cutoffs = 0;

	void explore(State a) {
		visitor.init(a.agent);
		moves[a.agent] = 0; // TODO merge moves to visitor (into set)
		while (!visitor.done()) {
			// TODO stop the loop early after we reach all sides of all boxes
			// TODO prioritize cells closer to untouched sides of boxes
			int agent = visitor.next();
			for (int p : level.moves[agent]) {
				if (!a.box(p)) {
					if (visitor.visited(p))
						continue;
					visitor.add(p);
					moves[p] = moves[agent] + 1;
					continue;
				}

				timer_moves.open();
				State b = a.push(p, level.delta[agent][p], level, optimal_macro_moves, moves[agent], a.agent);
				timer_moves.close();
				if (b == null || closed.contains(b))
					continue;

				int v_total_dist = open.get_total_dist(b);
				if (v_total_dist == 0 && !disable_deadlock_check && deadlock.check(b))
					continue;

				int h = heuristic.evaluate(b);
				if (h >= cutoff) {
					cutoffs += 1;
					continue;
				}
				b.set_heuristic(h);

				if (v_total_dist == 0) {
					if (level.is_solved_fast(b.box)) {
						open.remove_all_ge(b.total_dist());
						cutoff = b.total_dist();
					}
					open.add(b);
					continue;
				}
				if (b.total_dist() < v_total_dist)
					open.update(v_total_dist, b);
			}
		}
	}

	State[] extractPath(State end) {
		ArrayDeque<State> path = new ArrayDeque<State>();
		while (!end.equals(level.start)) {
			path.addFirst(end);
			end = closed.get(end.prev(level));
		}
		return path.toArray(new State[path.size()]);
	}

	private long prev_time = 0;
	private int prev_open = 0;
	private int prev_closed = 0;
	private double speed = 0;

	private void report(State a) {
		long delta_time = AutoTimer.total() - prev_time;
		int delta_closed = closed.size() - prev_closed;
		int delta_open = open.size() - prev_open;
		prev_time = AutoTimer.total();
		prev_closed = closed.size();
		prev_open = open.size();

		speed = (speed + 1e9 * delta_closed / delta_time) / 2;

		closed.report();
		open.report();
		deadlock.report();
		System.out.printf("dist:%d total_dist:%d cutoff:%s dead:%s live:%s\n", a.dist(), a.total_dist(),
				Util.human(cutoffs), Util.human(heuristic.deadlocks), Util.human(heuristic.non_deadlocks));
		System.out.printf("speed:%s ", Util.human((int) speed));
		System.out.printf("branch:%.2f ", 1 + (double) delta_open / delta_closed);
		Log.info("free_memory:%s", Util.human(Runtime.getRuntime().freeMemory()));
		AutoTimer.report();

		level.print(a);
	}
}

class IterativeDeepeningAStar {
	State solve(Level level) {
		Heuristic heuristic = new Heuristic(level);
		if (level.is_solved_fast(level.start.box))
			return level.start;

		ArrayDeque<State> stack = new ArrayDeque<State>();
		int total_dist_max = heuristic.evaluate(level.start);
		while (true) {
			assert stack.isEmpty();
			stack.push(level.start);

			int total_dist_cutoff_min = Integer.MAX_VALUE;
			while (!stack.isEmpty()) {
				State a = stack.pop();
				for (int dir : level.dirs[a.agent]) {
					State b = null; // a.move(dir, level, context.optimal_macro_moves);
					if (b == null)
						break;
					// TODO cut move if possible

					b.set_heuristic(heuristic.evaluate(b));
					if (b.total_dist() > total_dist_max) {
						total_dist_cutoff_min = Math.min(total_dist_cutoff_min, b.total_dist());
						continue;
					}
					stack.push(b);
				}
				// TODO reorder moves
			}
			if (total_dist_cutoff_min == Integer.MAX_VALUE)
				break;
			total_dist_max = total_dist_cutoff_min;
		}
		return null;
	}
}

public class Solver {
	static void printSolution(Level level, State[] solution) {
		for (int i = 0; i < solution.length; i++) {
			State s = solution[i];
			State n = i == solution.length - 1 ? null : solution[i + 1];
			if (i == solution.length - 1 || level.move(s.agent, s.dir) != n.agent || s.dir != n.dir) {
				System.out.printf("dist:%s heur:%s\n", s.dist(), s.total_dist() - s.dist());
				level.print(s);
			}
		}
	}

	static Timer timer = new Timer();

	// solved original 1 2 3 4 * 6 7 *
	public static void main(String[] args) throws Exception {
		Level level = Level.load("microban5:21");
		//Level level = Level.load("original:15");
		Log.info("cells:%d alive:%d boxes:%d state_space:%s", level.cells, level.alive, level.num_boxes,
				level.state_space());
		level.print(level.start);
		AStarSolver solver = new AStarSolver(level);
		solver.trace = 1;

		timer.start();
		State end = solver.solve();
		timer.stop();
		if (end == null) {
			Log.info("no solution! %s", timer.human());
		} else {
			State[] solution = solver.extractPath(end);
			for (State s : solution)
				if (solver.deadlock.check(s)) {
					level.print(s);
					throw new Error();
				}
			printSolution(level, solution);
			Log.info("solved in %d steps! %s", end.dist(), timer.human());
		}
	}
}