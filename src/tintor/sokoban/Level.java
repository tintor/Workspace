package tintor.sokoban;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.ArrayDequeInt;
import tintor.common.AutoTimer;
import tintor.common.BitMatrix;
import tintor.common.Bits;
import tintor.common.Util;
import tintor.common.Visitor;
import tintor.common.Zobrist;
import tintor.sokoban.LowLevel.IndexToChar;

final class Level {
	// Note: could easily increase this limit
	static class MoreThan256CellsError extends Error {
		private static final long serialVersionUID = 1L;
	}

	final LowLevel low;

	// direction
	final static int Left = 0;
	final static int Up = 1;
	final static int Right = 2;
	final static int Down = 3;

	// Used by move() to signal invalid move
	final static int Bad = -1;

	static int reverseDir(int dir) {
		assert 0 <= dir && dir < 4 : dir;
		return dir ^ 2;
	}

	static ArrayList<Level> loadAll(String filename) {
		int count = LowLevel.numberOfLevels(filename);
		ArrayList<Level> levels = new ArrayList<Level>();
		for (int i = 1; i <= count; i++)
			try {
				levels.add(load(filename + ":" + i));
			} catch (MoreThan256CellsError e) {
			}
		return levels;
	}

	static Level load(String filename) {
		LowLevel low = LowLevel.load(filename);
		final boolean[] walkable = low.compute_walkable();
		low.add_walls(walkable);
		low.check_boxes_and_goals();
		if (Util.count(walkable) > 256)
			throw new MoreThan256CellsError(); // just to avoid calling compute_alive() for huge levels
		ArrayDequeInt deque = new ArrayDequeInt(low.cells());
		BitMatrix visited = new BitMatrix(low.cells(), low.cells()); // TODO this is huge, as cells is raw buffer size
		if (!low.new AreAllGoalsReachable().run(visited))
			throw new IllegalArgumentException("level contains unreachable goal");
		final boolean[] is_alive = low.compute_alive(deque, visited, walkable);
		return new Level(low, walkable, is_alive);
	}

	private Level(LowLevel low, boolean[] walkable, boolean[] is_alive) {
		this.low = low;
		low.check_boxes_and_goals();

		cells = Util.count(walkable);
		Zobrist.ensure(128 + cells);
		if (cells > 256)
			throw new MoreThan256CellsError();
		alive = Util.count(is_alive);

		int b = 0;
		int[] old_to_new = new int[low.cells()];
		low.new_to_old = new int[cells];
		Arrays.fill(old_to_new, -1);
		for (int a = 0; a < low.cells(); a++)
			if (is_alive[a]) {
				old_to_new[a] = b;
				low.new_to_old[b] = a;
				b += 1;
			}
		assert b == alive;
		for (int a = 0; a < low.cells(); a++)
			if (walkable[a] && !is_alive[a]) {
				assert old_to_new[a] == -1;
				old_to_new[a] = b;
				low.new_to_old[b] = a;
				b += 1;
			}
		assert b == cells;

		move = new int[4 * cells];
		for (int i = 0; i < low.cells(); i++)
			if (walkable[i])
				for (int dir = 0; dir < 4; dir++)
					move[old_to_new[i] * 4 + dir] = (low.move(i, dir) != Bad) ? old_to_new[low.move(i, dir)] : Bad;

		boolean[] goalb = new boolean[alive];
		for (int i = 0; i < low.cells(); i++)
			if (low.goal(i))
				goalb[old_to_new[i]] = true;
		boolean[] box = new boolean[alive];
		for (int i = 0; i < low.cells(); i++)
			if (low.box(i))
				box[old_to_new[i]] = true;

		// TODO: Level should not depend on State. Have constructor of State that takes Level as parameter.
		start = new State(old_to_new[low.agent()], Util.compressToIntArray(box), low.dist, 0, 0, 0);
		goal = Util.compressToIntArray(goalb);
		num_boxes = Util.count(box);
		assert num_boxes == Util.count(goalb);

		visitor = new Visitor(cells);
		moves = new int[cells][];
		dirs = new int[cells][];
		delta = new int[cells][cells];
		agent_distance = new int[cells][cells];
		bottleneck = new boolean[cells];

		compute_moves_and_dirs();
		compute_delta();
		compute_agent_distance();
		compute_bottlenecks();

		current.set(this);
	}

	private int find_bottlenecks(int a, int[] discovery, int parent, int[] time) {
		int children = 0;
		int low_a = discovery[a] = ++time[0];
		for (int b : moves[a])
			if (discovery[b] == 0) {
				children += 1;
				int low_b = find_bottlenecks(b, discovery, a, time);
				low_a = Math.min(low_a, low_b);
				if (parent != -1 && low_b >= discovery[a])
					bottleneck[a] = true;
			} else if (b != parent)
				low_a = Math.min(low_a, discovery[b]);
		if (parent == -1 && children > 1)
			bottleneck[a] = true;
		return low_a;
	}

	void compute_bottlenecks() {
		int[] discovery = new int[cells];
		int[] time = new int[1];
		find_bottlenecks(0, discovery, -1, time);
	}

	private void compute_moves_and_dirs() {
		for (int i = 0; i < cells; i++) {
			int c = 0;
			for (byte dir = 0; dir < 4; dir++)
				if (move(i, dir) != Bad)
					c += 1;
			moves[i] = new int[c];
			dirs[i] = new int[c];
			c = 0;
			for (byte dir = 0; dir < 4; dir++)
				if (move(i, dir) != Bad) {
					moves[i][c] = move(i, dir);
					dirs[i][c++] = dir;
				}
		}
	}

	private void compute_delta() {
		for (int i = 0; i < cells; i++) {
			Arrays.fill(delta[i], -1);
			for (byte dir = 0; dir < 4; dir++)
				if (move(i, dir) != Bad)
					delta[i][move(i, dir)] = dir;
		}
	}

	private void compute_agent_distance() {
		for (int i = 0; i < cells; i++)
			Arrays.fill(agent_distance[i], Integer.MAX_VALUE);
		for (int i = 0; i < cells; i++) {
			agent_distance[i][i] = 0;
			for (int a : visitor.init(i))
				for (int b : moves[a])
					if (!visitor.visited(b)) {
						agent_distance[i][b] = agent_distance[i][a] + 1;
						visitor.add(b);
					}
		}
		for (int i = 0; i < cells; i++)
			for (int j = 0; j < cells; j++)
				assert agent_distance[i][j] == agent_distance[j][i];
	}

	int state_space() {
		BigInteger agent_positions = BigInteger.valueOf(cells - num_boxes);
		return Util.combinations(alive, num_boxes).multiply(agent_positions).bitLength();
	}

	void print_cells() {
		low.print(p -> (p >= 10) ? (char) ((int) 'A' + (p - 10) % 26) : (char) ((int) '0' + p));
	}

	public static interface IndexToBool {
		boolean fn(int index);
	}

	void print(IndexToBool agent, IndexToBool box) {
		low.print(p -> {
			if (box.fn(p))
				return goal(p) ? LowLevel.BoxGoal : LowLevel.Box;
			if (agent.fn(p))
				return goal(p) ? LowLevel.AgentGoal : LowLevel.Agent;
			if (goal(p))
				return LowLevel.Goal;
			return ' ';
		});
	}

	void print(StateKey s) {
		print(p -> s.agent == p, p -> s.box(p));
	}

	final AutoTimer timer_isvalidlevel = new AutoTimer("is_valid_level");

	boolean is_valid_level(IndexToChar op) {
		try (AutoTimer t = timer_isvalidlevel.open()) {
			LowLevel low_clone = low.clone(op);
			low_clone.compute_walkable();
			if (!low_clone.check_boxes_and_goals_silent())
				return false;
			return true;
			//BitMatrix visited = new BitMatrix(low_clone.cells, low_clone.cells); // TODO this is huge, as cells is raw buffer size
			//return low_clone.new AreAllGoalsReachable().run(visited);
		}
	}

	int move(int src, int dir) {
		assert 0 <= src && src < cells : src + " vs " + cells;
		assert 0 <= dir && dir < 4 : dir;
		int dest = move[src * 4 + dir];
		assert dest == Bad || (0 <= dest && dest < cells);
		assert dest != src;
		return dest;
	}

	int rmove(int src, int dir) {
		return move(src, reverseDir(dir));
	}

	boolean goal(int i) {
		return i < alive && Bits.test(goal, i);
	}

	boolean is_solved(int[] box) {
		for (int i = 0; i < box.length; i++)
			if ((box[i] | goal[i]) != goal[i])
				return false;
		return true;
	}

	boolean is_solved_fast(int[] box) {
		return Arrays.equals(box, goal);
	}

	int degree(int pos) {
		return moves[pos].length;
	}

	boolean tunnel(int pos) {
		if (moves[pos].length != 2)
			return false;
		return (move(pos, Left) == -1 && move(pos, Right) == -1) || (move(pos, Up) == -1 && move(pos, Down) == -1);
	}

	public static final ThreadLocal<Level> current = new ThreadLocal<Level>();

	final int[][] moves; // TODO make also box_moves which doesn't include dead cells
	final int[][] dirs;
	final int[][] delta;
	final int[][] agent_distance;
	final boolean[] bottleneck;
	final Visitor visitor;

	final int alive; // number of cells that can contain boxes
	final int cells; // number of cells agent can walk on
	final int num_boxes;
	final State start;
	final int[] goal;

	// index, direction => index * 4 + direction
	// returns index to move to, or -1 if invalid (or wall)
	private int[] move;
}