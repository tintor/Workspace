package tintor.sokoban;

import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.Arrays;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.Bits;
import tintor.common.Flags;
import tintor.common.For;
import tintor.common.InstrumentationAgent;
import tintor.common.MurmurHash3;
import tintor.common.OpenAddressingHashSet;
import tintor.common.Util;

final class GoalZoneCache {
	final int[] agent;
	final int[] frozen_boxes;
	final int[] boxes;
	boolean deadlock;
	int hash;

	GoalZoneCache(int[] agent, int[] frozen_boxes, int[] boxes, Level level) {
		this.agent = Array.clone(agent);
		this.frozen_boxes = Array.clone(frozen_boxes);
		this.boxes = Array.clone(boxes);

		int a = 0;
		a = MurmurHash3.hash(agent, a);
		a = MurmurHash3.hash(frozen_boxes, a);
		a = MurmurHash3.hash(boxes, a);
		hash = a;
	}

	@Override
	public boolean equals(Object o) {
		GoalZoneCache c = (GoalZoneCache) o;
		return hash == c.hash && Arrays.equals(agent, c.agent) && Arrays.equals(frozen_boxes, c.frozen_boxes)
				&& Arrays.equals(boxes, c.boxes);
	}

	@Override
	public int hashCode() {
		return hash;
	}
}

public final class Deadlock {
	ClosedSet closed;
	OpenSet open;

	public final PatternIndex patternIndex;
	private final CellVisitor visitor;
	private final Level level;
	private final OpenAddressingHashSet<GoalZoneCache> goal_zone_cache = new OpenAddressingHashSet<>();

	// Used by isGoalZoneDeadlock
	private final ArrayDeque<StateKey> queue = new ArrayDeque<>();
	private final StateSet explored;

	private FileWriter goal_zone_deadlocks_file;
	private FileWriter is_valid_level_deadlocks_file;

	private static final AutoTimer timer = new AutoTimer("deadlock");
	private static final AutoTimer timer_frozen = new AutoTimer("deadlock.frozen");
	private static final AutoTimer timer_minimize = new AutoTimer("deadlock.minimize");
	private static final AutoTimer timer_goalzone = new AutoTimer("deadlock.goalzone");
	private static final AutoTimer timer_cleanup = new AutoTimer("deadlock.cleanup");

	private int deadlocks = 0;
	private int non_deadlocks = 0;
	private int trivial = 0;
	private int goalzone_deadlocks = 0;
	private int reachable_free_goals_deadlocks = 0;
	private int isvalidlevel_quick_deadlocks = 0;
	private int isvalidlevel_deadlocks = 0;
	private int cleanup_count = 0;

	private boolean should_cleanup(int agent, int[] box, int offset) {
		if (!patternIndex.matchesNew(agent, box, offset, level.num_boxes))
			return false;
		cleanup_count += 1;
		return true;
	}

	@SneakyThrows
	void report() {
		if (patternIndex.hasNew()) {
			{
				@Cleanup val t = timer_cleanup.openExclusive();
				open.remove_if(this::should_cleanup);
				closed.remove_if(this::should_cleanup);
			}
			patternIndex.clearNew();
		}

		System.out.printf("dead:%s live:%s rev:%s ", Util.human(deadlocks), Util.human(non_deadlocks),
				Util.human(trivial));
		System.out.printf("goaldead:%s rfgdead:%s ivlqdead:%s ivldead:%s db:%s ivl_db:%s\n",
				Util.human(goalzone_deadlocks), Util.human(reachable_free_goals_deadlocks),
				Util.human(isvalidlevel_deadlocks), Util.human(isvalidlevel_quick_deadlocks),
				Util.human(patternIndex.size()), Util.human(goal_zone_cache.size()));
		System.out.printf("  memory:%s cleanup:%s\n", Util.human(InstrumentationAgent.deepSizeOf(patternIndex)),
				Util.human(cleanup_count));
		goal_zone_deadlocks_file.flush();
		is_valid_level_deadlocks_file.flush();
	}

	@SneakyThrows
	public Deadlock(Level level) {
		this.level = level;
		visitor = new CellVisitor(level.cells.length);
		patternIndex = new PatternIndex(level);
		goal_zone_deadlocks_file = new FileWriter(level.name + "_goalzone_deadlocks.txt");
		is_valid_level_deadlocks_file = new FileWriter(level.name + "_isvalidlevel_deadlocks.txt");
		temp_box = new int[(level.alive.length + 31) / 32];
		explored = new StateSet(level.alive.length, level.cells.length);
	}

	@SneakyThrows
	private boolean isGoalZoneDeadlock(StateKey s) {
		@Cleanup val t = timer_goalzone.open();
		if (!level.has_goal_zone)
			return false;

		// remove all boxes not on goals
		int[] box = temp_box;
		Array.copy(s.box, 0, box, 0, s.box.length);
		for (Cell b : level.alive)
			if (Bits.test(box, b.id) && !b.goal)
				Bits.clear(box, b.id);
		if (Bits.count(box) == 0)
			return false;

		for (Cell a : visitor.init(level.cells[s.agent]))
			for (Move m : a.moves)
				if (!s.box(m.cell))
					visitor.try_add(m.cell);
		// TODO cache results

		// move agent and try to push remaining boxes
		// TODO assume goals and agent position can fit in 64bit long
		queue.clear();
		explored.clear();
		queue.addLast(new StateKey(s.agent, box));
		while (!queue.isEmpty()) {
			StateKey q = queue.removeFirst();
			visitor.init(level.cells[q.agent]);
			int reachableGoals = 0;
			int boxesOnGoals = Bits.count(q.box);
			// TODO configure visitor so that in case of a single goal room agent doesn't have to go outside all the time
			// TODO in case of multiple goal rooms do it for each room separately
			while (!visitor.done()) {
				Cell a = visitor.next();
				if (a.goal) {
					reachableGoals += 1;
					assert boxesOnGoals + reachableGoals <= level.num_boxes;
					if (boxesOnGoals + reachableGoals == level.num_boxes)
						return false;
				}
				for (Move e : a.moves) {
					Cell b = e.cell;
					if (!b.alive || !Bits.test(q.box, b.id)) {
						visitor.try_add(b);
						continue;
					}
					Move c = b.move(e.exit_dir);
					if (c == null)
						continue;
					if (!c.cell.goal) {
						// box is removed
						if (--boxesOnGoals == 0)
							return false;
						Bits.clear(q.box, b.id);
						visitor.add(b);
						continue;
					}
					if (!Bits.test(q.box, c.cell.id)) {
						// push box to c
						box = Array.clone(q.box);
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
		goal_zone_deadlocks_file.write(Code.emojify(level.render(s)));
		return true;
	}

	static enum Result {
		Deadlock, NotFrozen, GoalZoneDeadlock,
	}

	// Note: modifies input array!
	private final int[] temp_box;

	@SneakyThrows
	private Result containsFrozenBoxes(final int agent, int[] box, int num_boxes) {
		@Cleanup val t = timer_frozen.open();
		if (num_boxes < 2)
			return Result.NotFrozen;
		int pushed_boxes = 0;
		Array.copy(box, 0, temp_box, 0, box.length);

		for (Cell a : visitor.init(level.cells[agent]))
			for (Move e : a.moves) {
				Cell b = e.cell;
				if (visitor.visited(b.id))
					continue;
				if (!b.alive || !Bits.test(box, b.id)) {
					// agent moves to B
					visitor.add(b);
					continue;
				}

				Move c = b.move(e.exit_dir);
				if (c == null || !c.alive || Bits.test(box, c.cell.id))
					continue;

				Bits.clear(box, b.id);
				Bits.set(box, c.cell.id);
				boolean m = LevelUtil.is_2x2_frozen(c.cell, box) || patternIndex.matches(b.id, box, 0, num_boxes, true);
				Bits.clear(box, c.cell.id);
				if (m) {
					Bits.set(box, b.id);
					continue;
				}

				// agent pushes box from B to C (and box disappears)
				if (--num_boxes == 1)
					return Result.NotFrozen;
				pushed_boxes += 1;
				visitor.init(b);
				break;
			}

		if (!level.is_solved(box))
			return Result.Deadlock;

		return containsFrozenGoalBoxes(pushed_boxes, box, temp_box, agent);
	}

	@SneakyThrows
	private Result containsFrozenGoalBoxes(int pushed_boxes, int[] box, int[] original_box, int agent) {
		// check that agent can reach all goals without boxes on them
		int reachable_free_goals = 0;
		for (Cell a : level.alive)
			if (a.goal && visitor.visited(a))
				reachable_free_goals += 1;
		if (reachable_free_goals < pushed_boxes) {
			reachable_free_goals_deadlocks += 1;
			return Result.GoalZoneDeadlock;
		}

		// Quick pre-filter for is_valid_level()
		for (Cell a : level.goals)
			if (!For.any(a.moves, m -> m.cell.alive && m.alive && m.cell.move(m.dir) != null)) {
				isvalidlevel_quick_deadlocks += 1;
				return Result.GoalZoneDeadlock;
			}

		val visited = Util.compressToIntArray(visitor.visited());
		val cache_new = new GoalZoneCache(visited, box, original_box, level);
		GoalZoneCache cache = goal_zone_cache.get(cache_new);
		if (cache == null) {
			val z = level.render(p -> {
				if (p.id == agent)
					return p.goal ? Code.AgentGoal : Code.Agent;
				if (p.alive && Bits.test(box, p.id))
					return Code.Wall;
				if (p.alive && Bits.test(original_box, p.id))
					return p.goal ? Code.BoxGoal : Code.Box;
				return p.goal ? Code.Goal : Code.Space;
			});
			cache_new.deadlock = !level.is_valid_level(z);
			goal_zone_cache.insert(cache_new);
			cache = cache_new;
			if (cache_new.deadlock)
				is_valid_level_deadlocks_file.write(Code.emojify(z));
		}
		if (cache.deadlock) {
			isvalidlevel_deadlocks += 1;
			return Result.GoalZoneDeadlock;
		}

		return Result.NotFrozen;
	}

	// Looks for boxes not on goal that can't be moved
	// return true - means it is definitely a deadlock
	// return false - not sure if it is a deadlock
	private boolean checkInternal(State s, int num_boxes, boolean incremental) {
		// TODO do we really need this?
		if (level.is_solved_fast(s.box))
			return false;
		if (incremental && LevelUtil.is_2x2_frozen(level.cells[s.agent].move(s.dir).cell, s.box))
			return true;
		if (patternIndex.matches(s.agent, s.box, 0, num_boxes, incremental))
			return true;
		if (isGoalZoneDeadlock(s))
			return true;

		int[] box = Array.clone(s.box);
		Result result = containsFrozenBoxes(s.agent, box, num_boxes);
		if (result == Result.NotFrozen)
			return false;
		// if we have boxes frozen on goals, we can't store that pattern
		if (result == Result.GoalZoneDeadlock)
			return true;

		boolean[] agent = Array.clone(visitor.visited());
		num_boxes = minimizePattern(s, agent, box, Bits.count(box));
		patternIndex.add(agent, box, num_boxes);
		return true;
	}

	// TODO there could be more than one minimal pattern from box
	private int minimizePattern(StateKey s, boolean[] agent, int[] box, int num_boxes) {
		@Cleanup val t = timer_minimize.openExclusive();
		// try removing boxes to generalize the pattern
		for (Cell i : level.cells)
			if (i.alive && Bits.test(box, i.id) && !level.is_solved(box)) {
				int[] box_copy = Array.clone(box);
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
			if (!agent[i] && (i >= level.alive.length || !Bits.test(box, i))
					&& containsFrozenBoxes(i, Array.clone(box), num_boxes) == Result.Deadlock)
				Util.updateOr(agent, visitor.visited());
		return num_boxes;
	}

	private static final Flags.Bool enabled = new Flags.Bool("deadlocks", true);

	public boolean checkIncremental(State s) {
		@Cleanup val t = timer.open();
		assert !s.is_initial();
		if (enabled.value) {
			if (LevelUtil.is_reversible_push(s, level)) {
				trivial += 1;
				return false;
			}
			if (checkInternal(s, level.num_boxes, true)) {
				deadlocks += 1;
				return true;
			}
			non_deadlocks += 1;
		}
		return false;
	}

	public boolean checkFull(State s) {
		@Cleanup val t = timer.open();
		assert !s.is_initial();
		if (enabled.value) {
			if (checkInternal(s, level.num_boxes, false)) {
				deadlocks += 1;
				return true;
			}
			non_deadlocks += 1;
		}
		return false;
	}
}