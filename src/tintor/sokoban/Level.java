package tintor.sokoban;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import tintor.common.Bits;
import tintor.common.Util;
import tintor.common.Visitor;
import tintor.common.Zobrist;

class Level {
	final LevelIO io;

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
		io = new LevelIO(filename);

		final int w = io.width;
		final int raw_cells = io.lines.size() * w;
		// TODO rename to raw_box raw_wall raw_goal
		boolean[] box = new boolean[raw_cells];
		boolean[] wall = new boolean[io.lines.size() * w];
		boolean[] goal = new boolean[raw_cells];
		int agent = -1;
		for (int i = 0; i < raw_cells; i++) {
			String s = io.lines.get(i / w);
			char c = (i % w) < s.length() ? s.charAt(i % w) : LevelIO.Space;
			wall[i] = c == LevelIO.Wall;
			goal[i] = (c == LevelIO.AgentGoal || c == LevelIO.BoxGoal || c == LevelIO.Goal);
			box[i] = (c == LevelIO.BoxGoal || c == LevelIO.Box);
			if (c == LevelIO.AgentGoal || c == LevelIO.Agent) {
				if (agent != -1)
					throw new IllegalArgumentException("multiple agents");
				agent = i;
			}
		}

		if (agent == -1)
			throw new IllegalArgumentException("no agent");
		if (Util.count(box) == 0)
			throw new IllegalArgumentException("no boxes");
		if (Util.count(box) != Util.count(goal))
			throw new IllegalArgumentException(
					"count(box) != count(goal) " + Util.count(box) + " vs. " + Util.count(goal));

		int[] raw_move = new int[raw_cells * 4];
		for (int pos = 0; pos < raw_cells; pos++)
			for (int dir = 0; dir < 4; dir++) {
				int m = Bad;
				if (dir == Left && (pos % w) > 0)
					m = pos - 1;
				if (dir == Right && (pos + 1) % w != 0)
					m = pos + 1;
				if (dir == Up && pos >= w)
					m = pos - w;
				if (dir == Down && pos + w < raw_cells)
					m = pos + w;
				raw_move[pos * 4 + dir] = (m != Bad && !wall[m]) ? m : Bad;
			}

		final int[] min_dist_to_solve = compute_min_dist_to_solve(w, goal, wall, raw_cells, raw_move);
		boolean[] walkable = compute_walkable(agent, raw_cells, raw_move);

		// Compress level (find non-dead cells and agent only cells)
		alive = Util.count(raw_cells, i -> min_dist_to_solve[i] != Integer.MAX_VALUE);
		if (alive > 128)
			throw new IllegalArgumentException("more than 128 alive cells");
		assert alive >= Util.count(box);

		int b = 0;
		int[] old_to_new = new int[raw_cells];
		for (int a = 0; a < raw_cells; a++)
			old_to_new[a] = -1;
		for (int a = 0; a < raw_cells; a++)
			if (min_dist_to_solve[a] != Integer.MAX_VALUE) {
				old_to_new[a] = b;
				b += 1;
			}
		for (int a = 0; a < raw_cells; a++)
			if (walkable[a] && min_dist_to_solve[a] == Integer.MAX_VALUE) {
				old_to_new[a] = b;
				b += 1;
			}
		io.old_to_new = old_to_new;

		// Re-compute
		cells = Util.count(walkable);
		Zobrist.ensure(128 + cells);
		move = new int[4 * cells];
		for (int i = 0; i < raw_cells; i++)
			if (walkable[i])
				for (int dir = 0; dir < 4; dir++)
					move[old_to_new[i] * 4 + dir] = (raw_move[i * 4 + dir] != Bad) ? old_to_new[raw_move[i * 4 + dir]]
							: Bad;
		boolean[] new_goal = new boolean[alive];
		for (int i = 0; i < raw_cells; i++)
			if (goal[i])
				new_goal[old_to_new[i]] = true;
		boolean[] new_box = new boolean[alive];
		for (int i = 0; i < raw_cells; i++)
			if (box[i]) {
				assert old_to_new[i] < alive : old_to_new[i] + " vs " + alive;
				new_box[old_to_new[i]] = true;
			}
		int new_agent = old_to_new[agent];

		start = new State(new_agent, Util.compress(new_box, 0), Util.compress(new_box, 1), 0, -1, false);
		goal0 = Util.compress(new_goal, 0);
		goal1 = Util.compress(new_goal, 1);
		num_boxes = Util.count(new_box);
		assert num_boxes == Util.count(new_goal);

		visitor = new Visitor(cells);
		moves = new int[cells][];
		dirs = new int[cells][];
		delta = new int[cells][cells];
		agent_distance = new int[cells][cells];

		compute_moves_and_dirs();
		compute_delta();
		compute_agent_distance();

		current.set(this);
	}

	final int[][] moves; // TODO make also box_moves which doesn't include dead cells
	final int[][] dirs;
	final int[][] delta;
	final int[][] agent_distance;
	final Visitor visitor;

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

	boolean[] compute_walkable(int agent, int raw_cells, int[] raw_move) {
		Visitor visitor = new Visitor(raw_cells);
		for (int a : visitor.init(agent))
			for (byte dir = 0; dir < 4; dir++) {
				int b = raw_move[a * 4 + dir];
				if (b != Bad && !visitor.visited(b))
					visitor.add(b);
			}
		return visitor.visited();
	}

	static class Triple {
		final int agent, box, dist;

		Triple(int agent, int box, int dist) {
			this.agent = agent;
			this.box = box;
			this.dist = dist;
		}
	}

	int[] compute_min_dist_to_solve(int width, boolean[] goal, boolean[] wall, int raw_cells, int[] raw_move) {
		int[] result = new int[raw_cells];
		Deque<Triple> queue = new ArrayDeque<Triple>();
		final boolean[][] set = new boolean[raw_cells][raw_cells];

		for (int b = 0; b < raw_cells; b++) {
			if (wall[b]) {
				result[b] = Integer.MAX_VALUE;
				continue;
			}
			if (goal[b]) {
				result[b] = 0;
				continue;
			}

			queue.clear();
			for (int a = 0; a < raw_cells; a++) {
				Arrays.fill(set[a], false);
				if (wall[a] || !is_next_to(a, b, width))
					continue;
				queue.push(new Triple(a, b, 0));
				set[a][b] = true;
			}

			int min_dist = Integer.MAX_VALUE;
			while (!queue.isEmpty()) {
				final Triple s = queue.pollFirst();
				for (int dir = 0; dir < 4; dir++) {
					final int ap = raw_move[s.agent * 4 + dir];
					if (ap == Bad)
						continue;
					if (ap != s.box) {
						if (!set[ap][s.box]) {
							set[ap][s.box] = true;
							queue.push(new Triple(ap, s.box, s.dist + 1));
						}
						continue;
					}
					final int bp = raw_move[ap * 4 + dir];
					if (bp == Bad)
						continue;
					if (goal[bp]) {
						min_dist = s.dist + 1;
						queue.clear();
						break;
					}
					if (!set[ap][bp]) {
						set[ap][bp] = true;
						queue.push(new Triple(ap, bp, s.dist + 1));
					}
				}
			}
			result[b] = min_dist;
		}
		return result;
	}

	private static boolean is_next_to(int a, int b, int width) {
		int ax = a % width, ay = a / width;
		int bx = b % width, by = b / width;
		return Math.abs(ax - bx) + Math.abs(ay - by) == 1;
	}

	void print_cells() {
		io.print(p -> (p >= 10) ? (char) ((int) 'A' + (p - 10) % 26) : (char) ((int) '0' + p));
	}

	public static interface IndexToBool {
		boolean fn(int index);
	}

	void print(IndexToBool agent, IndexToBool box) {
		io.print(p -> {
			if (box.fn(p))
				return goal(p) ? LevelIO.BoxGoal : LevelIO.Box;
			if (agent.fn(p))
				return goal(p) ? LevelIO.AgentGoal : LevelIO.Agent;
			if (goal(p))
				return LevelIO.Goal;
			return ' ';
		});
	}

	void print(State s) {
		print(p -> s.agent() == p, p -> s.box(p));
	}

	int move(int src, int dir) {
		assert 0 <= src && src < cells : src + " vs " + cells;
		assert 0 <= dir && dir < 4 : dir;
		int dest = move[src * 4 + dir];
		assert dest == Bad || (0 <= dest && dest < cells);
		assert dest != src;
		return dest;
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

	final int alive; // number of cells that can contain boxes
	final int cells; // number of cells agent can walk on
	final int num_boxes;
	final State start;
	final long goal0;
	final long goal1;

	// index, direction => index * 4 + direction
	// returns index to move to, or -1 if invalid (or wall)
	private int[] move;

	public static final ThreadLocal<Level> current = new ThreadLocal<Level>();
}