package tintor.sokoban;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import tintor.common.Util;
import tintor.common.Visitor;

class Level {
	final LevelIO io;

	Level(String filename) {
		io = new LevelIO(filename);

		int w = io.width;
		cells = io.lines.size() * w;
		boolean[] box = new boolean[cells];
		boolean[] wall = new boolean[io.lines.size() * w];
		goal = new boolean[cells];
		int agent = -1;
		for (int i = 0; i < cells; i++) {
			String s = io.lines.get(i / w);
			char c = (i % w) < s.length() ? s.charAt(i % w) : LevelIO.Space;
			wall[i] = c == LevelIO.Wall;
			goal[i] = (c == LevelIO.AgentGoal || c == LevelIO.BoxGoal || c == LevelIO.Goal);
			box[i] = (c == LevelIO.BoxGoal || c == LevelIO.Box);
			if (c == LevelIO.AgentGoal || c == LevelIO.Agent)
				agent = i;
		}

		if (agent == -1)
			throw new IllegalArgumentException("no agent");
		if (Util.count(box) == 0)
			throw new IllegalArgumentException("count(box) == 0");
		if (Util.count(box) != Util.count(goal))
			throw new IllegalArgumentException(
					"count(box) != count(goal) " + Util.count(box) + " vs. " + Util.count(goal));

		move = new int[cells * 4];
		for (int pos = 0; pos < cells; pos++) {
			for (int dir = 0; dir < 4; dir++) {
				int m = -1;
				if (dir == 0 && (pos % w) > 0) // left
					m = pos - 1;
				if (dir == 1) // right
					m = (pos + 1) % w == 0 ? -1 : (pos + 1);
				if (dir == 2) // up
					m = pos < w ? -1 : pos - w;
				if (dir == 3 && pos + w < cells) // down
					m = pos + w;
				move[pos * 4 + dir] = (m != -1 && !wall[m]) ? m : -1;
			}
		}

		min_dist_to_solve = compute_min_dist_to_solve(w, wall);
		boolean[] walkable = compute_walkable(agent);

		// Compress level (find non-dead cells and agent only cells)
		alive = Util.count(cells, i -> !dead(i));

		int b = 0;
		int[] old_to_new = new int[cells];
		for (int a = 0; a < cells; a++)
			old_to_new[a] = -1;
		for (int a = 0; a < cells; a++)
			if (!dead(a)) {
				old_to_new[a] = b;
				b += 1;
			}
		for (int a = 0; a < cells; a++)
			if (walkable[a] && dead(a)) {
				old_to_new[a] = b;
				b += 1;
			}
		io.old_to_new = old_to_new;

		// Re-compute
		int new_cells = Util.count(walkable);
		final int[] new_min_dist_to_solve = new int[alive];
		for (int i = 0; i < cells; i++)
			if (!dead(i))
				new_min_dist_to_solve[old_to_new[i]] = min_dist_to_solve[i];
		int[] new_can_move = new int[4 * new_cells];
		for (int i = 0; i < cells; i++)
			if (walkable[i])
				for (int dir = 0; dir < 4; dir++)
					new_can_move[old_to_new[i] * 4 + dir] = (move[i * 4 + dir] != -1) ? old_to_new[move[i * 4 + dir]]
							: -1;
		boolean[] new_goal = new boolean[alive];
		for (int i = 0; i < cells; i++) {
			if (goal[i])
				new_goal[old_to_new[i]] = true;
		}
		boolean[] new_box = new boolean[alive];
		for (int i = 0; i < cells; i++) {
			if (box[i]) {
				new_box[old_to_new[i]] = true;
			}
		}
		int new_agent = old_to_new[agent];

		// Perform swap
		compacted = true;
		start = new State(new_agent, new_box);
		goal = new_goal;
		cells = new_cells;
		move = new_can_move;
		min_dist_to_solve = new_min_dist_to_solve;
		goal0 = Util.compress(goal, 0);

		visitor = new Visitor(cells);
		compute_moves_and_dirs();
		compute_delta();
		compute_agent_distance();
	}

	int[][] moves;
	int[][] dirs;

	private void compute_moves_and_dirs() {
		moves = new int[cells][];
		dirs = new int[cells][];
		for (int i = 0; i < cells; i++) {
			int c = 0;
			for (byte dir = 0; dir < 4; dir++)
				if (move(i, dir) != -1)
					c += 1;
			moves[i] = new int[c];
			dirs[i] = new int[c];
			c = 0;
			for (byte dir = 0; dir < 4; dir++)
				if (move(i, dir) != -1) {
					moves[i][c] = move(i, dir);
					dirs[i][c++] = dir;
				}
		}
	}

	int[][] delta;

	private void compute_delta() {
		delta = new int[cells][cells];
		for (int i = 0; i < cells; i++) {
			Arrays.fill(delta[i], -1);
			for (byte dir = 0; dir < 4; dir++)
				if (move(i, dir) != -1)
					delta[i][move(i, dir)] = dir;
		}
	}

	int[][] agent_distance;
	final Visitor visitor;

	private void compute_agent_distance() {
		agent_distance = new int[cells][cells];
		for (int i = 0; i < cells; i++)
			Arrays.fill(agent_distance[i], -1);
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
		BigInteger agent_positions = BigInteger.valueOf(cells - Util.count(start.box));
		return Util.combinations(alive, Util.count(start.box)).multiply(agent_positions).bitLength();
	}

	boolean[] compute_walkable(int agent) {
		Visitor visitor = new Visitor(cells);
		for (int a : visitor.init(agent))
			for (byte dir = 0; dir < 4; dir++) {
				int b = move(a, dir);
				if (b != -1 && !visitor.visited(b))
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

	int[] compute_min_dist_to_solve(int width, boolean[] wall) {
		int[] result = new int[cells];
		Deque<Triple> queue = new ArrayDeque<Triple>();
		for (int b = 0; b < cells; b++) {
			if (wall[b]) {
				result[b] = Integer.MAX_VALUE;
				continue;
			}
			if (goal[b]) {
				result[b] = 0;
				continue;
			}

			queue.clear();
			final boolean[][] set = new boolean[cells][cells];

			for (int a = 0; a < cells; a++) {
				if (wall[a] || manhatan_dist(a, b, width) != 1)
					continue;
				queue.push(new Triple(a, b, 0));
				set[a][b] = true;
			}

			int min_dist = Integer.MAX_VALUE;
			while (!queue.isEmpty()) {
				final Triple s = queue.pollFirst();
				for (byte dir = 0; dir < 4; dir++) {
					final int ap = move(s.agent, dir);
					if (ap == -1)
						continue;
					if (ap != s.box) {
						if (!set[ap][s.box]) {
							set[ap][s.box] = true;
							queue.push(new Triple(ap, s.box, s.dist + 1));
						}
						continue;
					}
					final int bp = move(ap, dir);
					if (bp == -1)
						continue;
					if (goal(bp)) {
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

	private static int manhatan_dist(int a, int b, int width) {
		int ax = a % width, ay = a / width;
		int bx = b % width, by = b / width;
		return Math.abs(ax - bx) + Math.abs(ay - by);
	}

	boolean dead(int i) {
		return (min_dist_to_solve != null && i < min_dist_to_solve.length) ? min_dist_to_solve[i] == Integer.MAX_VALUE
				: false;
	}

	void print_cells() {
		io.print(p -> (p >= 10) ? (char) ((int) 'A' + (p - 10) % 26) : (char) ((int) '0' + p));
	}

	void print_min_dist() {
		io.print(p -> {
			if (p >= min_dist_to_solve.length)
				return LevelIO.Space;
			int q = min_dist_to_solve[p];
			if (q < 10)
				return (char) ((int) '0' + q);
			if (q < 10 + 26)
				return (char) ((int) 'a' + q - 10);
			if (q < 10 + 26 * 2)
				return (char) ((int) 'A' + q - 10 - 26);
			return '~';
		});
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
		print(p -> s.agent == p, p -> s.box(p));
	}

	int move(int src, int dir) {
		assert 0 <= src && src < cells;
		assert 0 <= dir && dir < 4 : dir;
		int dest = move[src * 4 + dir];
		assert -1 <= dest && dest < cells && dest != src;
		return dest;
	}

	boolean goal(int i) {
		return i < goal.length ? goal[i] : false;
	}

	static int reverseDir(int dir) {
		return dir - (dir % 2) * 2 + 1;
	}

	boolean is_solved(boolean[] box) {
		for (int i = 0; i < alive; i++)
			if (box[i] && !goal[i])
				return false;
		return true;
	}

	boolean is_solved(long box0) {
		return (box0 | goal0) == goal0;
	}

	boolean compacted;
	final State start;
	boolean[] goal;
	long goal0;
	final int alive; // number of cells that can contain boxes
	int cells; // number of cells agent can walk on

	int[] min_dist_to_solve; // for every position of box

	// index, direction => index * 4 + direction
	// returns index to move to, or -1 if invalid (or wall)
	private int[] move;
}