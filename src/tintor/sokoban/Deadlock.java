package tintor.sokoban;

import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.Bits;
import tintor.common.InstrumentationAgent;
import tintor.common.Util;
import tintor.common.Visitor;
import tintor.sokoban.StateMap.StateKeyPredicate;

// TODO: new int[] + System.arraycopy() might be faster than clone() (or maybe even Arrays.copyOf(array, array.length))

final class Deadlock {
	final static class Patterns {
		public long[] array_box = new long[0];

		public int size;
		public int[] end;

		public Patterns(int level_num_boxes, int level_alive) {
			end = new int[level_num_boxes + 1];
		}

		public void add(int[] box, int num_boxes) {
			if (matches(box, num_boxes))
				return;

			final int N = (box.length + 1) / 2;
			if (array_box.length == size * N)
				array_box = Arrays.copyOf(array_box, Math.max(N, array_box.length * 2));

			int x = end[num_boxes];
			System.arraycopy(array_box, x * N, array_box, (x + 1) * N, (size - x) * N);
			for (int i = 0; i < N; i++) {
				long b = intLow(box[i * 2]);
				if (box.length > i * 2 + 1)
					b |= intHigh(box[i * 2 + 1]);
				array_box[x * N + i] = b;
			}
			size += 1;
			for (int i = num_boxes; i < end.length; i++)
				end[i] += 1;
		}

		static long intLow(int a) {
			return a & 0xFFFFFFFFl;
		}

		static long intHigh(int a) {
			return ((long) a) << 32;
		}

		public boolean matches(int[] box, int num_boxes) {
			return matches_internal(box, num_boxes) != -1;
		}

		public int matches_internal(int[] box, int num_boxes) {
			final int N = (box.length + 1) / 2;

			long box0 = intLow(box[0]);
			if (box.length > 1)
				box0 |= intHigh(box[1]);
			if (N == 1) {
				for (int i = 0; i < end[num_boxes]; i++)
					if ((box0 | array_box[i]) == box0)
						return i;
				return -1;
			}

			long box1 = intLow(box[2]);
			if (box.length > 3)
				box1 |= intHigh(box[3]);
			if (N == 2) {
				for (int i = 0; i < end[num_boxes]; i++)
					if ((box0 | array_box[i * 2]) == box0 && (box1 | array_box[i * 2 + 1]) == box1)
						return i;
				return -1;
			}

			long[] lbox = new long[N];
			lbox[0] = box0;
			lbox[1] = box1;
			for (int i = 2; i < N; i++) {
				long b = intLow(box[i * 2]);
				if (box.length > i * 2 + 1)
					b |= intHigh(box[i * 2 + 1]);
				lbox[i] = b;
			}
			for (int i = 0; i < end[num_boxes]; i++)
				if (matches_one(i, lbox))
					return i;
			return -1;
		}

		private boolean matches_one(int index, long[] box) {
			for (int i = 0; i < box.length; i++)
				if ((box[i] | array_box[index * box.length + i]) != box[i])
					return false;
			return true;
		}
	}

	ClosedSet closed;
	OpenSet open;

	private final Visitor visitor;
	private final Patterns[] pattern_index;
	int patterns;
	int[] histogram;
	private final Level level;
	private FileWriter pattern_file;

	private final AutoTimer timer = new AutoTimer("deadlock");
	private final AutoTimer timerFrozen = new AutoTimer("deadlock.frozen");
	private final AutoTimer timerMatch = new AutoTimer("deadlock.match");
	private final AutoTimer timerAdd = new AutoTimer("deadlock.add");
	private final AutoTimer timerMinimize = new AutoTimer("deadlock.minimize");
	private final AutoTimer timerCleanup = new AutoTimer("deadlock.cleanup");
	private final AutoTimer timerGoalzone = new AutoTimer("deadlock.goalzone");

	private int deadlocks = 0;
	private int non_deadlocks = 0;
	private int trivial = 0;
	private int goalzone_deadlocks = 0;
	private int isvalidlevel_deadlocks = 0;

	void report() {
		System.out.printf("dead:%s live:%s rev:%s goaldead:%s ivldead:%s db:%s db2:%s memory:%s\n",
				Util.human(deadlocks), Util.human(non_deadlocks), Util.human(trivial), Util.human(goalzone_deadlocks),
				Util.human(isvalidlevel_deadlocks), Util.human(patterns), Util.human(goal_zone_patterns.size()),
				Util.human(InstrumentationAgent.deepSizeOf(pattern_index)));
	}

	Deadlock(Level level) {
		this.level = level;
		visitor = new Visitor(level.cells);
		pattern_index = Array.make(level.cells, i -> new Patterns(level.num_boxes, level.alive));
		histogram = new int[level.num_boxes - 1];
		pattern_file = Util.checkIOException(() -> new FileWriter("patterns.txt", false));
	}

	private final ArrayDeque<StateKey> queue = new ArrayDeque<>();
	private StateSet explored;

	private boolean isGoalZoneDeadlock(StateKey s) {
		// remove all boxes not on goals
		int[] box = s.box.clone();
		for (int b = 0; b < level.alive; b++)
			if (Bits.test(box, b) && !level.goal(b))
				Bits.clear(box, b);
		if (Bits.count(box) == 0)
			return false;

		// move agent and try to push remaining boxes
		// TODO assume at most 32 goals and represent boxes with a single int (+ agent position)
		if (explored == null)
			explored = new StateSet(level.alive, level.cells);
		queue.clear();
		explored.clear();
		queue.addLast(new StateKey(s.agent, box));
		while (!queue.isEmpty()) {
			StateKey q = queue.removeFirst();
			visitor.init(q.agent);
			int reachableGoals = 0;
			int boxesOnGoals = Bits.count(q.box);
			// TODO configure visitor so that in case of a single goal room agent doesn't have to go outside all the time
			while (!visitor.done()) {
				int a = visitor.next();
				if (level.goal(a)) {
					reachableGoals += 1;
					assert boxesOnGoals + reachableGoals <= level.num_boxes;
					if (boxesOnGoals + reachableGoals == level.num_boxes)
						return false;
				}
				for (int b : level.moves[a]) {
					if (b >= level.alive || !Bits.test(q.box, b)) {
						if (!visitor.visited(b))
							visitor.add(b);
						continue;
					}
					int c = level.move(b, level.delta[a][b]);
					if (c == -1)
						continue;
					if (!level.goal(c)) {
						// box is removed
						if (--boxesOnGoals == 0)
							return false;
						Bits.clear(q.box, b);
						visitor.add(b);
						continue;
					}
					if (!Bits.test(q.box, c)) {
						// push box to c
						box = q.box.clone();
						Bits.clear(box, b);
						Bits.set(box, c);
						StateKey e = new StateKey(b, box);
						if (!explored.contains(e)) {
							explored.insert(e);
							queue.addLast(e);
						}
						continue;
					}
				}
			}
		}
		goalzone_deadlocks += 1;
		return true;
	}

	private boolean looksLikeAPush(int agent, int[] box) {
		for (int dir = 0; dir < 4; dir++) {
			int b = level.move(agent, dir);
			if (b == -1 || b >= level.alive || !Bits.test(box, b))
				continue;
			int s = level.rmove(agent, dir);
			if (s != -1 && (s >= level.alive || !Bits.test(box, s)))
				return true;
		}
		return false;
	}

	private boolean matchesPattern(int agent, int[] box, int num_boxes) {
		try (AutoTimer t = timerMatch.open()) {
			assert looksLikeAPush(agent, box);
			return pattern_index[agent].matches(box, num_boxes);
		}
	}

	static enum Result {
		Deadlock, NotFrozen, GoalZoneDeadlock,
	}

	// Note: modifies input array!
	private Result containsFrozenBoxes(int agent, int[] box, int num_boxes) {
		try (AutoTimer t = timerFrozen.open()) {
			if (num_boxes < 2)
				return Result.NotFrozen;
			int pushed_boxes = 0;
			int[] original_box = box.clone();

			visitor.init(agent);
			while (!visitor.done()) {
				int a = visitor.next();
				for (int b : level.moves[a]) {
					if (visitor.visited(b))
						continue;
					if (b >= level.alive || !Bits.test(box, b)) {
						// agent moves to B
						visitor.add(b);
						continue;
					}

					int dir = level.delta[a][b];
					int c = level.move(b, dir);
					if (c == -1 || c >= level.alive || Bits.test(box, c))
						continue;

					Bits.clear(box, b);
					Bits.set(box, c);
					boolean m = matchesPattern(b, box, num_boxes);
					Bits.clear(box, c);
					if (m) {
						Bits.set(box, b);
						continue;
					}

					// agent pushes box from B to C (and box disappears)
					if (--num_boxes == 1)
						return Result.NotFrozen;
					pushed_boxes += 1;
					visitor.init(b);
					break;
				}
			}

			if (!level.is_solved(box))
				return Result.Deadlock;

			// check that agent can reach all goals without boxes on them
			int reachable_free_goals = 0;
			for (int a = 0; a < level.alive; a++)
				if (level.goal(a) && visitor.visited(a))
					reachable_free_goals += 1;
			if (reachable_free_goals < pushed_boxes)
				return Result.GoalZoneDeadlock;

			if (!level.is_valid_level(p -> {
				if (p == agent)
					return level.goal(p) ? LowLevel.AgentGoal : LowLevel.Agent;
				if (p < level.alive && Bits.test(box, p))
					return LowLevel.Wall;
				if (p < level.alive && Bits.test(original_box, p))
					return level.goal(p) ? LowLevel.BoxGoal : LowLevel.Box;
				return level.goal(p) ? LowLevel.Goal : LowLevel.Space;
			})) {
				isvalidlevel_deadlocks += 1;
				return Result.GoalZoneDeadlock;
			}

			return Result.NotFrozen;
		}
	}

	// Looks for boxes not on goal that can't be moved
	// return true - means it is definitely a deadlock
	// return false - not sure if it is a deadlock
	private boolean checkInternal(State s, int num_boxes) {
		// TODO do we really need this?
		if (level.is_solved_fast(s.box))
			return false;
		if (matchesPattern(s.agent, s.box, num_boxes) || matchesGoalZonePattern(s.agent, s.box))
			return true;
		try (AutoTimer t = timerGoalzone.open()) {
			if (isGoalZoneDeadlock(s))
				return true;
		}

		int[] box = s.box.clone();
		Result result = containsFrozenBoxes(s.agent, box, num_boxes);
		if (result == Result.NotFrozen)
			return false;

		// if we have boxes frozen on goals, we can't store that pattern
		if (result == Result.GoalZoneDeadlock) {
			if (goal_zone_crap) {
				long unreachable_goals = 0;
				for (int a = 0; a < level.alive; a++)
					if (level.goal(a) && !visitor.visited(a))
						unreachable_goals |= Bits.mask(a);
				assert box[1] == 0; // TODO
				final GoalZonePattern z = new GoalZonePattern();
				z.agent = visitor.visited().clone();
				z.boxes_frozen_on_goals = box[0];
				z.unreachable_goals = unreachable_goals & ~box[0];
				level.low.print(i -> {
					int e = 0;
					if (!level.goal(i))
						return ' ';
					if (Bits.test(z.boxes_frozen_on_goals, i))
						e += 1;
					if (Bits.test(z.unreachable_goals, i))
						e += 2;
					if (e == 0)
						return ' ';
					return (char) ((int) '0' + e);
				});
				goal_zone_patterns.add(z);
			}
			return true;
		}

		num_boxes = Bits.count(box);
		boolean[] agent = visitor.visited().clone();

		// TODO there could be more than one minimal pattern from box
		try (AutoTimer t = timerMinimize.open()) {
			// try to removing boxes to generalize the pattern
			for (int i = 0; i < level.alive; i++)
				if (Bits.test(box, i) && !level.is_solved(box)) {
					int[] box_copy = box.clone();
					Bits.clear(box_copy, i);
					if (containsFrozenBoxes(s.agent, box_copy, num_boxes - 1) == Result.Deadlock) {
						Bits.clear(box, i);
						num_boxes -= 1;
						for (int z : level.moves[i])
							if (agent[z])
								agent[i] = true;
					}
				}
			// try moving agent to unreachable cells to generalize the pattern
			for (int i = 0; i < level.cells; i++)
				if (!agent[i] && (i >= level.alive || !Bits.test(box, i))
						&& containsFrozenBoxes(i, box.clone(), num_boxes) == Result.Deadlock)
					Util.updateOr(agent, visitor.visited());
		}

		// Save remaining state as a new deadlock pattern
		try (AutoTimer t = timerAdd.open()) {
			addPatternToFile(agent, box);
			histogram[num_boxes - 2] += 1;
			for (int b = 0; b < level.alive; b++)
				if (Bits.test(box, b))
					for (int a = 0; a < level.cells; a++)
						if (agent[a])
							pattern_index[a].add(box, num_boxes);
			patterns += 1;
		}

		try (AutoTimer t = timerCleanup.open()) {
			StateKeyPredicate predicate = (s_agent, s_box) -> matchesPattern(s_agent, s_box, level.num_boxes);
			open.remove_if(predicate);
			closed.remove_if(predicate);
		}
		return true;

	}

	private void addPatternToFile(boolean[] agent, int[] box) {
		char[] buffer = level.low.render(p -> {
			if (p < box.length * 32 && Bits.test(box, p))
				return '$';
			if (agent[p])
				return ' ';
			return '.';
		});
		Util.checkIOException(() -> pattern_file.write(buffer, 0, buffer.length));
		Util.checkIOException(() -> pattern_file.flush());
	}

	static class GoalZonePattern {
		boolean[] agent;
		long boxes_frozen_on_goals;
		long unreachable_goals;

		boolean match(int agent, int[] box, Level level) {
			/*assert box1 == 0;
			long test_boxes_on_goals = level.goal0 & box0;
			if (this.agent[agent] && test_boxes_on_goals == boxes_frozen_on_goals) {
				// level.print(i -> i == agent, i -> (box & mask(i)) != 0);
				return true;
			}*/
			return false;
			// return this.agent[agent] && (test_boxes_on_goals |
			// boxes_frozen_on_goals) == test_boxes_on_goals
			// && (test_boxes_on_goals & unreachable_goals) == 0;
		}
	}

	ArrayList<GoalZonePattern> goal_zone_patterns = new ArrayList<GoalZonePattern>();

	boolean matchesGoalZonePattern(int agent, int[] box) {
		for (GoalZonePattern z : goal_zone_patterns)
			if (z.match(agent, box, level))
				return true;
		return false;
	}

	private final static boolean goal_zone_crap = false;

	boolean check(State s) {
		try (AutoTimer t = timer.open()) {
			assert !s.is_initial();
			if (LevelUtil.is_reversible_push(s, level)) {
				trivial += 1;
				return false;
			}
			if (checkInternal(s, level.num_boxes)) {
				deadlocks += 1;
				return true;
			}
			non_deadlocks += 1;
			return false;
		}
	}
}