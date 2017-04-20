package tintor.sokoban;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.InlineChainingHashSet;
import tintor.common.Log;
import tintor.common.Timer;
import tintor.common.Util;

// Deadlock:
// TODO: Take every 2 and 3 box subset from start position and initialize deadlock DB
//       with all REACHABLE deadlock patterns of size 2 and 3. This way ContainsFrozenBoxes can stop when it reaches 2 or 3 boxes.
// TODO: ExaustiveDeadlockTest with only boxes on goals
// TODO: ExaustiveDeadlockTest: with every subset of X boxes, is it solvable with a simple BFS solver?
// TODO: deadlock patterns for goal cells (must keep track of which goals are empty and which are occupied by frozen boxes)
// TODO: regenerate level (recompute alive cells) once a box becomes frozen on goal.
//       Will reduce number of live cells and make deadlock checking and heuristic faster and more accurate.
// TODO: microban:144 contains 4 frozen boxes on goals, we should change these to wall at start
// TODO: save deadlock patterns to text file!
// TODO: can avoid calling matchesPattern() inside containsFrozenBoxes() if box push can be reversed
//       (can do a very cheap check here, just try to go around the box)
// TODO: store deadlock patterns from deadlocks found by Heuristic

// Search space reduction:
// TODO: Take advantage of symmetry
//       State.equals and State.hashCode to treat symmetrical states as equal (also deadlock patterns)
//       Compute all symmetry transforms: int->int for use in equals and hashCode.
// TODO: search from end backwards and from start forwards and meet in the middle?
// TODO: How to optimize alive tunnels?
//       1) compress them as they can contain at most one box (if no goals) -> reduces number of alive cells
//       2) or push box all the way then it enters tunnel
// TODO: compress agent-only tunnels -> reduces total number of cells
// TODO: How to optimize goal room with single entrance? Two entrances?
// TODO: What states can we cut without eliminating: all solutions / optimal solution? (tunnels for example)
// TODO: Split level into sub-levels that can be solved independently

// Memory:
// TODO: use SparseHashSet for ClosedSet
// TODO: store States in OpenSet as compacted (works well with removal of binary heap)
// TODO: compact States to byte[] in ClosedSet
// TODO: use int[] instead of boolean[] for boxes everywhere! (easier to transition to and more compact than boolean[])
// TODO: use 2xlong instead of boolean[] for boxes everywhere! (how to handle >128 alive cells? find all levels which need >128)
// TODO: in OpenSet use array of hash sets (one for each total_dist). This way we can remove total_dist fields and binary heap array!
//       It also prevents use of secondary priority.
// TODO: How to remove states from ClosedSet and OpenSet that were later found to be deadlocks?
//       - One way is just to restart the search (while keeping deadlock patterns), and also to populate deadlock patterns at start.
//       - If state A is explored and all of its next B states are deadlocks then we can safely not add A to ClosedSet.
//       - In ClosedSet keep of number of all non-deadlock next nodes that were generated.
//             When node A is found to be deadlock update its parent in ClosedSet (and recursively).
// TODO: removing garbage from heap: sort heap by (total_dist, identity), remove duplicates, (resulting array is a valid heap)
//       try removing heap garbage before growing heap array
// TODO: store only states with unique boxes, not unique boxes and agent (but still optimize for moves)
//       - might need to keep more than one (agent, distance) pair (but not all pairs as most can be derived)

// Search order:
// TODO: add num_solved_boxes as second priority for State (might not make any difference)
// TODO: shorten last iteration: once B is found solved, we can return it as soon as all shorter States are removed from Queue

// Heuristic:
// TODO: tell MatchingModel which solved boxes are frozen (so that it could potentially find a new deadlock)
// TODO: use minimal push distance in Heuristics

// Other:
// TODO: assert garbage can't be negative
// TODO: IDA*
// TODO: Parallelize hash table grow! as there are no conflicts
// TODO: How can search be parallelized?
//       - more exhaustive deadlock check per State
// TODO: Nightly mode - run all microban + original levels over night (catching OOMs) and report results
// TODO: Start using git!
// TODO: Look at the sokoban PhD for more ideas.

public class Solver {
	static int greedy_score(StateBase s, Level level) {
		int score = 0;
		if (s instanceof State) {
			State e = (State) s;
			for (int a = 0; a < level.alive; a++)
				if (level.goal(a) && e.box(a)) {
					score += 5;
					for (byte dir = 0; dir < 4; dir++)
						if (level.move(a, dir) == Level.Bad)
							score += 1;
				}
		} else {
			State2 e = (State2) s;
			for (int a = 0; a < level.alive; a++)
				if (level.goal(a) && e.box(a)) {
					score += 5;
					for (byte dir = 0; dir < 4; dir++)
						if (level.move(a, dir) == Level.Bad)
							score += 1;
				}
		}
		return score;
	}

	static StateBase[] extractPath(Level level, StateBase start, StateBase end, ClosedSet closed) {
		ArrayDeque<StateBase> path = new ArrayDeque<StateBase>();
		while (!end.equals(start)) {
			path.addFirst(end);
			end = closed.get(end.prev(level));
		}
		return path.toArray(new StateBase[path.size()]);
	}

	static State[] solve_IDAstar(Level level, State start, Heuristic model, Deadlock deadlock, Context context) {
		if (deadlock != null && deadlock.check(start))
			return null;
		if (level.is_solved(start))
			return new State[] {};

		ArrayDeque<StateBase> stack = new ArrayDeque<StateBase>();
		int total_dist_max = model.evaluate(start, null);
		while (true) {
			assert stack.isEmpty();
			stack.push(start);

			int total_dist_cutoff_min = Integer.MAX_VALUE;
			while (!stack.isEmpty()) {
				StateBase a = stack.pop();
				for (int dir : level.dirs[a.agent()]) {
					StateBase b = a.move(dir, level);
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

	static StateBase[] solve_Astar(Level level, StateBase start, Heuristic model, Deadlock deadlock, Context context) {
		if (deadlock != null && deadlock.check(start))
			return null;
		if (level.is_solved(start))
			return new StateBase[] {};

		// Generate initial deadlock patterns
		if (context.enable_populate) {
			InlineChainingHashSet set = new InlineChainingHashSet(1 << 20, level);
			ArrayDeque<StateBase> queue = new ArrayDeque<StateBase>();
			queue.add(start);
			while (!queue.isEmpty() && set.size() < (1 << 20) - 3) {
				StateBase a = queue.removeFirst();
				for (byte dir = 0; dir < 4; dir++) {
					StateBase b = a.move(dir, level);
					if (b == null || set.contains(b))
						continue;
					if (b.is_push && deadlock.check(b))
						continue;
					queue.addLast(b);
					set.addUnsafe(b);
					if (set.size() % (1 << 20) == 0)
						Log.info("found %d patterns (set %s) %s", deadlock.patterns, Util.human(set.size()),
								Arrays.toString(deadlock.histogram));
				}
			}
		}

		final ClosedSet closed = new ClosedSet(level.alive);
		final OpenSet open = new OpenSet(level.alive);
		open.addUnsafe(start);

		final Monitor monitor = new Monitor();
		monitor.closed = closed;
		monitor.open = open;
		monitor.deadlock = deadlock;
		monitor.level = level;
		monitor.timer.start();

		while (open.size() > 0) {
			StateBase a = open.removeMin();
			if (!closed.add(a)) {
				open.garbage -= 1;
				continue;
			}

			if (level.is_solved(a)) {
				context.open_set_size = open.size();
				context.closed_set_size = closed.size();
				return extractPath(level, start, a, closed);
			}

			int reverse_dir = -1;
			if (!a.is_push && a.dir != -1)
				reverse_dir = Level.reverseDir(a.dir);
			for (int dir : level.dirs[a.agent()]) {
				if (dir == reverse_dir)
					continue;
				StateBase b = a.move(dir, level);
				if (b == null || closed.contains(b))
					continue;

				StateBase v = open.get(b);
				if (v == null && b.is_push && deadlock.check(b))
					continue;

				/*if (context.enable_greedy)
					b.greedy_score = b.is_push ? (byte) greedy_score(b, level) : a.greedy_score;*/
				monitor.model_timer.start();
				int h = model.evaluate(b, a);
				monitor.model_timer.stop();
				if (h == Integer.MAX_VALUE) {
					monitor.model_deadlocks += 1;
					continue;
				} else
					monitor.model_non_deadlocks += 1;
				b.set_heuristic(h);

				if (v == null) {
					open.addUnsafe(b);
					monitor.branches += 1;
					continue;
				}
				if (b.total_dist() >= v.total_dist())
					continue;

				// B is better than V
				open.update(v, b);
				open.garbage += 1;
			}

			if (context.trace > 0)
				monitor.report(a);
		}
		return null;
	}

	static void printSolution(Level level, StateBase[] solution) {
		ArrayList<StateBase> pushes = new ArrayList<StateBase>();
		for (StateBase s : solution)
			if (s.is_push)
				pushes.add(s);
		for (int i = 0; i < pushes.size(); i++) {
			StateBase s = pushes.get(i);
			StateBase n = i == pushes.size() - 1 ? null : pushes.get(i + 1);
			if (i == pushes.size() - 1 || level.move(s.agent(), s.dir) != n.agent() || s.dir != n.dir)
				level.print(s);
		}
	}

	static Timer timer = new Timer();

	public static void main(String[] args) throws Exception {
		Level level = new Level("data/sokoban/microban:139");
		Log.info("cells:%d alive:%d boxes:%d state_space:%s", level.cells, level.alive, level.num_boxes,
				level.state_space());
		level.print(level.start);
		Heuristic model = new MatchingHeuristic();
		model.init(level);
		Context context = new Context();
		context.trace = 1;
		context.enable_populate = false;
		context.enable_greedy = false;
		level.start.set_heuristic(model.evaluate(level.start, null));

		Deadlock deadlock = new Deadlock(level);
		timer.start();
		StateBase[] solution = solve_Astar(level, level.start, model, deadlock, context);
		timer.stop();
		if (solution == null) {
			Log.info("no solution! %s", timer.human());
		} else {
			printSolution(level, solution);
			Log.info("solved in %d steps! %s", solution.length, timer.human());
		}
		Log.info("closed:%s open:%s patterns:%s", context.closed_set_size, context.open_set_size,
				Util.human(deadlock.patterns));
	}

	static class Context {
		boolean enable_populate;
		boolean enable_greedy;
		int trace; // 0 to turn off any tracing
		int open_set_size;
		int closed_set_size;
	}
}