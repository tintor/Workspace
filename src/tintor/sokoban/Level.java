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

		boolean[] walkable = low.compute_walkable();
		low.add_walls(walkable);
		low.check_boxes_and_goals();
		if (Util.count(walkable) > 256)
			throw new MoreThan256CellsError(); // just to avoid calling compute_alive() for huge levels

		walkable = low.minimize(walkable);

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
		start = new State(old_to_new[low.agent()], Util.compressToIntArray(box), 0, low.dist, 0, 0, 0);
		goal = Util.compressToIntArray(goalb);
		num_boxes = Util.count(box);
		assert num_boxes == Util.count(goalb);

		transforms = new int[7][];
		inv_transforms = new int[7][];
		int w = 0;
		for (int symmetry = 1; symmetry <= 7; symmetry += 1)
			if (low.is_symmetric(walkable, symmetry)) {
				int[] mapping = new int[cells];
				int[] inv_mapping = new int[cells];
				for (int i = 0; i < low.cells(); i++)
					if (walkable[i]) {
						int a = old_to_new[i];
						int c = old_to_new[low.transform(i, symmetry)];
						mapping[c] = a;
						inv_mapping[a] = c;
					}
				transforms[w] = mapping;
				inv_transforms[w++] = inv_mapping;

				for (int i = 0; i < alive; i++)
					assert 0 <= mapping[i] && mapping[i] < alive;
				for (int i = alive; i < cells; i++)
					assert alive <= mapping[i] && mapping[i] < cells;
				assert Util.all(cells, i -> inv_mapping[mapping[i]] == i);
				assert Util.sum(cells, i -> i - mapping[i]) == 0;
			}
		transforms = Arrays.copyOf(transforms, w);
		transforms = new int[0][];
		inv_transforms = Arrays.copyOf(inv_transforms, w);
		inv_transforms = new int[0][];

		visitor = new Visitor(cells);
		moves = new int[cells][];
		dirs = new int[cells][];
		delta = new int[cells][cells];
		agent_distance = new int[cells][cells];
		bottleneck = new boolean[cells];

		compute_moves_and_dirs();
		compute_delta();
		compute_agent_distance();
		find_bottlenecks(0, new int[cells], -1, new int[1]);

		current.set(this);
	}

	// For all the level symmetries
	private int[][] transforms;
	private int[][] inv_transforms;

	private int[] transform(int[] box, int[] mapping) {
		int[] out = new int[box.length];
		for (int i = 0; i < alive; i++)
			if (Bits.test(box, i))
				Bits.set(out, mapping[i]);
		return out;
	}

	/*	private int[] transform(int nbox0, int[] box, int[] mapping) {
			int[] out = new int[box.length];
			out[0] = nbox0;
			for (int i = 32; i < alive; i++)
				if (Bits.test(box, mapping[i]))
					Bits.set(out, i);
			return out;
		}
	
		private int transform_one(int[] box, int[] mapping) {
			int out = 0;
			for (int i = 0; i < Math.min(32, alive); i++)
				if (Bits.test(box, mapping[i]))
					out = Bits.set(out, i);
			return out;
		}*/

	private static int compare(int[] a, int[] b) {
		assert a.length == b.length;
		for (int i = 0; i < a.length; i++) {
			if (a[i] < b[i])
				return -1;
			if (a[i] > b[i])
				return 1;
		}
		return 0;
	}

	StateKey normalize(StateKey k) {
		if (transforms.length == 0)
			return k;

		int agent = k.agent;
		int[] box = k.box;

		for (int[] map : transforms) {
			int nagent = map[k.agent];
			if (nagent < agent) {
				agent = nagent;
				box = transform(k.box, map);
				continue;
			}

			if (nagent > agent)
				continue;

			//int nbox0 = transform_one(k.box, map);
			//if (nbox0 > box[0])
			//	continue;

			int[] nbox = transform(/*nbox0, */k.box, map);
			assert Arrays.equals(nbox, transform(k.box, map));
			if (compare(nbox, box) < 0) {
				agent = nagent;
				box = nbox;
			}
		}
		return box == k.box ? k : new StateKey(agent, box);
	}

	State normalize(State s) {
		assert s.symmetry == 0;
		if (transforms.length == 0)
			return s;

		int agent = s.agent;
		int[] box = s.box;
		int symmetry = 0;
		int dir = s.dir;
		int prev_agent = s.prev_agent;

		for (int i = 0; i < transforms.length; i++) {
			int[] map = transforms[i];

			int nagent = map[s.agent];
			if (nagent < agent) {
				agent = nagent;
				box = transform(s.box, map);
				symmetry = i + 1;
				dir = low.transform_dir(s.dir, s.symmetry, false);
				prev_agent = map[s.prev_agent];
				continue;
			}

			if (nagent > agent)
				continue;

			//int nbox0 = transform_one(s.box, map);
			//if (nbox0 > box[0])
			//	continue;

			int[] nbox = transform(/*nbox0, */s.box, map);
			assert Arrays.equals(nbox, transform(s.box, map));
			if (compare(nbox, box) < 0) {
				agent = nagent;
				box = nbox;
				symmetry = i + 1;
				dir = low.transform_dir(s.dir, s.symmetry, false);
				prev_agent = map[s.prev_agent];
			}
		}
		State q = s;
		if (box != s.box) {
			q = new State(agent, box, symmetry, s.dist, dir, s.pushes, prev_agent);
			q.set_heuristic(s.total_dist - s.dist);
		}
		assert ((StateKey) q).equals(normalize((StateKey) s));
		assert Arrays.equals(denormalize(q).box, s.box);
		assert denormalize(q).agent == s.agent;
		assert denormalize(q).pushes == s.pushes;
		assert denormalize(q).dist == s.dist;
		assert denormalize(q).total_dist == s.total_dist;
		assert denormalize(q).prev_agent == s.prev_agent;
		assert denormalize(q).dir == s.dir;
		assert denormalize(q).equals(s);
		return q;
	}

	State denormalize(State s) {
		if (s.symmetry == 0)
			return s;
		int[] map = inv_transforms[s.symmetry - 1];
		State q = new State(map[s.agent], transform(s.box, map), 0, s.dist,
				low.transform_dir(s.dir, s.symmetry, true), s.pushes, map[s.prev_agent]);
		q.set_heuristic(s.total_dist - s.dist);
		return q;
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