package tintor.sokoban;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.val;
import lombok.experimental.ExtensionMethod;
import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.BipartiteMatching;
import tintor.common.Bits;
import tintor.common.For;
import tintor.common.Util;
import tintor.sokoban.Cell.Dir;

// TODO move inside Cell class
final class Move implements Comparable<Move> {
	Cell cell;
	final Dir dir;
	Dir exit_dir; // in case of dead tunnels if can be different from dir
	int dist;
	boolean alive = true;

	Move(Cell cell, Dir dir, int dist, boolean alive) {
		assert cell != null;
		assert dir != null;
		assert dist > 0;
		this.cell = cell;
		this.dir = this.exit_dir = dir;
		this.dist = dist;
		this.alive = alive;
	}

	@Override
	public int compareTo(Move o) {
		return dist - o.dist;
	}
}

@ExtensionMethod(Array.class)
public final class Level {
	final static char Box = '$';
	final static char Wall = '#';
	final static char BoxGoal = '*';
	final static char AgentGoal = '+';
	final static char Goal = '.';
	final static char Agent = '@';
	final static char Space = ' ';

	public final String name;
	private final char[] buffer;
	public Cell[] cells;
	public int alive;
	public final int num_boxes;
	public final CellVisitor visitor;
	public final State start;
	int[] goal;
	public final CellLevelTransforms transforms;
	public final boolean has_goal_rooms;
	public final boolean has_goal_zone;

	public static class MoreThan256CellsError extends Error {
		private static final long serialVersionUID = 1L;
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
		return new Level(LevelLoader.load(filename), filename);
	}

	private Level(char[] buffer, String name) {
		int w = 0;
		while (buffer[w] != '\n')
			w++;
		assert buffer.length % (w + 1) == 0;
		this.name = name;
		this.buffer = buffer;

		Cell[] grid = create_cell_grid(w);
		connect_nearby_cells(grid, w);
		Cell agent = find_agent(grid);

		val p = move_agent_from_deadend(grid, agent);
		int dist = p.dist;
		agent = p.agent;

		remove_deadend_cells(grid, agent);
		visitor = new CellVisitor(buffer.length);
		remove_unreachable_cells(agent, grid);
		clean_buffer_chars(w);
		assign_compact_cell_ids();
		init_goal_ordinals();

		if (name == null) {
			transforms = null;
			start = null;
			has_goal_rooms = false;
			has_goal_zone = false;
			num_boxes = 0;
			goal = null;
			alive = Integer.MAX_VALUE;
			return;
		}

		add_nice_walls(w);

		// Check boxes and goals
		num_boxes = Array.count(cells, c -> c.box);
		if (num_boxes == 0)
			throw new IllegalArgumentException("no boxes");
		if (num_boxes != Array.count(cells, c -> c.goal))
			throw new IllegalArgumentException("count(box) != count(goal)");

		// Make sure all goals are reachable by boxes
		CellPairVisitor pair_visitor = new CellPairVisitor(cells.length, cells.length, cells);
		if (!are_all_goals_reachable_full(pair_visitor))
			throw new IllegalArgumentException("level contains unreachable goal");

		compute_alive_cells(pair_visitor);
		remove_useless_alive_cells();
		int room_count = compute_rooms();

		// Set alive of all Moves
		for (Cell a : cells)
			for (Move am : a.moves)
				am.alive = a.alive && am.cell.alive;

		compress_dead_tunnels(agent);
		compress_alive_tunnels(agent);
		compress_cell_ids();
		pair_visitor.cells = cells;
		for (Cell a : cells)
			Arrays.sort(a.moves);
		move_alive_cells_in_front();
		compute_distances_to_each_goal(Array.count(cells, c -> c.goal), pair_visitor);
		start = compute_goal_and_start(agent, dist);
		transforms = new CellLevelTransforms(this, w + 1, buffer.length / (w + 1), grid);
		find_bottlenecks(agent, new int[cells.length], null, new int[1]);
		has_goal_rooms = has_goal_rooms(room_count);
		has_goal_zone = For.any(cells, a -> a.goal && For.any(a.moves, e -> e.cell.goal));
	}

	private Cell[] create_cell_grid(int w) {
		Cell[] grid = new Cell[buffer.length];
		for (int y = w + 1; y < buffer.length - w - 1; y += w + 1)
			for (int xy = y + 1; xy < y + w - 1; xy++)
				if (buffer[xy] != Wall)
					grid[xy] = new Cell(this, xy, buffer[xy]);
		return grid;
	}

	private void connect_nearby_cells(Cell[] grid, int w) {
		Move[] m = new Move[4];
		for (Cell c : grid)
			if (c != null) {
				int p = 0;
				for (Dir dir : Dir.values()) {
					int xy = move(c.xy, dir, w + 1, buffer.length);
					if (grid[xy] != null)
						c.dir[dir.ordinal()] = m[p++] = new Move(grid[xy], dir, 1, true);
				}
				c.moves = Arrays.copyOf(m, p);
			}
	}

	private Cell find_agent(Cell[] grid) {
		if (Array.count(grid, c -> c != null && (buffer[c.xy] == Agent || buffer[c.xy] == AgentGoal)) != 1)
			throw new IllegalArgumentException("need exactly one agent");
		Cell agent = null;
		for (Cell c : grid)
			if (c != null && (buffer[c.xy] == Agent || buffer[c.xy] == AgentGoal))
				agent = c;
		return agent;
	}

	@AllArgsConstructor
	static class DistAgent {
		int dist;
		Cell agent;
	}

	private DistAgent move_agent_from_deadend(Cell[] grid, Cell agent) {
		int dist = 0;
		// TODO remove restriction on not being able to push box to remove dead end
		while (!agent.goal && agent.moves.length == 1 && !agent.moves[0].cell.box) {
			Cell b = agent.moves[0].cell;
			buffer[agent.xy] = Wall;
			buffer[b.xy] = Agent;
			grid[agent.xy] = null;
			detach(b, agent);
			agent = b;
			assert grid[agent.xy] != null;
			dist += 1;
		}
		return new DistAgent(dist, agent);
	}

	private void remove_deadend_cells(Cell[] grid, Cell agent) {
		for (Cell a : grid)
			while (a != null && a != agent && a.moves.length == 1 && !a.goal && !a.box) {
				Cell b = a.moves[0].cell;
				buffer[a.xy] = Wall;
				grid[a.xy] = null;
				detach(b, a);
				a = b;
			}
	}

	private void remove_unreachable_cells(Cell agent, Cell[] grid) {
		visitor.add(agent);
		for (Cell a : visitor)
			for (Move e : grid[a.xy].moves)
				visitor.try_add(e.cell);
		for (int i = 0; i < grid.length; i++)
			if (!visitor.visited()[i])
				grid[i] = null;
	}

	private void clean_buffer_chars(int w) {
		for (int i = 0; i < buffer.length; i++)
			if (!visitor.visited(i) && buffer[i] != '\n' & buffer[i] != Space)
				if (!is_close_to_visited(i, visitor.visited(), w + 1, buffer.length / (w + 1), false))
					buffer[i] = Space;
	}

	private void assign_compact_cell_ids() {
		int count = visitor.tail();
		// TODO move this after tunnel compression, as cell count will decrease
		if (count > 256)
			throw new MoreThan256CellsError(); // until I make compute_alive() faster
		cells = new Cell[count];
		Array.copy(visitor.queue(), 0, cells, 0, count);
		for (int i = 0; i < cells.length; i++)
			cells[i].id = i;
	}

	private void add_nice_walls(int w) {
		for (int i = 0; i < buffer.length; i++)
			if (!visitor.visited(i) && buffer[i] != '\n' && buffer[i] != Wall)
				if (is_close_to_visited(i, visitor.visited(), w + 1, buffer.length / (w + 1), true))
					buffer[i] = Wall;
	}

	private void init_goal_ordinals() {
		int i = 0;
		for (Cell c : cells)
			if (c.goal)
				c.goal_ordinal = i++;
	}

	private void remove_useless_alive_cells() {
		for (Cell a : cells)
			while (true) {
				Cell b = end_of_half_tunnel(a);
				if (b == null || a.box || a.goal)
					break;
				a.alive = false;
				a = b;
			}
	}

	private static Cell end_of_half_tunnel(Cell pos) {
		if (!pos.alive || pos.moves.length > 2)
			return null;
		int count = 0;
		Cell b = null;
		for (Move e : pos.moves)
			if (e.cell.alive) {
				count += 1;
				b = e.cell;
			}
		if (count != 1 || b.moves.length != 2)
			return null;
		return Array.count(b.moves, e -> e.cell.alive) == 2 ? b : null;
	}

	private int compute_rooms() {
		int room_count = 0;
		for (Cell a : cells) {
			if (a.moves.length == 2 || a.room > 0)
				continue;
			room_count += 1;
			for (Cell b : visitor.init(a)) {
				b.room = room_count;
				for (Move e : b.moves)
					if (e.cell.moves.length != 2)
						visitor.try_add(e.cell);
			}
		}
		return room_count;
	}

	private void compress_dead_tunnels(Cell agent) {
		for (Cell a : cells)
			if (a != null && (a.alive || a.moves.length != 2))
				for (Move am : a.moves) {
					Cell b = am.cell;
					while (!b.alive && b.moves.length == 2 && b != agent && !b.goal && !b.box) {
						// remove B, and connect A and C directly
						Move bma = b.rmove(am.exit_dir);
						Move bmc = Array.find(b.moves, m -> m != bma);
						Cell c = bmc.cell;
						if (a.connected_to(c))
							break;
						Move cm = c.rmove(bmc.exit_dir);
						cells[b.id] = null;
						buffer[b.xy] = 'o';
						am.cell = c;
						am.dist += bmc.dist;
						am.alive = false;
						am.exit_dir = bmc.exit_dir;
						cm.cell = a;
						cm.dist += bma.dist;
						cm.alive = false;
						cm.exit_dir = bma.exit_dir;
						b = c;
					}
				}
		for (Cell a : cells)
			if (a != null)
				for (Move am : a.moves) {
					Cell b = am.cell;
					Move bm = b.rmove(am.exit_dir);
					assert am.dist == bm.dist;
					if (am.dist > 1)
						assert !am.alive && !bm.alive;
					assert am.exit_dir == bm.dir.reverse;
					assert bm.exit_dir == am.dir.reverse;
				}
	}

	private void compress_alive_tunnels(Cell agent) {
		if (compress_tunnels)
			for (Cell a : cells)
				if (a != null && a.alive && a.tunnel_entrance()) {
					Move e = Array.find(a.moves, p -> p.cell.moves.length == 2);
					Cell b = e.cell;
					int dist = e.dist;
					boolean is_alive = b.alive;
					while (b.tunnel_interior() && !b.goal) {
						Cell c = cells[b.moves[0].cell.id ^ b.moves[1].cell.id ^ b.id];
						is_alive = is_alive && c.alive;
						cells[b.id] = null;
						buffer[b.xy] = 'o';
						detach(a, b);
						attach(a, new Move(c, e.dir, ++dist, is_alive));
						detach(c, b);
						// TODO second direction might be different if tunnel isn't straight
						attach(c, new Move(c, e.dir.reverse, dist, is_alive));
						alive -= 1;
						b = c;
					}
				}
	}

	private void compress_cell_ids() {
		int id = 0;
		for (int i = 0; i < cells.length; i++)
			if (cells[i] != null)
				cells[id++] = cells[i];
		cells = Arrays.copyOf(cells, id);
		for (int i = 0; i < cells.length; i++)
			cells[i].id = i;
	}

	private void move_alive_cells_in_front() {
		int p = 0, q = cells.length - 1;
		while (true) {
			while (p < q && cells[p].alive)
				p += 1;
			while (p < q && !cells[q].alive)
				q -= 1;
			if (p >= q)
				break;
			Cell e = cells[p];
			cells[p] = cells[q];
			cells[q] = e;
			cells[p].id = p++;
			cells[q].id = q--;
		}
		alive = cells[p].alive ? p + 1 : p;
		assert Util.all(alive, e -> cells[e].alive);
		assert Util.all(alive, cells.length, e -> !cells[e].alive);
	}

	private static final boolean compress_tunnels = false;

	private boolean has_goal_rooms(int room_count) {
		if (For.any(cells, a -> a.goal && a.room == 0))
			return false;

		// count goals in each room
		int[] goals_in_room = new int[room_count + 1];
		For.each(cells, a -> goals_in_room[a.room] += a.goal ? 1 : 0);

		// check that all boxes are outside of goal rooms
		return For.all(cells, a -> !a.box || goals_in_room[a.room] == 0);
	}

	public int state_space() {
		BigInteger agent_positions = BigInteger.valueOf(cells.length - num_boxes);
		return Util.combinations(alive, num_boxes).multiply(agent_positions).bitLength();
	}

	private static int find_bottlenecks(Cell a, int[] discovery, Cell parent, int[] time) {
		int children = 0;
		int low_a = discovery[a.id] = ++time[0];
		for (Move e : a.moves)
			if (discovery[e.cell.id] == 0) {
				children += 1;
				int low_b = find_bottlenecks(e.cell, discovery, a, time);
				low_a = Math.min(low_a, low_b);
				if (parent != null && low_b >= discovery[a.id])
					a.bottleneck = true;
			} else if (e.cell != parent)
				low_a = Math.min(low_a, discovery[e.cell.id]);
		if (parent == null && children > 1)
			a.bottleneck = true;
		return low_a;
	}

	private void compute_distances_to_each_goal(int goals, CellPairVisitor pair_visitor) {
		For.each(cells, g -> g.distance_box = Array.ofInt(goals, Cell.Infinity));
		int[][] distance = Array.ofInt(cells.length, alive, Cell.Infinity);
		for (Cell g : cells)
			if (g.goal) {
				pair_visitor.init();
				for (Move e : g.moves)
					if (pair_visitor.try_add(e.cell, g))
						distance[e.cell.id][g.id] = e.dist - 1;
				g.distance_box[g.goal_ordinal] = 0;

				while (!pair_visitor.done()) {
					final Cell agent = pair_visitor.first();
					final Cell box = pair_visitor.second();
					assert distance[agent.id][box.id] != Cell.Infinity;
					box.distance_box[g.goal_ordinal] = Math.min(box.distance_box[g.goal_ordinal],
							distance[agent.id][box.id]);

					for (Move e : agent.moves) {
						// TODO moves included only if ! optimal
						Cell c = e.cell;
						if (c != box && pair_visitor.try_add(c, box))
							distance[c.id][box.id] = distance[agent.id][box.id] + e.dist;
						Move m = agent.rmove(e.dir);
						if (agent.alive && m != null && m.cell == box && pair_visitor.try_add(c, agent))
							distance[c.id][agent.id] = distance[agent.id][box.id] + e.dist;
					}
				}
			}
	}

	private State compute_goal_and_start(Cell agent, int dist) {
		int[] box = new int[(alive + 31) / 32];
		goal = new int[(alive + 31) / 32];
		for (Cell c : cells) {
			if (c.box)
				Bits.set(box, c.id);
			if (c.goal)
				Bits.set(goal, c.id);
		}
		return new State(agent.id, box, 0, dist, 0, 0, 0);
	}

	public boolean are_all_goals_reachable_full(CellPairVisitor visitor) {
		int[] num = new int[1];
		int[] box_ordinal = Array.ofInt(cells.length, p -> cells[p].box ? num[0]++ : -1);
		int num_boxes = num[0];
		if (num_boxes == 0)
			return true;

		boolean[][] can_reach = new boolean[num_boxes][num_boxes];
		main:
		for (Cell g : cells) {
			if (!g.goal)
				continue;
			assert g.goal_ordinal >= 0;
			int count = 0;
			if (g.box) {
				can_reach[box_ordinal[g.id]][g.goal_ordinal] = true;
				if (++count == num_boxes)
					continue main;
			}
			visitor.init();
			for (Move e : g.moves)
				visitor.add(e.cell, g);
			while (!visitor.done()) {
				final Cell a = visitor.first();
				final Cell b = visitor.second();
				for (Move e : a.moves) {
					Cell c = e.cell;
					if (c == b)
						continue;
					visitor.try_add(c, b);
					Move m = a.rmove(e.dir);
					if (m != null && m.cell == b && visitor.try_add(c, a))
						if (a.box && !can_reach[box_ordinal[a.id]][g.goal_ordinal]) {
							can_reach[box_ordinal[a.id]][g.goal_ordinal] = true;
							if (++count == num_boxes)
								continue main;
						}
				}
			}
			if (count == 0)
				return false;
		}
		return BipartiteMatching.maxBPM(can_reach) == num_boxes;
	}

	// TODO instead of searching forward N times => search backward once from every goal
	private void compute_alive_cells(CellPairVisitor visitor) {
		for (Cell b : cells) {
			if (b.goal) {
				b.alive = true;
				continue;
			}

			visitor.init();
			for (Move e : b.moves)
				visitor.add(e.cell, b);

			loop:
			while (!visitor.done()) {
				final Cell agent = visitor.first();
				final Cell box = visitor.second();

				for (Move e : agent.moves) {
					if (e.cell != box) {
						visitor.try_add(e.cell, box);
						continue;
					}
					final Move q = e.cell.move(e.dir);
					if (q == null)
						continue;
					if (q.cell.goal) {
						b.alive = true;
						break loop;
					}
					visitor.try_add(e.cell, q.cell);
				}
			}
		}
	}

	private static boolean is_close_to_visited(int pos, boolean[] visited, int width, int height, boolean diagonal) {
		int x = pos % width, y = pos / width;
		for (int ax = Math.max(0, x - 1); ax <= Math.min(x + 1, width - 2); ax++)
			for (int ay = Math.max(0, y - 1); ay <= Math.min(y + 1, height - 1); ay++)
				if ((diagonal || ax == x || ay == y) && visited[ay * width + ax])
					return true;
		return false;
	}

	private static int move(int xy, Dir dir, int width, int length) {
		if (dir == Dir.Left && (xy % width) > 0)
			return xy - 1;
		if (dir == Dir.Right && (xy + 1) % width != 0)
			return xy + 1;
		if (dir == Dir.Up && xy >= width)
			return xy - width;
		if (dir == Dir.Down && xy + width < length)
			return xy + width;
		return 0;
	}

	private static void detach(Cell a, Cell b) {
		for (int i = 0; i < a.moves.length; i++)
			if (a.moves[i].cell == b) {
				a.dir[a.moves[i].dir.ordinal()] = null;
				a.moves = Array.remove(a.moves, i);
				return;
			}
		throw new Error();
	}

	private static void attach(Cell a, Move m) {
		assert a.dir[m.dir.ordinal()] == null;
		a.dir[m.dir.ordinal()] = m;
		a.moves = Array.append(a.moves, m);
	}

	public char[] render(CellToChar ch) {
		char[] copy = buffer.clone();
		for (Cell c : cells)
			if (c != null)
				copy[c.xy] = ch.fn(c);
		return copy;
	}

	public char[] render(StateKey s) {
		return render(c -> {
			if (s.agent == c.id)
				return c.goal ? AgentGoal : Agent;
			if (s.box(c.id))
				return c.goal ? BoxGoal : Box;
			return c.goal ? Goal : Space;
		});
	}

	public void print(CellToChar ch) {
		System.out.print(render(ch));
	}

	public void print(StateKey s) {
		System.out.print(render(s));
	}

	public boolean is_solved(int[] box) {
		for (int i = 0; i < box.length; i++)
			if ((box[i] | goal[i]) != goal[i])
				return false;
		return true;
	}

	public boolean is_solved_fast(int[] box) {
		return Arrays.equals(box, goal);
	}

	static final AutoTimer timer_isvalidlevel = new AutoTimer("is_valid_level");

	public boolean is_valid_level(CellToChar op) {
		@Cleanup val t = timer_isvalidlevel.open();
		Level clone = new Level(render(op), null);
		if (Array.count(clone.cells, c -> c.box) != Array.count(clone.cells, c -> c.goal))
			return false;

		CellPairVisitor visitor = new CellPairVisitor(clone.cells.length, clone.cells.length, clone.cells);
		return has_goal_rooms ? clone.are_all_goals_reachable_quick(visitor)
				: clone.are_all_goals_reachable_full(visitor);
	}

	private boolean are_all_goals_reachable_quick(CellPairVisitor visitor) {
		// TODO assert original level is single_goal_room
		main:
		for (Cell g : cells) {
			if (!g.goal || g.box)
				continue;
			visitor.init();
			for (Move e : g.moves)
				visitor.add(e.cell, g);
			while (!visitor.done()) {
				final Cell a = visitor.first();
				final Cell b = visitor.second();
				for (Move e : a.moves) {
					if (e.cell == b)
						continue;
					visitor.try_add(e.cell, b);
					Move m = a.rmove(e.dir);
					if (m != null && m.cell == b && visitor.try_add(e.cell, a))
						if (a.box && !a.goal && !e.cell.goal)
							continue main;
				}
			}
			return false;
		}
		return true;
	}
}