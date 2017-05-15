package tintor.sokoban;

import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;

import tintor.common.AutoTimer;
import tintor.common.Bits;
import tintor.common.InstrumentationAgent;
import tintor.common.Util;
import tintor.common.Visitor;

// TODO: new int[] + System.arraycopy() might be faster than clone() (or maybe even Arrays.copyOf(array, array.length))

public final class Deadlock {
	ClosedSet closed;
	OpenSet open;

	public final PatternIndex patternIndex;
	private final Visitor visitor;
	private final Level level;

	// Used by isGoalZoneDeadlock
	private final ArrayDeque<StateKey> queue = new ArrayDeque<>();
	private StateSet explored;

	private FileWriter goal_zone_deadlocks_file = Util.openWriter("goalzone_deadlocks.txt");
	private FileWriter is_valid_level_deadlocks_file = Util.openWriter("isvalidlevel_deadlocks.txt");

	private static final AutoTimer timer = new AutoTimer("deadlock");
	private static final AutoTimer timer_frozen = new AutoTimer("deadlock.frozen");
	private static final AutoTimer timer_minimize = new AutoTimer("deadlock.minimize");
	private static final AutoTimer timer_goalzone = new AutoTimer("deadlock.goalzone");
	private static final AutoTimer timer_cleanup = new AutoTimer("deadlock.cleanup");

	private int deadlocks = 0;
	private int non_deadlocks = 0;
	private int trivial = 0;
	private int goalzone_deadlocks = 0;
	private int isvalidlevel_deadlocks = 0;
	private int cleanup_count = 0;

	private boolean should_cleanup(int agent, int[] box, int offset) {
		if (!patternIndex.matchesNew(agent, box, offset, level.num_boxes))
			return false;
		cleanup_count += 1;
		return true;
	}

	void report() {
		if (patternIndex.hasNew()) {
			try (AutoTimer t = timer_cleanup.openExclusive()) {
				open.remove_if(this::should_cleanup);
				closed.remove_if(this::should_cleanup);
			}
			patternIndex.clearNew();
		}

		System.out.printf("dead:%s live:%s rev:%s goaldead:%s ivldead:%s db:%s db2:%s\n  memory:%s cleanup:%s\n",
				Util.human(deadlocks), Util.human(non_deadlocks), Util.human(trivial), Util.human(goalzone_deadlocks),
				Util.human(isvalidlevel_deadlocks), Util.human(patternIndex.size()),
				Util.human(goal_zone_patterns.size()), Util.human(InstrumentationAgent.deepSizeOf(patternIndex)),
				Util.human(cleanup_count));
		Util.flush(goal_zone_deadlocks_file);
		Util.flush(is_valid_level_deadlocks_file);
	}

	public Deadlock(Level level) {
		this.level = level;
		visitor = new Visitor(level.cells.length);
		patternIndex = new PatternIndex(level);
	}

	private boolean isGoalZoneDeadlock(StateKey s) {
		if (!level.has_goal_zone)
			return false;

		// remove all boxes not on goals
		int[] box = s.box.clone();
		for (int b = 0; b < level.alive; b++)
			if (Bits.test(box, b) && !level.cells[b].goal)
				Bits.clear(box, b);
		if (Bits.count(box) == 0)
			return false;

		// move agent and try to push remaining boxes
		// TODO assume at most 32 goals and represent boxes with a single int (+ agent position)
		if (explored == null)
			explored = new StateSet(level.alive, level.cells.length);
		queue.clear();
		explored.clear();
		queue.addLast(new StateKey(s.agent, box));
		while (!queue.isEmpty()) {
			StateKey q = queue.removeFirst();
			visitor.init(q.agent);
			int reachableGoals = 0;
			int boxesOnGoals = Bits.count(q.box);
			// TODO configure visitor so that in case of a single goal room agent doesn't have to go outside all the time
			// TODO in case of multiple goal rooms do it for each room separately
			while (!visitor.done()) {
				Cell a = level.cells[visitor.next()];
				if (a.goal) {
					reachableGoals += 1;
					assert boxesOnGoals + reachableGoals <= level.num_boxes;
					if (boxesOnGoals + reachableGoals == level.num_boxes)
						return false;
				}
				for (Move e : a.moves) {
					Cell b = e.cell;
					if (!b.alive || !Bits.test(q.box, b.id)) {
						visitor.try_add(b.id);
						continue;
					}
					Move c = b.move(e.dir);
					if (c == null)
						continue;
					if (!c.cell.goal) {
						// box is removed
						if (--boxesOnGoals == 0)
							return false;
						Bits.clear(q.box, b.id);
						visitor.add(b.id);
						continue;
					}
					if (!Bits.test(q.box, c.cell.id)) {
						// push box to c
						box = q.box.clone();
						Bits.clear(box, b.id);
						Bits.set(box, c.cell.id);
						StateKey m = new StateKey(b.id, box);
						if (!explored.contains(m)) {
							explored.insert(m);
							queue.addLast(m);
						}
						continue;
					}
				}
			}
		}
		goalzone_deadlocks += 1;
		Util.write(goal_zone_deadlocks_file, level.render(s));
		return true;
	}

	static enum Result {
		Deadlock, NotFrozen, GoalZoneDeadlock,
	}

	// Note: modifies input array!
	private Result containsFrozenBoxes(final int agent, int[] box, int num_boxes) {
		try (AutoTimer t = timer_frozen.open()) {
			if (num_boxes < 2)
				return Result.NotFrozen;
			int pushed_boxes = 0;
			int[] original_box = box.clone();

			visitor.init(agent);
			while (!visitor.done()) {
				Cell a = level.cells[visitor.next()];
				for (Move e : a.moves) {
					Cell b = e.cell;
					if (visitor.visited(b.id))
						continue;
					if (!b.alive || !Bits.test(box, b.id)) {
						// agent moves to B
						visitor.add(b.id);
						continue;
					}

					Move c = b.move(e.dir);
					if (c == null || !c.alive || Bits.test(box, c.cell.id))
						continue;

					Bits.clear(box, b.id);
					Bits.set(box, c.cell.id);
					boolean m = patternIndex.matches(b.id, box, 0, num_boxes, true);
					Bits.clear(box, c.cell.id);
					if (m) {
						Bits.set(box, b.id);
						continue;
					}

					// agent pushes box from B to C (and box disappears)
					if (--num_boxes == 1)
						return Result.NotFrozen;
					pushed_boxes += 1;
					visitor.init(b.id);
					break;
				}
			}

			if (!level.is_solved(box))
				return Result.Deadlock;

			// check that agent can reach all goals without boxes on them
			int reachable_free_goals = 0;
			for (int a = 0; a < level.alive; a++)
				if (level.cells[a].goal && visitor.visited(a))
					reachable_free_goals += 1;
			if (reachable_free_goals < pushed_boxes)
				return Result.GoalZoneDeadlock;

			if (!level.is_valid_level(p -> {
				if (p.id == agent)
					return p.goal ? Level.AgentGoal : Level.Agent;
				if (p.alive && Bits.test(box, p.id))
					return Level.Wall;
				if (p.alive && Bits.test(original_box, p.id))
					return p.goal ? Level.BoxGoal : Level.Box;
				return p.goal ? Level.Goal : Level.Space;
			})) {
				isvalidlevel_deadlocks += 1;
				Util.write(is_valid_level_deadlocks_file, level.render(new StateKey(agent, original_box)));
				return Result.GoalZoneDeadlock;
			}

			return Result.NotFrozen;
		}
	}

	// Looks for boxes not on goal that can't be moved
	// return true - means it is definitely a deadlock
	// return false - not sure if it is a deadlock
	private boolean checkInternal(State s, int num_boxes, boolean incremental) {
		// TODO do we really need this?
		if (level.is_solved_fast(s.box))
			return false;
		if (incremental && LevelUtil.is_2x2_frozen(level.cells[s.agent].move(s.dir).cell, s)) {
			return true;
		}
		if (patternIndex.matches(s.agent, s.box, 0, num_boxes, incremental))
			return true;
		if (matchesGoalZonePattern(s.agent, s.box))
			return true;
		try (AutoTimer t = timer_goalzone.open()) {
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
					if (level.cells[a].goal && !visitor.visited(a))
						unreachable_goals |= Bits.mask(a);
				assert box[1] == 0; // TODO
				final GoalZonePattern z = new GoalZonePattern();
				z.agent = visitor.visited().clone();
				z.boxes_frozen_on_goals = box[0];
				z.unreachable_goals = unreachable_goals & ~box[0];
				level.print(i -> {
					int e = 0;
					if (!i.goal)
						return ' ';
					if (Bits.test(z.boxes_frozen_on_goals, i.id))
						e += 1;
					if (Bits.test(z.unreachable_goals, i.id))
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
		try (AutoTimer t = timer_minimize.open()) {
			// try to removing boxes to generalize the pattern
			for (Cell i : level.cells)
				if (i.alive && Bits.test(box, i.id) && !level.is_solved(box)) {
					int[] box_copy = box.clone();
					Bits.clear(box_copy, i.id);
					if (containsFrozenBoxes(s.agent, box_copy, num_boxes - 1) == Result.Deadlock) {
						Bits.clear(box, i.id);
						num_boxes -= 1;
						for (Move z : i.moves)
							if (agent[z.cell.id])
								agent[i.id] = true;
					}
				}
			// try moving agent to unreachable cells to generalize the pattern
			for (int i = 0; i < level.cells.length; i++)
				if (!agent[i] && (i >= level.alive || !Bits.test(box, i))
						&& containsFrozenBoxes(i, box.clone(), num_boxes) == Result.Deadlock)
					Util.updateOr(agent, visitor.visited());
		}

		// Save remaining state as a new deadlock pattern
		patternIndex.add(agent, box, num_boxes);
		return true;
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

	public boolean checkIncremental(State s) {
		try (AutoTimer t = timer.open()) {
			assert !s.is_initial();
			if (LevelUtil.is_reversible_push(s, level)) {
				trivial += 1;
				return false;
			}
			if (checkInternal(s, level.num_boxes, true)) {
				deadlocks += 1;
				return true;
			}
			non_deadlocks += 1;
			return false;
		}
	}

	public boolean checkFull(State s) {
		try (AutoTimer t = timer.open()) {
			assert !s.is_initial();
			if (checkInternal(s, level.num_boxes, false)) {
				deadlocks += 1;
				return true;
			}
			non_deadlocks += 1;
			return false;
		}
	}
}