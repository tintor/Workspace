package tintor.sokoban;

import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.Timer;
import tintor.common.Util;
import tintor.common.Visitor;

class LevelUtil {
	// can agent move to C without pushing any box?
	static boolean is_cell_reachable(int c, State s, Level level) {
		int[] dist = level.agent_distance[c];
		int[] next = new int[4];

		Visitor visitor = level.visitor;
		for (int a : visitor.init(s.agent)) {
			int e = 0;
			for (int b : level.moves[a]) {
				if (visitor.visited(b) || s.box(b))
					continue;
				if (b == c)
					return true;
				next[e++] = b;
			}
			// sort 'next' by agent distance and add it to visitor
			if (e == 0)
				continue;
			if (e == 1) {
				visitor.add(next[0]);
				continue;
			}
			if (e == 2) {
				if (dist[next[1]] < dist[next[0]]) {
					visitor.add(next[1]);
					visitor.add(next[0]);
				} else {
					visitor.add(next[0]);
					visitor.add(next[1]);
				}
				continue;
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
				continue;
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
		return false;
	}

	static boolean is_reverseable_push(State s, Level level) {
		assert s.is_push;
		int c = level.move(level.move(s.agent, s.dir), s.dir);
		if (c == -1 || s.box(c))
			return false;
		return is_cell_reachable(c, s, level);
	}
}

class Deadlock {
	static class Patterns {
		short[][] array_box = new short[4][];
		long[] array_box0 = new long[4];
		int size;
		int[] end;

		Patterns(int level_num_boxes) {
			end = new int[level_num_boxes + 1];
		}

		void add(short[] box, long box0, int num_boxes) {
			if (array_box.length == size) {
				array_box = Arrays.copyOf(array_box, array_box.length * 2);
				array_box0 = Arrays.copyOf(array_box0, array_box0.length * 2);
			}

			int x = end[num_boxes];
			System.arraycopy(array_box, x, array_box, x + 1, size - x);
			System.arraycopy(array_box0, x, array_box0, x + 1, size - x);
			array_box[x] = box;
			array_box0[x] = box0;
			size += 1;
			for (int i = num_boxes; i < end.length; i++)
				end[i] += 1;
		}

		boolean matches64(int agent, long box0, int num_boxes) {
			for (int i = 0; i < end[num_boxes]; i++)
				if ((box0 | array_box0[i]) == box0)
					return true;
			return false;
		}

		boolean matches(int agent, boolean[] box, int num_boxes) {
			if (box.length <= 64) {
				long box0 = Util.compress(box);
				return matches64(agent, box0, num_boxes);
			}

			for (int i = 0; i < end[num_boxes]; i++)
				if (isSubsetOf(array_box[i], box))
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
		int level_num_boxes = Util.count(level.start.box);
		for (int d = 0; d < 4; d++)
			for (int i = 0; i < level.alive; i++)
				pattern_index[d][i] = new Patterns(level_num_boxes);
		histogram = new int[Util.count(level.start.box) - 2 + 1];
	}

	private boolean matchesPattern(State s, int num_boxes) {
		if (s.dir >= 0) {
			int b = level.move(s.agent, s.dir);
			if (b != -1 && s.box(b))
				return matchesPattern(b, s.dir, s.agent, s.box, num_boxes);
		}
		return false;
	}

	private boolean matchesPattern64(State s, long box, int num_boxes) {
		if (s.dir >= 0) {
			int b = level.move(s.agent, s.dir);
			if (b != -1 && s.box(b))
				return matchesPattern64(b, s.dir, s.agent, box, num_boxes);
		}
		return false;
	}

	private boolean matchesPattern(int moved_box, int dir, int agent, boolean[] box, int num_boxes) {
		try (Timer t = timerMatch.start()) {
			assert 0 <= agent && agent < level.cells;
			assert box.length == level.alive;
			return pattern_index[dir][moved_box].matches(agent, box, num_boxes);
		}
	}

	private boolean matchesPattern64(int moved_box, int dir, int agent, long box0, int num_boxes) {
		try (Timer t = timerMatch.start()) {
			assert 0 <= agent && agent < level.cells;
			return pattern_index[dir][moved_box].matches64(agent, box0, num_boxes);
		}
	}

	// Note: modifies input array!
	private boolean containsFrozenBoxes(int agent, boolean[] box, int num_boxes) {
		try (Timer t = timerFrozen.start()) {
			if (num_boxes < 2)
				return false;
			int pushed_boxes = 0;
			for (int a : visitor.init(agent))
				for (int b : level.moves[a]) {
					if (visitor.visited(b))
						continue;
					if (b >= box.length || !box[b]) {
						// agent moves to B
						visitor.add(b);
						continue;
					}

					int dir = level.delta[a][b];
					int c = level.move(b, dir);
					if (c == -1 || c >= box.length)
						continue;
					if (!box[c]) {
						box[b] = false;
						box[c] = true;
						timerFrozen.stop();
						boolean m = matchesPattern(c, dir, b, box, num_boxes);
						timerFrozen.start();
						if (m) {
							box[b] = true;
							box[c] = false;
							continue;
						}

						// agent pushes box from B to C (and box disappears)
						if (--num_boxes == 1)
							return false;
						pushed_boxes += 1;
						box[c] = false;
						visitor.init(b);
						break;
					}
				}

			if (!level.is_solved(box))
				return true;

			// remaining boxes are on goals => check that agent can reach all goals without boxes on them
			// TODO make it stronger condition: check that all original boxes (that were removed) can be pushed to some goal (one by one)
			int reachable_free_goals = 0;
			for (int a = 0; a < level.alive; a++)
				if (level.goal[a] && visitor.visited(a))
					reachable_free_goals += 1;
			if (reachable_free_goals < pushed_boxes)
				return true;

			return false;
		}
	}

	private static long mask(int i) {
		return 1l << i;
	}

	// returns: 0 if not frozen, else bitset of frozen boxes
	private long containsFrozenBoxes64(int agent, long box, int num_boxes) {
		try (Timer t = timerFrozen.start()) {
			assert num_boxes == Long.bitCount(box);
			if (num_boxes < 2)
				return 0;
			int pushed_boxes = 0;
			//long original_boxes = box;
			for (int a : visitor.init(agent))
				for (int b : level.moves[a]) {
					if (visitor.visited(b))
						continue;
					if (b >= level.alive || (box & mask(b)) == 0) {
						// agent moves to B
						visitor.add(b);
						continue;
					}

					int dir = level.delta[a][b];
					int c = level.move(b, dir);
					if (c == -1 || c >= level.alive)
						continue;
					if ((box & mask(c)) == 0) {
						box &= ~mask(b);
						timerFrozen.stop();
						boolean m = matchesPattern64(c, dir, b, box | mask(c), num_boxes);
						//boolean m2 = matchesGoalZonePattern(b, box | mask(c));
						timerFrozen.start();
						if (m) {
							{
								//level.print(i -> agent == i, i -> (original_boxes & mask(i)) != 0);
							}
							box |= mask(b);
							continue;
						}

						// agent pushes box from B to C (and box disappears)
						if (--num_boxes == 1)
							return 0;
						pushed_boxes += 1;
						visitor.init(b);
						break;
					}
				}

			if (!level.is_solved(box))
				return box;

			// remaining boxes are on goals => check that agent can reach all goals without boxes on them
			int reachable_free_goals = 0;
			for (int a = 0; a < level.alive; a++)
				if (level.goal[a])
					if (visitor.visited(a))
						reachable_free_goals += 1;
			if (reachable_free_goals < pushed_boxes)
				return box; // goal zone deadlock

			// for every free goal there must be a box that can be pushed to it
			/*for (int a = 0; a < level.alive; a++)
				if (level.goal[a] && (box & mask(a)) == 0) {
					boolean deadlock = true;
					Visitor visitor = new Visitor(level.alive);
					visitor.add(a);
					for (int b : visitor) {
						for (byte dir = 0; dir < 4; dir++) {
							int c = level.move(b, dir);
							if (c == -1 || c >= level.alive || visitor.visited(c) || (box & mask(c)) != 0)
								continue;
							int d = level.move(c, dir);
							if (d == -1)
								continue;
							if ((original_boxes & mask(c)) != 0) {
								visitor.init();
								deadlock = false;
								break;
							}
							visitor.add(c);
						}
					}
					if (deadlock)
						return box; // goal zone deadlock
				}*/

			return 0;
		}
	}

	static boolean[] remove(boolean[] a, int i) {
		a[i] = false;
		return a;
	}

	// Looks for boxes not on goal that can't be moved
	// return true - means it is definitely a deadlock
	// return false - not sure if it is a deadlock
	private boolean checkInternal(State s, int num_boxes) {
		if (level.is_solved(s.box))
			return false;
		if (matchesPattern(s, num_boxes))
			return true;
		if (!enable_frozen_boxes)
			return false;

		boolean[] box = s.box.clone();
		if (!containsFrozenBoxes(s.agent, box, num_boxes))
			return false;
		// if we have boxes frozen on goals, we can't store that pattern
		if (level.is_solved(box))
			return true;

		num_boxes = Util.count(box);
		boolean[] agent = visitor.visited().clone();

		// try to removing boxes to generalize the pattern
		for (int i = 0; i < box.length; i++)
			if (box[i] && !level.is_solved(box))
				if (containsFrozenBoxes(s.agent, remove(box.clone(), i), num_boxes - 1)) {
					box[i] = false;
					num_boxes -= 1;
					for (byte dir = 0; dir < 4; dir++) {
						int z = level.move(i, dir);
						if (z != -1 && agent[z])
							agent[i] = true;
					}
				}
		// try moving agent to unreachable cells to generalize the pattern
		for (int i = 0; i < level.cells; i++)
			if (!agent[i] && (i >= level.alive || !box[i]) && containsFrozenBoxes(i, box.clone(), num_boxes))
				Util.updateOr(agent, visitor.visited());

		// Save remaining state as a new deadlock pattern
		addPattern(compress(box), box.length <= 64 ? Util.compress(box) : 0, agent, compress(box));
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
				//level.print(i -> i == agent, i -> (box & mask(i)) != 0);
				return true;
			}
			return false;
			//return this.agent[agent] && (test_boxes_on_goals | boxes_frozen_on_goals) == test_boxes_on_goals
			//		&& (test_boxes_on_goals & unreachable_goals) == 0;
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
		if (level.is_solved(box))
			return false;
		if (matchesPattern64(s, box, num_boxes) || matchesGoalZonePattern(s.agent, box))
			return true;
		if (!enable_frozen_boxes)
			return false;

		box = containsFrozenBoxes64(s.agent, box, num_boxes);
		if (box == 0)
			return false;
		// if we have boxes frozen on goals, we can't store that pattern
		if (level.is_solved(box)) {
			long unreachable_goals = 0;
			for (int a = 0; a < level.alive; a++)
				if (level.goal[a] && !visitor.visited(a)) {
					unreachable_goals |= mask(a);
				}
			final GoalZonePattern z = new GoalZonePattern();
			z.agent = visitor.visited().clone();
			z.boxes_frozen_on_goals = box;
			z.unreachable_goals = unreachable_goals & ~box;
			/*level.io.print(i -> {
				int e = 0;
				if (!level.goal(i))
					return ' ';
				if ((z.boxes_frozen_on_goals & mask(i)) != 0)
					e += 1;
				if ((z.unreachable_goals & mask(i)) != 0)
					e += 2;
				if (e == 0)
					return ' ';
				return (char) ((int) '0' + e);
			});*/
			goal_zone_patterns.add(z);
			return true;
		}

		num_boxes = Long.bitCount(box);
		boolean[] agent = visitor.visited().clone();

		// try to removing boxes to generalize the pattern
		for (int i = 0; i < level.alive; i++)
			if ((box & mask(i)) != 0 && !level.is_solved(box & ~mask(i)))
				if (containsFrozenBoxes64(s.agent, box & ~mask(i), num_boxes - 1) != 0) {
					box &= ~mask(i);
					num_boxes -= 1;
					for (byte dir = 0; dir < 4; dir++) {
						int z = level.move(i, dir);
						if (z != -1 && agent[z])
							agent[i] = true;
					}
				}
		// try moving agent to unreachable cells to generalize the pattern
		for (int i = 0; i < level.cells; i++)
			if (!agent[i] && (i >= level.alive || (box & mask(i)) == 0)
					&& containsFrozenBoxes64(i, box, num_boxes) != 0)
				Util.updateOr(agent, visitor.visited());

		// Save remaining state as a new deadlock pattern
		addPattern(null, box, agent, compress64(box, level.alive));
		return true;
	}

	void addPattern(short[] box, long box0, boolean[] agent, short[] ebox) {
		histogram[ebox.length - 2] += 1;
		for (short b : ebox)
			for (byte dir = 0; dir < 4; dir++) {
				int a = level.move(b, Level.reverseDir(dir));
				if (a != -1 && agent[a])
					pattern_index[dir][b].add(box, box0, ebox.length);
			}
		patterns += 1;
	}

	boolean check(State s) {
		try (Timer t = timer.start()) {
			if (s.is_push && LevelUtil.is_reverseable_push(s, level)) {
				reversable += 1;
				return false;
			}

			int num_boxes = Util.count(s.box);
			if (level.alive <= 64 ? checkInternal64(s, Util.compress(s.box), num_boxes) : checkInternal(s, num_boxes)) {
				deadlocks += 1;
				return true;
			}
			non_deadlocks += 1;
			return false;
		}
	}

	private static short[] compress(boolean[] box) {
		int c = 0;
		for (boolean e : box)
			if (e)
				c += 1;
		short[] v = new short[c];
		int w = 0;
		for (short i = 0; i < box.length; i++)
			if (box[i])
				v[w++] = i;
		return v;
	}

	private static short[] compress64(long box, int length) {
		int c = Long.bitCount(box);
		short[] v = new short[c];
		int w = 0;
		for (short i = 0; i < length; i++)
			if ((box & mask(i)) != 0)
				v[w++] = i;
		return v;
	}

	private static boolean isSubsetOf(short[] a, boolean[] b) {
		for (short e : a)
			if (!b[e])
				return false;
		return true;
	}
}