package tintor.sokoban;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.InlineChainingHashSet;
import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;

// try –XX:+UseG1GC

// Deadlock:
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
// TODO: store deadlock patterns from deadlocks found by Heuristic
// TODO: scan open and closed sets for deadlocks (once more patterns have been discovered) and remove deadlock states

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
// TODO: 33% savings on ClosedSet memory (every add() writes to file, don't store map values in memory)
// TODO: How to remove states from ClosedSet and OpenSet that were later found to be deadlocks?
//       - Keep scanning ClosedSet and OpenSet slowly continuously against the deadlock db and remove deadlocks
//       - If state A is explored and all of its next B states are deadlocks then we can safely not add A to ClosedSet.
//       - In ClosedSet keep of number of all non-deadlock next nodes that were generated.
//             When node A is found to be deadlock update its parent in ClosedSet (and recursively).
// TODO: store only states with unique boxes, not unique boxes and agent (but still optimize for moves)
//       - might need to keep more than one (agent, distance) pair (but not all pairs as most can be derived)

// Search order:
// TODO: add num_solved_boxes as second priority for State (might not make any difference)
// TODO: shorten last iteration: once B is found solved, we can return it as soon as all shorter States are removed from Queue

// Heuristic:
// TODO: tell MatchingModel which solved boxes are frozen (so that it could potentially find a new deadlock)

// Other:
// TODO: Switch State to int[]
// TODO: IDA*
// TODO: How can search be parallelized?
//       - more exhaustive deadlock check per State
// TODO: Nightly mode - run all microban + original levels over night (catching OOMs) and report results
// TODO: Look at the sokoban PhD for more ideas.

public final class Solver {
	static State[] extractPath(Level level, State start, State end, ClosedSet closed) {
		ArrayDeque<State> path = new ArrayDeque<State>();
		while (!end.equals(start)) {
			path.addFirst(end);
			end = closed.get(end.prev(level));
		}
		return path.toArray(new State[path.size()]);
	}

	static State[] solve_IDAstar(Level level, State start, Heuristic model, Deadlock deadlock, Context context) {
		if (deadlock != null && deadlock.check(start))
			return null;
		if (level.is_solved(start))
			return new State[] {};

		ArrayDeque<State> stack = new ArrayDeque<State>();
		int total_dist_max = model.evaluate(start, null);
		while (true) {
			assert stack.isEmpty();
			stack.push(start);

			int total_dist_cutoff_min = Integer.MAX_VALUE;
			while (!stack.isEmpty()) {
				State a = stack.pop();
				for (int dir : level.dirs[a.agent()]) {
					State b = a.move(dir, level, context.optimal_macro_moves);
					if (b == null)
						break;
					// TODO cut move if possible

					b.set_heuristic(model.evaluate(b, a));
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

	static State[] solve_Astar(Level level, boolean trace) {
		Context context = new Context();
		context.trace = trace ? 1 : 0;
		return solve_Astar(level, level.start, new Heuristic(level), new Deadlock(level), context);
	}

	static State[] solve_Astar(Level level, State start, Heuristic heuristic, Deadlock deadlock, Context context) {
		int h = heuristic.evaluate(start, null);
		if (h == Integer.MAX_VALUE)
			return null;
		start.set_heuristic(h);
		if (deadlock.check(start))
			return null;
		if (level.is_solved(start))
			return new State[] {};

		// Generate initial deadlock patterns
		if (context.enable_populate) {
			InlineChainingHashSet set = new InlineChainingHashSet(1 << 20, level, true);
			ArrayDeque<State> queue = new ArrayDeque<State>();
			queue.add(start);
			while (!queue.isEmpty() && set.size() < (1 << 20) - 3) {
				State a = queue.removeFirst();
				for (byte dir = 0; dir < 4; dir++) {
					State b = a.move(dir, level, context.optimal_macro_moves);
					if (b == null || set.contains(b))
						continue;
					if (b.is_push() && deadlock.check(b))
						continue;
					queue.addLast(b);
					set.addUnsafe(b);
					if (set.size() % (1 << 20) == 0)
						Log.info("found %d patterns (set %s) %s", deadlock.patterns, Util.human(set.size()),
								Arrays.toString(deadlock.histogram));
				}
			}
		}

		final ClosedSet closed = new ClosedSet(level.alive, level.cells);
		final OpenSet open = new OpenSet(level.alive, level.cells);
		assert start.total_dist() != 0;
		open.add(start);

		final Monitor monitor = new Monitor();
		monitor.closed = closed;
		monitor.open = open;
		monitor.deadlock = deadlock;
		monitor.level = level;
		monitor.heuristic = heuristic;
		monitor.timer.start();

		while (!open.empty()) {
			State a = open.remove_min();
			assert a.total_dist() != 0;
			closed.add(a);

			if (level.is_solved(a)) {
				context.open_set_size = open.size();
				context.closed_set_size = closed.size();
				return extractPath(level, start, a, closed);
			}

			// TODO parallelize: submit a task to execute this loop
			explore(a, monitor, context);

			if (context.trace > 0)
				monitor.report(a);
		}
		return null;
	}

	// TODO ReadWriteLock to ClosedSet
	// TODO ReadWriteLock to OpenSet
	// TODO move shared data from OpenSet to ThreadLocal
	// TODO ReadWriteLock to Deadlock (pattern database)
	// TODO move shared data from Heuristic to ThreadLocal
	static void explore(State a, Monitor monitor, Context context) {
		int reverse_dir = -1;
		if (!a.is_push() && a.dist() != 0)
			reverse_dir = Level.reverseDir(a.dir);
		for (int dir : monitor.level.dirs[a.agent()]) {
			if (dir == reverse_dir)
				continue;
			monitor.timer_moves.start();
			State b = a.move(dir, monitor.level, context.optimal_macro_moves);
			monitor.timer_moves.stop();
			if (b == null || monitor.closed.contains(b))
				continue;

			int v_total_dist = monitor.open.get_total_dist(b);
			if (v_total_dist == 0 && b.is_push() && monitor.deadlock.check(b))
				continue;

			int h = monitor.heuristic.evaluate(b, a);
			if (h == Integer.MAX_VALUE)
				continue;
			b.set_heuristic(h);
			assert b.total_dist() != 0;

			if (v_total_dist == 0) {
				monitor.open.add(b);
				monitor.branches += 1;
				continue;
			}
			if (b.total_dist() < v_total_dist)
				monitor.open.update(v_total_dist, b);
		}
	}

	static void printSolution(Level level, State[] solution) {
		ArrayList<State> pushes = new ArrayList<State>();
		for (State s : solution)
			if (s.is_push())
				pushes.add(s);
		for (int i = 0; i < pushes.size(); i++) {
			State s = pushes.get(i);
			State n = i == pushes.size() - 1 ? null : pushes.get(i + 1);
			if (i == pushes.size() - 1 || level.move(s.agent(), s.dir) != n.agent() || s.dir != n.dir)
				level.print(s);
		}
	}

	static Timer timer = new Timer();

	public static void main(String[] args) throws Exception {
		Level level = Level.load("original:2");
		Log.info("cells:%d alive:%d boxes:%d state_space:%s", level.cells, level.alive, level.num_boxes,
				level.state_space());
		level.print(level.start);
		Context context = new Context();
		context.trace = 1;
		context.enable_populate = false;
		context.optimal_macro_moves = false;

		Deadlock deadlock = new Deadlock(level);
		timer.start();
		State[] solution = solve_Astar(level, level.start, new Heuristic(level), deadlock, context);
		timer.stop();
		if (solution == null) {
			Log.info("no solution! %s", timer.human());
		} else {
			printSolution(level, solution);
			Log.info("solved in %d steps! %s", solution[solution.length - 1].dist(), timer.human());
		}
		Log.info("closed:%s open:%s patterns:%s", context.closed_set_size, context.open_set_size,
				Util.human(deadlock.patterns));
	}

	static class Context {
		boolean enable_populate;
		boolean optimal_macro_moves = false;
		int trace; // 0 to turn off any tracing
		int open_set_size;
		int closed_set_size;
	}
}