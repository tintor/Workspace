package tintor.sokoban;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.Bits;
import tintor.common.PairVisitor;
import tintor.common.Util;
import tintor.common.Visitor;
import tintor.sokoban.LowLevel.IndexToChar;

public final class Level {
	// Note: could easily increase this limit
	public static class MoreThan256CellsError extends Error {
		private static final long serialVersionUID = 1L;
	}

	public final LowLevel low;

	// direction
	public final static int Left = 0;
	public final static int Up = 1;
	public final static int Right = 2;
	public final static int Down = 3;

	// Used by move() to signal invalid move
	final static int Bad = -1;

	static int reverseDir(int dir) {
		assert 0 <= dir && dir < 4 : dir;
		return dir ^ 2;
	}

	public static ArrayList<Level> loadAll(String filename) {
		int count = LevelLoader.numberOfLevels(filename);
		ArrayList<Level> levels = new ArrayList<Level>();
		for (int i = 1; i <= count; i++)
			try {
				levels.add(load(filename + ":" + i));
			} catch (MoreThan256CellsError e) {
			}
		return levels;
	}

	public static Level load(String filename) {
		LowLevel low = new LowLevel(LevelLoader.load(filename), filename);

		boolean[] walkable = low.compute_walkable();
		low.add_walls(walkable);
		low.check_boxes_and_goals();
		if (Util.count(walkable) > 256)
			throw new MoreThan256CellsError(); // just to avoid calling compute_alive() for huge levels

		walkable = low.minimize(walkable);

		PairVisitor visitor = new PairVisitor(low.cells(), low.cells());
		if (!low.are_all_goals_reachable_full(visitor))
			throw new IllegalArgumentException("level contains unreachable goal");
		final boolean[] is_alive = low.compute_alive(visitor, walkable);

		return new Level(low, walkable, is_alive);
	}

	private Level(LowLevel low, boolean[] walkable, boolean[] is_alive) {
		this.low = low;
		low.check_boxes_and_goals();

		cells = Util.count(walkable);
		if (cells > 256)
			throw new MoreThan256CellsError();
		alive = Util.count(is_alive);

		int b = 0;
		int[] old_to_new = Array.ofInt(low.cells(), -1);
		low.new_to_old = new int[cells];
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

		transforms = new LevelTransforms(this, walkable, old_to_new);

		visitor = new Visitor(cells);
		moves = new int[cells][];
		dirs = new int[cells][];
		delta = Array.ofInt(cells, cells, -1);
		agent_distance = Array.ofInt(cells, cells, Integer.MAX_VALUE);
		bottleneck = new boolean[cells];

		compute_moves_and_dirs();
		compute_delta();
		compute_agent_distance();
		find_bottlenecks(0, new int[cells], -1, new int[1]);

		room = new int[cells];
		has_goal_rooms = has_goal_rooms();
		has_goal_zone = has_goal_zone();
	}

	public final boolean has_goal_zone; // at least two goals that are next to each other

	private boolean has_goal_zone() {
		for (int a = 0; a < alive; a++)
			if (goal(a))
				for (int b : moves[a])
					if (goal(b))
						return true;
		return false;
	}

	public final LevelTransforms transforms;

	private boolean has_goal_rooms() {
		if (Util.all(cells, p -> !bottleneck_tunnel(p)))
			return false;

		Arrays.fill(room, -1);
		int room_count = 0;
		for (int a = 0; a < cells; a++) {
			if (bottleneck_tunnel(a) || room[a] != -1)
				continue;
			visitor.init(a);
			while (!visitor.done()) {
				int b = visitor.next();
				room[b] = room_count;
				for (int c : moves[b])
					if (!bottleneck_tunnel(c))
						visitor.try_add(c);
			}
			room_count += 1;
		}

		int[] goals = new int[room_count];

		// count goals in each room
		for (int a = 0; a < alive; a++)
			if (goal(a)) {
				if (room[a] == -1)
					return false;
				goals[room[a]] += 1;
			}

		// check that all boxes are outside of goal rooms
		for (int a = 0; a < alive; a++)
			if (start.box(a) && room[a] != -1 && goals[room[a]] > 0)
				return false;
		return true;
	}

	final boolean has_goal_rooms;
	final int[] room;

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
		for (int i = 0; i < cells; i++)
			for (int dir = 0; dir < 4; dir++)
				if (move(i, dir) != Bad)
					delta[i][move(i, dir)] = dir;
	}

	private void compute_agent_distance() {
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

	public int state_space() {
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

	char[] render(IndexToBool agent, IndexToBool box) {
		return low.render(p -> {
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

	char[] render(StateKey s) {
		return render(p -> s.agent == p, p -> s.box(p));
	}

	static final AutoTimer timer_isvalidlevel = new AutoTimer("is_valid_level");

	boolean is_valid_level(IndexToChar op) {
		try (AutoTimer t = timer_isvalidlevel.open()) {
			LowLevel low_clone = low.clone(op);
			low_clone.compute_walkable();
			if (!low_clone.check_boxes_and_goals_silent())
				return false;
			PairVisitor visitor = new PairVisitor(low_clone.cells(), low_clone.cells());
			return has_goal_rooms ? low_clone.are_all_goals_reachable_quick(visitor)
					: low_clone.are_all_goals_reachable_full(visitor);
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

	boolean bottleneck_tunnel(int pos) {
		return bottleneck[pos] && tunnel(pos);
	}

	final int[][] moves; // TODO make also box_moves which doesn't include dead cells
	final int[][] dirs;
	final int[][] delta;
	final int[][] agent_distance;
	public final boolean[] bottleneck;
	final Visitor visitor;

	public final int alive; // number of cells that can contain boxes
	public final int cells; // number of cells agent can walk on
	public final int num_boxes;
	public final State start;
	final int[] goal;

	// index, direction => index * 4 + direction
	// returns index to move to, or -1 if invalid (or wall)
	private int[] move;
}