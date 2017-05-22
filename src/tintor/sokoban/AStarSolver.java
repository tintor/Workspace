package tintor.sokoban;

import java.util.ArrayDeque;

import lombok.Cleanup;
import lombok.val;
import tintor.common.AutoTimer;
import tintor.common.Flags;
import tintor.common.Timer;
import tintor.common.Util;

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

	public State solve() {
		int h = heuristic.evaluate(level.start);
		if (h == Integer.MAX_VALUE)
			return null;
		level.start.set_heuristic(h);
		if (level.is_solved_fast(level.start.box))
			return level.start;

		long next_report = AutoTimer.total() + report_time.value * AutoTimer.Second;
		explore(level.start);
		State a = null;
		while (true) {
			{
				@Cleanup val t = timer_solve.open();
				a = open.remove_min();
				if (a == null || level.is_solved_fast(a.box))
					break;
				explore(a);
			}

			if (trace > 1 && AutoTimer.total() >= next_report) {
				report();
				AutoTimer.report();
				level.print(a);
				next_report += report_time.value * AutoTimer.Second;
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
		if (!a.is_initial() && deadlock.checkFull(a))
			return;
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
						open.remove_all_ge(b.total_dist);
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

	private long prev_time = 0;
	private int prev_open = 0;
	private int prev_closed = 0;
	private double speed = 0;

	private void report() {
		long delta_time = AutoTimer.total() - prev_time;
		int delta_closed = closed.size() - prev_closed;
		int delta_open = open.size() - prev_open;
		prev_time = AutoTimer.total();
		prev_closed = closed.size();
		prev_open = open.size();

		speed = (speed + 1e9 * delta_closed / delta_time) / 2;

		System.out.printf("%s ", level.name);
		System.out.printf("cutoff:%s dead:%s live:%s ", Util.human(cutoffs), Util.human(heuristic.deadlocks),
				Util.human(heuristic.non_deadlocks));
		System.out.printf("time:%s ", Timer.format(AutoTimer.total()));
		System.out.printf("speed:%s ", Util.human((int) speed));
		System.out.printf("branch:%.2f\n", 1 + (double) delta_open / delta_closed);
		closed.report();
		open.report();
		deadlock.report();
	}
}