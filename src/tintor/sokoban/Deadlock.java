package tintor.sokoban;

import static tintor.common.InstrumentationAgent.deepSizeOf;
import static tintor.common.Util.print;

import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.BipartiteMatching;
import tintor.common.Bits;
import tintor.common.Flags;
import tintor.common.For;
import tintor.common.MurmurHash3;
import tintor.common.OpenAddressingHashSet;
import tintor.common.Util;

final class GoalZoneCache {
	final int[] agent;
	final int[] frozen_boxes;
	final int[] boxes;
	boolean deadlock;
	int hash;

	GoalZoneCache(int[] agent, int[] frozen_boxes, int[] boxes) {
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
	private FileWriter unstuck_fail_file;

	private static final AutoTimer timer = new AutoTimer("deadlock");
	private static final AutoTimer timer_frozen = new AutoTimer("deadlock.frozen");
	private static final AutoTimer timer_goalzone = new AutoTimer("deadlock.goalzone");
	private static final AutoTimer timer_cleanup = new AutoTimer("deadlock.cleanup");
	private static final AutoTimer timer_unstuck = new AutoTimer("deadlock.unstuck");
	private static final AutoTimer timer_bipartite = new AutoTimer("deadlock.bipartite");
	private static final AutoTimer timer_icorral = new AutoTimer("deadlock.icorral");

	private int deadlocks;
	private int non_deadlocks;
	private int trivial;

	private int cleanup_count;

	private int simple_deadlocks;
	private int pattern_deadlocks;
	private int frozen_box_deadlocks;
	private int goalzone_deadlocks;
	private int goalzone_cache_deadlocks;
	private int reachable_free_goals_deadlocks;
	private int isvalidlevel_filter_deadlocks;
	private int isvalidlevel_deadlocks;
	private int bipartite_deadlocks;
	private int icorral_deadlocks;
	private int icorral2_deadlocks;

	public int unstuck_bad;
	public int unstuck_good;

	@SneakyThrows
	public Deadlock(Level level) {
		this.level = level;
		visitor = new CellVisitor(level.cells.length);
		patternIndex = new PatternIndex(level);
		goal_zone_deadlocks_file = new FileWriter(level.name + "_goalzone_deadlocks.txt");
		is_valid_level_deadlocks_file = new FileWriter(level.name + "_isvalidlevel_deadlocks.txt");
		unstuck_fail_file = new FileWriter(level.name + "_unstuck_fail.txt");
		temp_box = new int[(level.alive.length + 31) / 32];
		explored = new StateSet(level.alive.length, level.cells.length);
		are_all_goals_in_the_same_tunnel = are_all_goals_in_the_same_tunnel();
	}

	@SneakyThrows
	void report() {
		cleanup();

		print("dead:%s live:%s rev:%s ", deadlocks, non_deadlocks, trivial);
		print("db:%s ivl_db:%s ", patternIndex.size(), goal_zone_cache.size());
		print("unstuck[good:%s bad:%s]\n", unstuck_good, unstuck_bad);

		print("dead[box:%s pattern:%s ", simple_deadlocks, pattern_deadlocks);
		print("fb:%s goalzone:%s goalzone_cache:%s ", frozen_box_deadlocks, goalzone_deadlocks,
				goalzone_cache_deadlocks);
		print("icorral:%s icorral2:%s ", icorral_deadlocks, icorral2_deadlocks);
		print("reach_free_goals:%s bipartite:%s ", reachable_free_goals_deadlocks, bipartite_deadlocks);
		print("ivl_filter:%s ivl:%s]\n", isvalidlevel_filter_deadlocks, isvalidlevel_deadlocks);

		print("pattern_index_memory:%s ", deepSizeOf(patternIndex));
		print("goal_zone_cache_memory:%s ", deepSizeOf(goal_zone_cache));
		print("cleanup:%s\n", cleanup_count);

		patternIndex.flush();
		goal_zone_deadlocks_file.flush();
		is_valid_level_deadlocks_file.flush();
		unstuck_fail_file.flush();
	}

	void cleanup() {
		if (patternIndex.hasNew()) {
			{
				@Cleanup val t = timer_cleanup.openExclusive();
				open.remove_if(this::should_cleanup);
				closed.remove_if(this::should_cleanup);
			}
			patternIndex.clearNew();
		}
	}

	private boolean should_cleanup(int agent, int[] box, int offset) {
		if (!patternIndex.matchesNew(agent, box, offset, level.num_boxes))
			return false;
		cleanup_count += 1;
		return true;
	}

	public boolean isICorralDeadlock(StateKey s) {
		@Cleanup val t = timer_icorral.openExclusive();
		boolean[] agent_reachable = LevelUtil.find_agent_reachable_cells(s, level, null);

		ArrayList<byte[]> corrals = new ArrayList<>();
		corrals.clear();
		level.visitor.init();
		for (Cell c : level.cells)
			if (!level.visitor.visited(c) && !s.box(c) && !agent_reachable[c.id])
				corrals.add(LevelUtil.build_corral(c, s));

		ArrayList<StateKey> pushes = new ArrayList<>();
		loop:
		for (byte[] corral : corrals)
			if (LevelUtil.is_unsolved_corral(corral, s, level) && LevelUtil.is_i_corral(corral, s, level)) {
				// Create a new StateKey without any non-corral boxes
				int[] boxes = new int[s.box.length];
				for (Cell c : level.alive)
					if (s.box(c) && corral[c.id] >= 0)
						Bits.set(boxes, c.id);
				StateKey sc = new StateKey(s.agent, boxes);

				// Find all non-deadlock pushes into I-corral
				pushes.clear();
				for (Cell a : visitor.init(level.cells[sc.agent]))
					for (Move p : a.moves) {
						if (!sc.box(p.cell)) {
							visitor.try_add(p.cell);
							continue;
						}

						StateKey b = sc.push(p, level, false);
						if (b == null)
							continue;
						Cell box = p.cell.move(p.exit_dir).cell;
						while (!b.box(box))
							box = box.move(p.exit_dir).cell;

						if (LevelUtil.is_simple_deadlock(level.cells[b.agent], box, b.box))
							continue;

						if (patternIndex.matches(b.agent, b.box, 0, Bits.count(b.box), true))
							continue;

						pushes.add(b);
					}
				Array.copy(visitor.visited(), 0, agent_reachable, 0, agent_reachable.length);

				// recursion depth is limited by the number of empty cells inside corral
				for (StateKey b : pushes)
					if (!isICorralDeadlock(b))
						continue loop;

				// Add the entire I-Corral to pattern index
				if (For.all(level.alive, p -> !p.box(sc.box) || !p.goal)) {
					patternIndex.add(agent_reachable, sc.box, Bits.count(sc.box), false, "i-corral");
					icorral_deadlocks += 1;
				} else {
					icorral2_deadlocks += 1;
				}
				return true;
			}
		return false;
	}

	private static final Flags.Int exaustive_limit = new Flags.Int("exaustive_limit", 200000);

	// s can have less boxes than level
	// TODO maybe parallelize this function
	public boolean isDeadlockExaustive(StateKey s, CellVisitor visitor, ArrayDeque<StateKey> queue, StateSet set) {
		// TODO if number of boxes < number of goals then it can be both solved (for current boxes) and deadlock (for remaining boxes)
		// TODO make sure it is not a goal zone deadlock
		if (level.is_solved(s.box))
			return false;

		int num_boxes = Bits.count(s.box);
		allow_more_goals_than_boxes = true;
		int explored = 0;
		queue.clear();
		set.clear();

		try {
			queue.addLast(s);
			long next = System.nanoTime() + 2500 * AutoTimer.Millisecond;
			while (!queue.isEmpty()) {
				StateKey a = queue.removeFirst();
				if (checkInternal2(a, -1, num_boxes))
					continue;
				for (Cell agent : visitor.init(level.cells[a.agent]))
					for (Move p : agent.moves) {
						if (!a.box(p.cell)) {
							visitor.try_add(p.cell);
							continue;
						}
						StateKey b = a.push(p, level, false);
						if (b == null || set.contains(b) || checkInternal2(b, p.exit_dir.ordinal(), num_boxes))
							continue;
						if (level.is_solved(b.box))
							// TODO if num boxes < num goals then it can be both solved (for current boxes) and deadlock (for remaining boxes)
							// TODO make sure it is not a goal zone deadlock
							return false;
						set.insert(b);
						queue.addLast(b);
					}
				long now = System.nanoTime();
				if (now >= next) {
					print("explored:%s set:%s queue:%s\n", Util.human(explored), Util.human(set.size()),
							Util.human(queue.size()));
					next = now + 2_500_000_000l;
				}
				// unable to determine if the state is deadlock in time
				if (++explored >= exaustive_limit.value)
					return false;
			}
		} finally {
			allow_more_goals_than_boxes = false;
		}
		return true;
	}

	private static final Flags.Bool unstuck_debug = new Flags.Bool("unstuck_debug", false);

	@SneakyThrows
	boolean unstuck(StateKey recent, StateKey current) {
		// TODO split this timer into 2 (one timer just for minimization)
		@Cleanup val t = timer_unstuck.openExclusive();
		int[] boxes = Array.clone(current.box);
		for (int i = 0; i < boxes.length; i++)
			boxes[i] &= recent.box[i];
		if (Bits.count(boxes) <= 1)
			return false;

		// must use its own structures as it calls checkFull/Incremental() which modify shared ones
		CellVisitor visitor = new CellVisitor(level.cells.length);
		ArrayDeque<StateKey> queue = new ArrayDeque<>();
		StateSet set = new StateSet(level.alive.length, level.cells.length);

		StateKey q = new StateKey(current.agent, boxes);
		if (unstuck_debug.value) {
			print("unstuck - begin\n");
			level.print(q);
		}
		// TODO maybe have higher exhaustive limit for this first call to isDeadlockExaustive()

		// TODO cache results for the first call to isDeadlockExaustive(), to avoid recomputing it!
		// TODO OR next time we get asked for state S that returned ExaustiveLimit before, increase exhaustive limit!
		if (!isDeadlockExaustive(q, visitor, queue, set)) {
			if (unstuck_debug.value)
				print("unstuck - failed\n");
			unstuck_fail_file.write(Code.emojify(level.render(q)));
			unstuck_bad += 1;
			return false;
		}

		// try removing unnecessary boxes from pattern
		for (Cell b : level.alive)
			if (q.box(b)) {
				Bits.clear(boxes, b.id);
				if (!isDeadlockExaustive(q, visitor, queue, set))
					Bits.set(boxes, b.id);
			}
		// move agent to all reachable cells
		boolean[] agent = new boolean[level.cells.length];
		for (Cell a : visitor.init(level.cells[q.agent])) {
			agent[a.id] = true;
			for (Move m : a.moves)
				if (!q.box(m.cell))
					visitor.try_add(m.cell);
		}
		// TODO try to move agent even more
		patternIndex.add(agent, boxes, Bits.count(boxes), unstuck_debug.value, "unstuck");
		// TODO try generating more patterns based on it (by pushing it in) 
		cleanup();
		if (unstuck_debug.value)
			print("unstuck - successful");
		unstuck_good += 1;
		return true;
	}

	// TODO for microban4:56, one goal cell is at the tunnel entrance
	private final boolean are_all_goals_in_the_same_tunnel;

	private boolean are_all_goals_in_the_same_tunnel() {
		Cell a = level.goals[0];
		assert a.goal;
		if (!a.straight())
			return false;
		int count = 1;
		for (Move m : a.moves)
			if (m.dist == 1) {
				Cell b = m.cell;
				while (b.alive && b.straight()) {
					Move bm = b.move(m.dir);
					if (bm.dist != 1 || !bm.alive)
						break;
					if (b.goal)
						count += 1;
					b = bm.cell;
				}
			}
		return count == level.goals.length;
	}

	private static final Flags.Bool goalzone = new Flags.Bool("goalzone", true);

	@SneakyThrows
	private boolean isGoalZoneDeadlock(StateKey s) {
		if (!goalzone.value)
			return false;
		@Cleanup val t = timer_goalzone.open();
		if (!level.has_goal_zone || are_all_goals_in_the_same_tunnel)
			return false;

		// remove all boxes not on goals
		int[] box = temp_box;
		Array.copy(s.box, 0, box, 0, s.box.length);
		for (Cell b : level.alive)
			if (Bits.test(box, b.id) && !b.goal)
				Bits.clear(box, b.id);
		if (Bits.count(box) == 0)
			return false;

		// Compute all agent reachable cells
		for (Cell a : visitor.init(level.cells[s.agent]))
			for (Move m : a.moves)
				if (!s.box(m.cell))
					visitor.try_add(m.cell);
		boolean[] agent = visitor.visited().clone();

		// Cache lookup
		s = new StateKey(s.agent, box);
		val cache_new = new GoalZoneCache(Util.compressToIntArray(agent), new int[box.length], box);
		val cache_lookup = goal_zone_cache.get(cache_new);
		if (cache_lookup != null) {
			goalzone_cache_deadlocks += 1;
			return cache_lookup.deadlock;
		}

		// move agent and try to push remaining boxes
		// TODO assume goals and agent position can fit in 64bit long
		queue.clear();
		explored.clear();
		queue.addLast(s);
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
					if (boxesOnGoals + reachableGoals == level.num_boxes) {
						goal_zone_cache.insert(cache_new);
						return false;
					}
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
						if (--boxesOnGoals == 0) {
							goal_zone_cache.insert(cache_new);
							return false;
						}
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
		// TODO multiple agent spots
		goal_zone_deadlocks_file.write(Code.emojify(level.render(s)));
		cache_new.deadlock = true;
		goal_zone_cache.insert(cache_new);
		return true;
	}

	static enum Result {
		Deadlock, NotFrozen, GoalZoneDeadlock,
	}

	private final int[] temp_box;

	// Note: modifies input array!
	@SneakyThrows
	private Result containsFrozenBoxes(final int agent, int[] box, int num_boxes) {
		@Cleanup val t = timer_frozen.open();
		if (num_boxes < 2) {
			Arrays.fill(box, 0);
			return Result.NotFrozen;
		}
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
				boolean m = LevelUtil.is_simple_deadlock(b, c.cell, box)
						|| patternIndex.matches(b.id, box, 0, num_boxes, true);
				Bits.clear(box, c.cell.id);
				if (m) {
					Bits.set(box, b.id);
					continue;
				}

				// agent pushes box from B to C (and box disappears)
				if (--num_boxes == 1) {
					Arrays.fill(box, 0);
					return Result.NotFrozen;
				}
				pushed_boxes += 1;
				visitor.init(b);
				break;
			}

		if (!level.is_solved(box))
			return Result.Deadlock;

		return containsFrozenGoalBoxes(pushed_boxes, box, temp_box, agent);
	}

	private static boolean allow_more_goals_than_boxes = false;

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

		if (!allow_more_goals_than_boxes) {
			// Quick pre-filter for is_valid_level()
			for (Cell a : level.goals)
				if (!For.any(a.moves, m -> m.cell.alive && m.alive && m.cell.move(m.dir) != null)) {
					isvalidlevel_filter_deadlocks += 1;
					return Result.GoalZoneDeadlock;
				}

			val visited = Util.compressToIntArray(visitor.visited());
			val cache_new = new GoalZoneCache(visited, box, original_box);
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
				cache_new.deadlock = !level.is_valid_level(z, allow_more_goals_than_boxes);
				goal_zone_cache.insert(cache_new);
				cache = cache_new;
				if (cache_new.deadlock)
					is_valid_level_deadlocks_file.write(Code.emojify(z));
			}
			if (cache.deadlock) {
				isvalidlevel_deadlocks += 1;
				return Result.GoalZoneDeadlock;
			}
		}

		return Result.NotFrozen;
	}

	private boolean isFrozenBoxesDeadlock(StateKey s, int[] boxes, int num_boxes) {
		Result result = containsFrozenBoxes(s.agent, boxes, num_boxes);
		if (result == Result.Deadlock) {
			frozen_box_deadlocks += 1;
			boolean[] agent = Array.clone(visitor.visited());

			// TODO there could be more than one minimal pattern from box
			num_boxes = Bits.count(boxes);
			// try removing boxes to generalize the pattern
			for (Cell b : level.alive)
				if (Bits.test(boxes, b.id) && !level.is_solved(boxes)) {
					int[] box_copy = Array.clone(boxes);
					Bits.clear(box_copy, b.id);
					if (containsFrozenBoxes(s.agent, box_copy, num_boxes - 1) == Result.Deadlock) {
						Bits.clear(boxes, b.id);
						num_boxes -= 1;
						for (Move m : b.moves)
							if (agent[m.cell.id])
								agent[b.id] = true;
					}
				}
			// try moving agent to unreachable cells to generalize the pattern
			for (Cell a : level.cells)
				if (!agent[a.id] && (!a.alive || !Bits.test(boxes, a.id))
						&& containsFrozenBoxes(a.id, Array.clone(boxes), num_boxes) == Result.Deadlock)
					Util.updateOr(agent, visitor.visited());

			patternIndex.add(agent, boxes, num_boxes, false, "frozen");
		}
		// TODO store GoalZonePattern also
		return result != Result.NotFrozen;
	}

	boolean[][] graph;
	int[] match;
	boolean[] seen;

	private boolean containsBipartiteMatchingDeadlockInternal(int[] boxes, int[] frozen) {
		int bc = 0;
		for (Cell b : level.alive)
			if (Bits.test(boxes, b.id)) {
				if (b.goal && Bits.test(frozen, b.id))
					for (Cell g : level.goals)
						graph[bc][g.id] = g == b;
				else
					for (Cell g : level.goals)
						graph[bc][g.id] = !Bits.test(frozen, g.id) && b.distance_box[g.id] < Cell.Infinity;
				bc += 1;
			}
		return BipartiteMatching.maxBPM(graph, bc, match, seen) < bc;
	}

	private boolean containsBipartiteMatchingDeadlock(int[] boxes, int[] frozen) {
		@Cleanup val t = timer_bipartite.open();
		if (graph == null) {
			graph = new boolean[level.num_boxes][level.goals.length];
			match = new int[level.goals.length];
			seen = new boolean[level.goals.length];
		}

		if (!containsBipartiteMatchingDeadlockInternal(boxes, frozen))
			return false;

		bipartite_deadlocks += 1;
		boolean[] agent = Array.ofBool(level.cells.length, i -> true);
		int num_boxes = Bits.count(boxes);
		for (Cell b : level.alive)
			if (Bits.test(boxes, b.id) && !level.is_solved(boxes) && !Bits.test(frozen, b.id)) {
				Bits.clear(boxes, b.id);
				if (containsBipartiteMatchingDeadlockInternal(boxes, frozen))
					num_boxes -= 1;
				else
					Bits.set(boxes, b.id);
			}
		patternIndex.add(agent, boxes, num_boxes, false, "bipartite");
		return true;
	}

	// Looks for boxes not on goal that can't be moved
	// return true - means it is definitely a deadlock
	// return false - not sure if it is a deadlock
	private boolean checkInternal(StateKey s, int s_dir, int num_boxes) {
		assert Bits.count(s.box) == num_boxes;
		if (level.is_solved(s.box))
			return false;
		if (s_dir != -1
				&& LevelUtil.is_simple_deadlock(level.cells[s.agent], level.cells[s.agent].move(s_dir).cell, s.box)) {
			simple_deadlocks += 1;
			return true;
		}
		if (patternIndex.matches(s.agent, s.box, 0, num_boxes, s_dir != -1)) {
			pattern_deadlocks += 1;
			return true;
		}
		// TODO goal zone deadlock check might be redundant with ICorralDeadlock
		if (isGoalZoneDeadlock(s))
			return true;

		// TODO find the best ordering of deadlock checking functions
		// TODO move containsFrozenBoxes higher up as isGoalZoneDeadlock and isICorralDeadlock can take advantage of boxes frozen on goals
		// TODO move frozen boxes into isFrozenBoxesDeadlock()
		int[] boxes = Array.clone(s.box);
		if (isFrozenBoxesDeadlock(s, boxes, num_boxes))
			return true;

		if (containsBipartiteMatchingDeadlock(s.box, boxes))
			return true;

		if (false && isICorralDeadlock(s))
			return true;

		return false;
	}

	private static final Flags.Bool enabled = new Flags.Bool("deadlocks", true);

	private boolean checkInternal2(StateKey s, int dir, int num_boxes) {
		if (dir != -1 && LevelUtil.is_reversible_push(s, dir, level)) {
			trivial += 1;
			return false;
		}
		if (checkInternal(s, dir, num_boxes)) {
			deadlocks += 1;
			return true;
		}
		non_deadlocks += 1;
		return false;
	}

	public boolean checkIncremental(State s) {
		@Cleanup val t = timer.open();
		assert !s.is_initial();
		return enabled.value && checkInternal2(s, s.dir, level.num_boxes);
	}

	public boolean checkFull(State s) {
		@Cleanup val t = timer.open();
		return enabled.value && checkInternal2((StateKey) s, -1, level.num_boxes);
	}
}