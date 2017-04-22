package tintor.sokoban;

import java.math.BigInteger;
import java.util.Arrays;

import tintor.common.Bits;
import tintor.common.Util;
import tintor.common.Visitor;
import tintor.common.Zobrist;

class Level {
	static class MoreThan128AliveCellsError extends Error {
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

	Level(String filename) {
		low = new LowLevel(filename);

		final boolean[] walkable = low.compute_walkable();
		cells = Util.count(walkable);
		Zobrist.ensure(128 + cells);

		final boolean[] is_alive = low.compute_alive(walkable);
		alive = Util.count(is_alive);
		if (alive > 128)
			throw new MoreThan128AliveCellsError();

		int b = 0;
		int[] old_to_new = new int[low.cells];
		low.new_to_old = new int[cells];
		Arrays.fill(old_to_new, -1);
		for (int a = 0; a < low.cells; a++)
			if (is_alive[a]) {
				old_to_new[a] = b;
				low.new_to_old[b] = a;
				b += 1;
			}
		assert b == alive;
		for (int a = 0; a < low.cells; a++)
			if (walkable[a] && !is_alive[a]) {
				assert old_to_new[a] == -1;
				old_to_new[a] = b;
				low.new_to_old[b] = a;
				b += 1;
			}
		assert b == cells;

		move = new int[4 * cells];
		for (int i = 0; i < low.cells; i++)
			if (walkable[i])
				for (int dir = 0; dir < 4; dir++)
					move[old_to_new[i] * 4 + dir] = (low.move(i, dir) != Bad) ? old_to_new[low.move(i, dir)] : Bad;

		boolean[] goal = new boolean[alive];
		for (int i = 0; i < low.cells; i++)
			if (low.goal(i))
				goal[old_to_new[i]] = true;
		boolean[] box = new boolean[alive];
		for (int i = 0; i < low.cells; i++)
			if (low.box(i))
				box[old_to_new[i]] = true;

		start = new State(old_to_new[low.agent()], Util.compress(box, 0), Util.compress(box, 1), low.dist, -1, 0);
		goal0 = Util.compress(goal, 0);
		goal1 = Util.compress(goal, 1);
		num_boxes = Util.count(box);
		assert num_boxes == Util.count(goal) : num_boxes + " vs " + Util.count(goal);

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

	void print(State s) {
		print(p -> s.agent() == p, p -> s.box(p));
	}

	void print_alive(State s) {
		low.print(p -> p < alive ? '.' : ' ');
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
		if (i < 64)
			return Bits.test(goal0, i);
		if (i < 128)
			return Bits.test(goal1, i - 64);
		return false;
	}

	boolean is_solved(long box0, long box1) {
		return (box0 | goal0) == goal0 && (box1 | goal1) == goal1;
	}

	boolean is_solved(long box0) {
		assert alive <= 64;
		return (box0 | goal0) == goal0;
	}

	boolean is_solved(State s) {
		return (s.box0 | goal0) == goal0 && (s.box1 | goal1) == goal1;
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
	final long goal0;
	final long goal1;

	// index, direction => index * 4 + direction
	// returns index to move to, or -1 if invalid (or wall)
	private int[] move;
}