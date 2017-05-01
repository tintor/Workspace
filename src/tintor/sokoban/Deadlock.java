package tintor.sokoban;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.Bits;
import tintor.common.InstrumentationAgent;
import tintor.common.Timer;
import tintor.common.Util;
import tintor.common.Visitor;

class LevelUtil {
	// TODO be more strict: try to go around box, push it back and return to original agent position
	static boolean is_reverseable_push(State s, Level level) {
		assert s.is_push();
		int c = level.move(level.move(s.agent(), s.dir), s.dir);
		if (c == -1 || s.box(c))
			return false;
		return is_cell_reachable(c, s, level);
	}

	// can agent move to C without pushing any box?
	static boolean is_cell_reachable(int c, State s, Level level) {
		int[] dist = level.agent_distance[c];
		int[] next = new int[4];

		Visitor visitor = level.visitor.init(s.agent());
		for (int a : visitor) {
			int count = 0;
			for (int b : level.moves[a]) {
				if (visitor.visited(b) || s.box(b))
					continue;
				if (b == c)
					return true;
				next[count++] = b;
			}
			nextToVisitor(visitor, dist, next, count);
		}
		return false;
	}

	static void nextToVisitor(Visitor visitor, int[] dist, int[] next, int e) {
		// sort 'next' by agent distance and add it to visitor
		if (e == 0)
			return;
		if (e == 1) {
			visitor.add(next[0]);
			return;
		}
		if (e == 2) {
			if (dist[next[1]] < dist[next[0]]) {
				visitor.add(next[1]);
				visitor.add(next[0]);
			} else {
				visitor.add(next[0]);
				visitor.add(next[1]);
			}
			return;
		}
		if (e == 3) {
			int m = 0;
			if (dist[next[1]] < dist[next[0]])
				m = 1;
			if (dist[next[2]] < dist[next[m]])
				m = 2;
			visitor.add(next[m]);
			next[m] = next[0];

			m = 1;
			if (dist[next[2]] < dist[next[1]])
				m = 2;
			visitor.add(next[m]);
			next[m] = next[1];

			visitor.add(next[2]);
			return;
		}
		for (int i = 0; i < e; i++) {
			int m = i;
			for (int j = i + 1; j < e; j++)
				if (dist[next[j]] < dist[next[m]])
					m = j;
			visitor.add(next[m]);
			next[m] = next[i];
		}
	}
}

class Deadlock {
	static class Patterns {
		// TODO store them in single array for faster lookups
		public long[] array_box0 = new long[4];
		public long[] array_box1;
		public int size;
		public int[] end;

		public Patterns(int level_num_boxes, int level_alive) {
			end = new int[level_num_boxes + 1];
			if (level_alive > 64)
				array_box1 = new long[4];
		}

		public void add(int[] box, int num_boxes) {
			if (box.length > 4)
				throw new Error();
			if (array_box0.length == size) {
				array_box0 = Arrays.copyOf(array_box0, array_box0.length * 2);
				if (array_box1 != null)
					array_box1 = Arrays.copyOf(array_box1, array_box1.length * 2);
			}

			long box0 = box[0];
			if (box.length > 1)
				box0 |= ((long) box[1]) << 32;
			long box1 = 0;
			if (box.length > 2)
				box1 = box[2];
			if (box.length > 3)
				box0 |= ((long) box[3]) << 32;

			int x = end[num_boxes];
			System.arraycopy(array_box0, x, array_box0, x + 1, size - x);
			array_box0[x] = box0;
			if (array_box1 != null) {
				System.arraycopy(array_box1, x, array_box1, x + 1, size - x);
				array_box1[x] = box1;
			}
			size += 1;
			for (int i = num_boxes; i < end.length; i++)
				end[i] += 1;
		}

		public boolean matches(int agent, int[] box, int num_boxes) {
			if (box.length > 4)
				throw new Error();

			long box0 = box[0];
			if (box.length > 1)
				box0 |= ((long) box[1]) << 32;
			long box1 = 0;
			if (box.length > 2)
				box1 = box[2];
			if (box.length > 3)
				box0 |= ((long) box[3]) << 32;

			if (array_box1 == null || box1 == 0) {
				for (int i = 0; i < end[num_boxes]; i++)
					if ((box0 | array_box0[i]) == box0)
						return true;
				return false;
			}

			for (int i = 0; i < end[num_boxes]; i++)
				if ((box0 | array_box0[i]) == box0 && (box1 | array_box1[i]) == box1)
					return true;
			return false;
		}
	}

	private final Visitor visitor;
	private final Patterns[][] pattern_index;
	int patterns;
	int[] histogram;
	private final Level level;
	boolean enable_frozen_boxes = true;
	private BufferedWriter pattern_file;

	private final Timer timer = new Timer();
	private final Timer timerFrozen = new Timer();
	private final Timer timerMatch = new Timer();
	private int deadlocks = 0;
	private int non_deadlocks = 0;
	private int reversable = 0;

	long report(int cycles) {
		timer.total /= cycles;
		timerFrozen.total /= cycles;
		timerMatch.total /= cycles;

		long other = timer.total - timerFrozen.total - timerMatch.total;
		System.out.printf("dead:%s live:%s rev:%s db:%s db2:%s memory:%s\n", Util.human(deadlocks),
				Util.human(non_deadlocks), Util.human(reversable), Util.human(patterns),
				Util.human(goal_zone_patterns.size()), Util.human(InstrumentationAgent.deepSizeOf(pattern_index)));
		System.out.printf("  [frozen:%s match:%s other:%s]\n", timerFrozen.clear(), timerMatch.clear(), other);
		return timer.clear();
	}

	Deadlock(Level level) {
		this.level = level;
		visitor = new Visitor(level.cells);
		pattern_index = new Patterns[4][level.alive];
		for (int d = 0; d < 4; d++)
			for (int i = 0; i < level.alive; i++)
				pattern_index[d][i] = new Patterns(level.num_boxes, level.alive);
		histogram = new int[level.num_boxes - 1];

		try {
			pattern_file = new BufferedWriter(new FileWriter("patterns.txt", false));
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	private boolean matchesPattern(int moved_box, int dir, int agent, int[] box, int num_boxes) {
		try (Timer t = timerMatch.start()) {
			assert 0 <= agent && agent < level.cells;
			assert 0 <= moved_box && moved_box < level.alive : moved_box + " vs " + level.alive;
			return pattern_index[dir][moved_box].matches(agent, box, num_boxes);
		}
	}

	private boolean matchesPattern(int agent, int dir, int[] box, int num_boxes) {
		int b = level.move(agent, dir);
		if (0 <= b && b < box.length * 32 && Bits.test(box, b))
			return matchesPattern(b, dir, agent, box, num_boxes);
		return false;
	}

	static enum Result {
		Deadlock, NotFrozen, GoalZoneDeadlock,
	}

	// Note: modifies input array!
	private Result containsFrozenBoxes(int agent, int[] box, int num_boxes) {
		try (Timer t = timerFrozen.start()) {
			if (num_boxes < 2)
				return Result.NotFrozen;
			int pushed_boxes = 0;
			int[] original_box = box.clone();

			for (int a : visitor.init(agent))
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
					if (c == -1 || c >= level.alive)
						continue;
					if (!Bits.test(box, c)) {
						Bits.clear(box, b);
						Bits.set(box, c);
						timerFrozen.stop();
						boolean m = matchesPattern(c, dir, b, box, num_boxes);
						timerFrozen.start();
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
			}))
				return Result.GoalZoneDeadlock;

			return Result.NotFrozen;
		}
	}

	// Looks for boxes not on goal that can't be moved
	// return true - means it is definitely a deadlock
	// return false - not sure if it is a deadlock
	private boolean checkInternal(State s, int num_boxes) {
		if (level.is_solved(s.box))
			return false;
		if (matchesPattern(s.agent(), s.dir, s.box, num_boxes) || matchesGoalZonePattern(s.agent(), s.box))
			return true;
		if (!enable_frozen_boxes)
			return false;

		int[] box = s.box.clone();
		Result result = containsFrozenBoxes(s.agent(), box, num_boxes);
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

		// try to removing boxes to generalize the pattern
		for (int i = 0; i < level.alive; i++)
			if (Bits.test(box, i) && !level.is_solved(box)) {
				int[] box_copy = box.clone();
				Bits.clear(box_copy, i);
				if (containsFrozenBoxes(s.agent(), box_copy, num_boxes - 1) == Result.Deadlock) {
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

		// Save remaining state as a new deadlock pattern
		addPatternToFile(agent, box);
		histogram[num_boxes - 2] += 1;
		for (int b = 0; b < level.alive; b++)
			if (Bits.test(box, b))
				for (int a : level.moves[b])
					if (agent[a])
						pattern_index[level.delta[a][b]][b].add(box, num_boxes);
		patterns += 1;
		return true;
	}

	private void addPatternToFile(boolean[] agent, int[] box) {
		char[] buffer = level.low.render(p -> {
			if (agent[p])
				return level.goal(p) ? LowLevel.AgentGoal : LowLevel.Agent;
			if (p < box.length * 32 && Bits.test(box, p))
				return level.goal(p) ? LowLevel.BoxGoal : LowLevel.Box;
			return level.goal(p) ? LowLevel.Goal : LowLevel.Space;
		});
		try {
			pattern_file.write(buffer, 0, buffer.length);
			pattern_file.newLine();
		} catch (IOException e) {
			throw new Error(e);
		}
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
		try (Timer t = timer.start()) {
			if (s.is_push() && LevelUtil.is_reverseable_push(s, level)) {
				reversable += 1;
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