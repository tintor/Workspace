package tintor.sokoban;

import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.Bits;
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

		public void add(long box0, int num_boxes) {
			assert array_box1 == null;
			if (array_box0.length == size)
				array_box0 = Arrays.copyOf(array_box0, array_box0.length * 2);

			int x = end[num_boxes];
			System.arraycopy(array_box0, x, array_box0, x + 1, size - x);
			array_box0[x] = box0;
			size += 1;
			for (int i = num_boxes; i < end.length; i++)
				end[i] += 1;
		}

		public void add(long box0, long box1, int num_boxes) {
			if (array_box0.length == size) {
				array_box0 = Arrays.copyOf(array_box0, array_box0.length * 2);
				array_box1 = Arrays.copyOf(array_box1, array_box1.length * 2);
			}

			int x = end[num_boxes];
			System.arraycopy(array_box0, x, array_box0, x + 1, size - x);
			System.arraycopy(array_box1, x, array_box1, x + 1, size - x);
			array_box0[x] = box0;
			array_box1[x] = box1;
			size += 1;
			for (int i = num_boxes; i < end.length; i++)
				end[i] += 1;
		}

		public boolean matches(int agent, long box0, int num_boxes) {
			for (int i = 0; i < end[num_boxes]; i++)
				if ((box0 | array_box0[i]) == box0)
					return true;
			return false;
		}

		public boolean matches(int agent, long box0, long box1, int num_boxes) {
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

		timer.total -= timerFrozen.total + timerMatch.total;
		System.out.printf("dead:%s live:%s rev:%s db:%s db2:%s [frozen:%s match:%s other:%s]\n", Util.human(deadlocks),
				Util.human(non_deadlocks), Util.human(reversable), Util.human(patterns),
				Util.human(goal_zone_patterns.size()), timerFrozen.clear(), timerMatch.clear(), timer.total);
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
	}

	private boolean matchesPattern(int agent, int dir, long box, int num_boxes) {
		if (dir >= 0) {
			int b = level.move(agent, dir);
			// TODO: replace -1 with high positive value to avoid negative check
			if (0 <= b && b < 64 && Bits.test(box, b))
				return matchesPattern(b, dir, agent, box, num_boxes);
		}
		return false;
	}

	private boolean matchesPattern(int agent, int dir, long box0, long box1, int num_boxes) {
		if (dir >= 0) {
			int b = level.move(agent, dir);
			if (0 <= b && b < 128 && Bits.test(box0, box1, b))
				return matchesPattern(b, dir, agent, box0, box1, num_boxes);
		}
		return false;
	}

	private boolean matchesPattern(int moved_box, int dir, int agent, long box0, int num_boxes) {
		try (Timer t = timerMatch.start()) {
			assert 0 <= agent && agent < level.cells;
			assert 0 <= moved_box && moved_box < level.alive : moved_box + " vs " + level.alive;
			return pattern_index[dir][moved_box].matches(agent, box0, num_boxes);
		}
	}

	private boolean matchesPattern(int moved_box, int dir, int agent, long box0, long box1, int num_boxes) {
		try (Timer t = timerMatch.start()) {
			assert 0 <= agent && agent < level.cells;
			assert 0 <= moved_box && moved_box < level.alive : moved_box + " vs " + level.alive;
			return pattern_index[dir][moved_box].matches(agent, box0, box1, num_boxes);
		}
	}

	private static void set(long[] box, int index) {
		if (index < 64)
			box[0] = Bits.set(box[0], index);
		else
			box[1] = Bits.set(box[1], index - 64);
	}

	private static void clear(long[] box, int index) {
		if (index < 64)
			box[0] = Bits.clear(box[0], index);
		else
			box[1] = Bits.clear(box[1], index - 64);
	}

	// Note: modifies input array!
	private boolean containsFrozenBoxes(int agent, long[] box, int num_boxes) {
		try (Timer t = timerFrozen.start()) {
			if (num_boxes < 2)
				return false;
			int pushed_boxes = 0;
			for (int a : visitor.init(agent))
				for (int b : level.moves[a]) {
					if (visitor.visited(b))
						continue;
					if (b >= level.alive || !Bits.test(box[0], box[1], b)) {
						// agent moves to B
						visitor.add(b);
						continue;
					}

					int dir = level.delta[a][b];
					int c = level.move(b, dir);
					if (c == -1 || c >= level.alive)
						continue;
					if (!Bits.test(box[0], box[1], c)) {
						clear(box, b);

						set(box, c);
						timerFrozen.stop();
						boolean m = matchesPattern(c, dir, b, box[0], box[1], num_boxes);
						timerFrozen.start();
						clear(box, c);
						if (m) {
							set(box, b);
							continue;
						}

						// agent pushes box from B to C (and box disappears)
						if (--num_boxes == 1)
							return false;
						pushed_boxes += 1;
						visitor.init(b);
						break;
					}
				}

			if (!level.is_solved(box[0], box[1]))
				return true;

			// remaining boxes are on goals => check that agent can reach all
			// goals without boxes on them
			// TODO make it stronger condition: check that all original boxes
			// (that were removed) can be pushed to some goal (one by one)
			int reachable_free_goals = 0;
			for (int a = 0; a < level.alive; a++)
				if (level.goal(a) && visitor.visited(a))
					reachable_free_goals += 1;
			if (reachable_free_goals < pushed_boxes)
				return true;

			return false;
		}
	}

	// returns: 0 if not frozen, else bitset of frozen boxes
	private long containsFrozenBoxes64(int agent, long box, int num_boxes) {
		try (Timer t = timerFrozen.start()) {
			assert num_boxes == Long.bitCount(box);
			if (num_boxes < 2)
				return 0;
			int pushed_boxes = 0;
			long original_boxes = box;
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
					if (c == -1 || c >= level.alive || Bits.test(box, c))
						continue;

					box = Bits.clear(box, b);
					timerFrozen.stop();
					boolean m = matchesPattern(c, dir, b, Bits.set(box, c), num_boxes);
					// boolean m2 = matchesGoalZonePattern(b, box |
					// mask(c));
					timerFrozen.start();
					if (m) {
						{
							// level.print(i -> agent == i, i ->
							// (original_boxes & mask(i)) != 0);
						}
						box = Bits.set(box, b);
						continue;
					}

					// agent pushes box from B to C (and box disappears)
					if (--num_boxes == 1)
						return 0;
					pushed_boxes += 1;
					visitor.init(b);
					break;
				}

			if (!level.is_solved(box))
				return box;

			// remaining boxes are on goals => check that agent can reach all
			// goals without boxes on them
			int reachable_free_goals = 0;
			for (int a = 0; a < level.alive; a++)
				if (level.goal(a))
					if (visitor.visited(a))
						reachable_free_goals += 1;
			if (reachable_free_goals < pushed_boxes)
				return box; // goal zone deadlock

			// for every free goal there must be a box that can be pushed to it
			if (goal_zone_deadlock_check) {
				for (int a = 0; a < level.alive; a++)
					if (level.goal(a) && !Bits.test(box, a)) {
						boolean deadlock = true;
						Visitor visitor = new Visitor(level.alive);
						visitor.add(a);
						for (int b : visitor) {
							for (int c : level.moves[b]) {
								if (c >= level.alive || visitor.visited(c) || Bits.test(box, c))
									continue;
								int d = level.move(c, level.delta[b][c]);
								if (d == -1)
									continue;
								if (Bits.test(original_boxes, c)) {
									visitor.init();
									deadlock = false;
									break;
								}
								visitor.add(c);
							}
						}
						if (deadlock)
							return box; // goal zone deadlock
					}
			}

			return 0;
		}
	}

	final static boolean goal_zone_deadlock_check = false;

	static long[] remove(long[] a, int i) {
		if (i < 64)
			a[0] = Bits.clear(a[0], i);
		else
			a[1] = Bits.clear(a[1], i - 64);
		return a;
	}

	// Looks for boxes not on goal that can't be moved
	// return true - means it is definitely a deadlock
	// return false - not sure if it is a deadlock
	private boolean checkInternal(State s, int num_boxes) {
		if (level.is_solved(s.box0, s.box1))
			return false;
		if (matchesPattern(s.agent(), s.dir, s.box0, s.box1, num_boxes))
			return true;
		if (!enable_frozen_boxes)
			return false;

		long[] box = new long[] { s.box0, s.box1 };
		if (!containsFrozenBoxes(s.agent(), box, num_boxes))
			return false;
		// if we have boxes frozen on goals, we can't store that pattern
		if (level.is_solved(box[0], box[1]))
			return true;

		num_boxes = Long.bitCount(box[0]) + Long.bitCount(box[1]);
		boolean[] agent = visitor.visited().clone();

		// try to removing boxes to generalize the pattern
		for (int i = 0; i < level.alive; i++)
			if (Bits.test(box[0], box[1], i) && !level.is_solved(box[0], box[1]))
				if (containsFrozenBoxes(s.agent(), remove(box.clone(), i), num_boxes - 1)) {
					if (i < 64)
						box[0] = Bits.clear(box[0], i);
					else
						box[1] = Bits.clear(box[1], i - 64);
					num_boxes -= 1;
					for (int z : level.moves[i])
						if (agent[z])
							agent[i] = true;
				}
		// try moving agent to unreachable cells to generalize the pattern
		for (int i = 0; i < level.cells; i++)
			if (!agent[i] && !Bits.test(box[0], box[1], i) && containsFrozenBoxes(i, box.clone(), num_boxes))
				Util.updateOr(agent, visitor.visited());

		// Save remaining state as a new deadlock pattern
		addPattern(box[0], box[1], agent, num_boxes);
		return true;
	}

	static Object lock = new Object();

	static class GoalZonePattern {
		boolean[] agent;
		long boxes_frozen_on_goals;
		long unreachable_goals;

		boolean match(int agent, long box, Level level) {
			long test_boxes_on_goals = level.goal0 & box;
			if (this.agent[agent] && test_boxes_on_goals == boxes_frozen_on_goals) {
				// level.print(i -> i == agent, i -> (box & mask(i)) != 0);
				return true;
			}
			return false;
			// return this.agent[agent] && (test_boxes_on_goals |
			// boxes_frozen_on_goals) == test_boxes_on_goals
			// && (test_boxes_on_goals & unreachable_goals) == 0;
		}
	}

	ArrayList<GoalZonePattern> goal_zone_patterns = new ArrayList<GoalZonePattern>();

	boolean matchesGoalZonePattern(int agent, long box) {
		for (GoalZonePattern z : goal_zone_patterns)
			if (z.match(agent, box, level))
				return true;
		return false;
	}

	private boolean checkInternal64(State s, long box, int num_boxes) {
		if (level.is_solved(box, 0))
			return false;
		if (matchesPattern(s.agent(), s.dir, box, num_boxes) || matchesGoalZonePattern(s.agent(), box))
			return true;
		if (!enable_frozen_boxes)
			return false;

		box = containsFrozenBoxes64(s.agent(), box, num_boxes);
		if (box == 0)
			return false;
		// if we have boxes frozen on goals, we can't store that pattern
		if (level.is_solved(box, 0)) {
			long unreachable_goals = 0;
			for (int a = 0; a < level.alive; a++)
				if (level.goal(a) && !visitor.visited(a))
					unreachable_goals |= Bits.mask(a);
			final GoalZonePattern z = new GoalZonePattern();
			z.agent = visitor.visited().clone();
			z.boxes_frozen_on_goals = box;
			z.unreachable_goals = unreachable_goals & ~box;
			/*
			 * level.io.print(i -> { int e = 0; if (!level.goal(i)) return ' ';
			 * if ((z.boxes_frozen_on_goals & mask(i)) != 0) e += 1; if
			 * ((z.unreachable_goals & mask(i)) != 0) e += 2; if (e == 0) return
			 * ' '; return (char) ((int) '0' + e); });
			 */
			goal_zone_patterns.add(z);
			return true;
		}

		num_boxes = Long.bitCount(box);
		boolean[] agent = visitor.visited().clone();

		// try to remove boxes to generalize the pattern
		for (int i = 0; i < level.alive; i++)
			if (Bits.test(box, i) && !level.is_solved(Bits.clear(box, i), 0))
				if (containsFrozenBoxes64(s.agent(), Bits.clear(box, i), num_boxes - 1) != 0) {
					box = Bits.clear(box, i);
					num_boxes -= 1;
					for (int z : level.moves[i])
						if (agent[z])
							agent[i] = true;
				}
		// try moving agent to unreachable cells to generalize the pattern
		for (int i = 0; i < level.cells; i++)
			if (!agent[i] && (i >= level.alive || !Bits.test(box, i)) && containsFrozenBoxes64(i, box, num_boxes) != 0)
				Util.updateOr(agent, visitor.visited());

		// Save remaining state as a new deadlock pattern
		addPattern(box, 0, agent, num_boxes);
		return true;
	}

	void addPattern(long box0, long box1, boolean[] agent, int num_boxes) {
		histogram[num_boxes - 2] += 1;
		for (int b = 0; b < level.alive; b++)
			if (Bits.test(box0, box1, b))
				for (int a : level.moves[b])
					if (agent[a]) {
						Patterns p = pattern_index[level.delta[a][b]][b];
						if (level.alive > 64)
							p.add(box0, box1, num_boxes);
						else
							p.add(box0, num_boxes);
					}
		patterns += 1;
	}

	boolean check(State s) {
		try (Timer t = timer.start()) {
			if (s.is_push() && LevelUtil.is_reverseable_push(s, level)) {
				reversable += 1;
				return false;
			}
			if (level.alive <= 64 ? checkInternal64(s, s.box0, level.num_boxes) : checkInternal(s, level.num_boxes)) {
				deadlocks += 1;
				return true;
			}
			non_deadlocks += 1;
			return false;
		}
	}
}