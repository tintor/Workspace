package tintor.sokoban;

import static tintor.common.Util.print;

import java.util.ArrayDeque;

import lombok.Cleanup;
import lombok.val;
import tintor.common.AutoTimer;
import tintor.common.Flags;
import tintor.common.Util;
import tintor.common.WallTimer;

public final class AStarSolver {
	public static class ClosedSizeLimitError extends Error {
		private static final long serialVersionUID = 1L;
	};

	public static class SpeedTooLow extends Error {
		private static final long serialVersionUID = 1L;
	};

	static final AutoTimer timer_solve = new AutoTimer("solve");
	static final AutoTimer timer_moves = new AutoTimer("moves");

	final Level level;
	public final OpenSet open;
	public final ClosedSet closed;
	final Heuristic heuristic;
	final Deadlock deadlock;

	public int closed_size_limit = Integer.MAX_VALUE;
	public int trace; // 0 to turn off any tracing
	public int min_speed = 0;

	private CellVisitor visitor;
	private int[] moves;

	private int cutoff = Integer.MAX_VALUE;
	int cutoffs = 0;

	private static final Flags.Bool optimal_solution = new Flags.Bool("optimal_solution", false);
	private static final Flags.Int report_time = new Flags.Int("report_time", 20); // in seconds
	private static final Flags.Int unstuck_period = new Flags.Int("unstuck_period", 16);

	public AStarSolver(Level level) {
		this.level = level;
		open = new OpenSet(level.alive.length, level.cells.length);
		closed = new ClosedSet(level);
		heuristic = new Heuristic(level, optimal_solution.value);
		deadlock = new Deadlock(level);

		visitor = new CellVisitor(level.cells.length);
		moves = new int[level.cells.length];
		deadlock.closed = closed;
		deadlock.open = open;
	}

	static boolean divisibleByPowerOf2(int a, int p) {
		return (a >> p) << p == a;
	}

	private long start_cpu_time;
	private long start_wall_time;

	public State solve() {
		start_cpu_time = Util.threadCpuTime();
		start_wall_time = System.nanoTime();
		int h = heuristic.evaluate(level.start);
		if (h == Integer.MAX_VALUE)
			return null;
		level.start.set_heuristic(h);
		if (level.is_solved_fast(level.start.box))
			return level.start;

		int explored = 0;
		long next_report = System.nanoTime() + report_time.value * AutoTimer.Second;
		explore(level.start);
		State a = null;
		StateKey recent = null;
		while (true) {
			{
				@Cleanup val t = timer_solve.open();
				a = open.remove_min();
				if (a == null || level.is_solved_fast(a.box))
					break;
				if (!a.is_initial() && deadlock.checkFull(a))
					continue;

				explore(a);
				explored += 1;
				// TODO auto-adjust frequency here so that deadlock.unstuck is just below 35% of time
				// (or run deadlock.unstuck in a separate thread 100% of time)
				if (divisibleByPowerOf2(explored, unstuck_period.value)) {
					if (recent != null)
						deadlock.unstuck(recent, a);
					recent = a;
				}
			}

			if (trace > 1 && System.nanoTime() >= next_report) {
				report();
				AutoTimer.report();
				level.print(a);
				next_report = System.nanoTime() + report_time.value * AutoTimer.Second;
				if (speed < min_speed)
					throw new SpeedTooLow();
			}
		}
		if (trace > 1)
			report();
		if (trace > 0)
			AutoTimer.report();
		return a;
	}

	void explore(State a) {
		closed.add(a);
		if (closed.size() >= closed_size_limit)
			throw new ClosedSizeLimitError();

		moves[a.agent] = 0; // TODO merge moves to visitor (into set)
		// TODO stop the loop early after we reach all sides of all boxes
		// TODO prioritize cells closer to untouched sides of boxes
		for (Cell agent : visitor.init(level.cells[a.agent]))
			for (Move p : agent.moves) {
				if (!a.box(p.cell.id)) {
					if (visitor.try_add(p.cell))
						moves[p.cell.id] = moves[agent.id] + p.dist;
					continue;
				}

				timer_moves.open();
				State b = a.push(p, level, optimal_solution.value, moves[agent.id], a.agent);
				timer_moves.close();

				if (b == null || closed.contains(b))
					continue;

				int v_total_dist = open.get_total_dist(b);
				if (v_total_dist == 0 && deadlock.checkIncremental(b))
					continue;

				int h = heuristic.evaluate(b);
				if (h >= cutoff) {
					cutoffs += 1;
					continue;
				}
				b.set_heuristic(h);

				if (v_total_dist == 0) {
					if (level.is_solved_fast(b.box)) {
						open.remove_all_ge(optimal_solution.value ? b.total_dist : 0);
						cutoff = b.total_dist;
					}
					open.add(b);
					continue;
				}
				if (b.total_dist < v_total_dist)
					open.update(v_total_dist, b);
			}
	}

	public State[] extractPath(State end) {
		ArrayDeque<State> path = new ArrayDeque<State>();
		State start = level.transforms.normalize(level.start);
		while (true) {
			path.addFirst(end);
			StateKey p = end.prev(level);
			if (level.transforms.normalize(p).equals(start))
				break;
			end = closed.get(end.prev(level));
		}
		return path.toArray(new State[path.size()]);
	}

	private long prev_cpu_time = 0;
	private int prev_open = 0;
	private int prev_closed = 0;
	private double speed = 0;

	private void report() {
		long now = System.nanoTime();
		long now_cpu = Util.threadCpuTime();
		long delta_time = now_cpu - prev_cpu_time;
		int delta_closed = closed.size() - prev_closed;
		int delta_open = open.size() - prev_open;
		prev_cpu_time = now_cpu;

		speed = (speed + 1e9 * delta_closed / delta_time) / 2;

		print("%s ", level.name);
		print("cutoff:%s dead:%s live:%s ", cutoffs, heuristic.deadlocks, heuristic.non_deadlocks);
		print("time:%s ", WallTimer.format(now - start_wall_time));
		print("cpu_time:%s ", WallTimer.format(now_cpu - start_cpu_time));
		print("speed:%s ", (int) speed);
		print("branch:%.2f\n", 1 + (double) delta_open / delta_closed);
		closed.report();
		open.report();
		deadlock.report();

		prev_closed = closed.size();
		prev_open = open.size();
	}
}