package tintor.sokoban;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.BipartiteMatching;
import tintor.common.Bits;
import tintor.common.PairVisitor;
import tintor.common.Util;
import tintor.common.Visitor;
import tintor.sokoban.Cell.Dir;

final class Move {
	final Cell cell;
	final Dir dir;
	final int dist;

	Move(Cell cell, Dir dir, int dist) {
		assert cell != null;
		assert dir != null;
		assert dist > 0;
		this.cell = cell;
		this.dir = dir;
		this.dist = dist;
	}
}

public final class CellLevel {
	final static char Box = '$';
	final static char Wall = '#';
	final static char BoxGoal = '*';
	final static char AgentGoal = '+';
	final static char Goal = '.';
	final static char Agent = '@';
	final static char Space = ' ';

	public final String name;
	final char[] buffer;
	public final Cell[] cells;
	public final int alive;
	public final int num_boxes;
	final Visitor visitor;
	public final State start;
	final int[] goal;
	final CellLevelTransforms transforms;
	final boolean has_goal_rooms;
	final boolean has_goal_zone;

	public static class MoreThan256CellsError extends Error {
		private static final long serialVersionUID = 1L;
	}

	public static ArrayList<CellLevel> loadAll(String filename) {
		int count = LevelLoader.numberOfLevels(filename);
		ArrayList<CellLevel> levels = new ArrayList<CellLevel>();
		for (int i = 1; i <= count; i++)
			try {
				String name = filename + ":" + i;
				levels.add(new CellLevel(LevelLoader.load(name), name));
			} catch (MoreThan256CellsError e) {
			}
		return levels;
	}

	public static CellLevel load(String filename) {
		return new CellLevel(LevelLoader.load(filename), filename);
	}

	CellLevel(char[] buffer, String name) {
		int w = 0;
		while (buffer[w] != '\n')
			w++;
		assert buffer.length % (w + 1) == 0;
		this.name = name;
		this.buffer = buffer;

		// Create cells
		Cell[] grid = new Cell[buffer.length];
		for (int y = w + 1; y < buffer.length - w - 1; y += w + 1)
			for (int xy = y + 1; xy < y + w - 1; xy++)
				if (buffer[xy] != Wall)
					grid[xy] = new Cell(this, xy, buffer[xy]);
		// Connect cells with neighbors
		Move[] m = new Move[4];
		for (Cell c : grid)
			if (c != null) {
				int p = 0;
				for (Dir dir : Dir.values()) {
					int xy = move(c.xy, dir, w + 1, buffer.length);
					if (grid[xy] != null) {
						c.dir[dir.ordinal()] = grid[xy];
						m[p++] = new Move(grid[xy], dir, 1);
					}
				}
				c.moves = Arrays.copyOf(m, p);
			}

		// Find agent
		if (Array.count(grid, c -> c != null && (buffer[c.xy] == Agent || buffer[c.xy] == AgentGoal)) != 1)
			throw new IllegalArgumentException("need exactly one agent");
		Cell agent = null;
		for (Cell c : grid)
			if (c != null && (buffer[c.xy] == Agent || buffer[c.xy] == AgentGoal))
				agent = c;

		// Try to move agent and remove reachable dead end cells
		int dist = 0;
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

		// Remove dead end cells
		for (Cell a : grid)
			while (a != null && a != agent && a.moves.length == 1 && !a.goal && !a.box) {
				Cell b = a.moves[0].cell;
				buffer[a.xy] = Wall;
				grid[a.xy] = null;
				detach(b, a);
				a = b;
			}

		// Remove non-reachable cells
		visitor = new Visitor(buffer.length);
		visitor.add(agent.xy);
		while (!visitor.done())
			for (Move e : grid[visitor.next()].moves)
				visitor.try_add(e.cell.xy);
		for (int i = 0; i < grid.length; i++)
			if (grid[i] != null && !visitor.visited(grid[i].xy))
				grid[i] = null;

		// Clean buffer chars that are far from reachable cells
		for (int i = 0; i < buffer.length; i++)
			if (!visitor.visited(i) && buffer[i] != '\n' & buffer[i] != Space)
				if (!is_close_to_visited(i, visitor.visited(), w + 1, buffer.length / (w + 1), false))
					buffer[i] = Space;
		// Add walls close to reachable cells to make level look nice
		if (name != null)
			for (int i = 0; i < buffer.length; i++)
				if (!visitor.visited(i) && buffer[i] != '\n' && buffer[i] != Wall)
					if (is_close_to_visited(i, visitor.visited(), w + 1, buffer.length / (w + 1), true))
						buffer[i] = Wall;

		// Assign compact cell id-s
		int count = Array.count(grid, c -> c != null);
		cells = new Cell[count];
		int id = 0;
		for (Cell c : grid)
			if (c != null)
				cells[c.id = id++] = c;
		if (count > 256)
			throw new MoreThan256CellsError(); // until I make compute_alive() faster

		// Init goal ordinals
		int i = 0;
		for (Cell c : cells)
			if (c.goal)
				c.goal_ordinal = i++;

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

		// Check boxes and goals
		num_boxes = Array.count(cells, c -> c.box);
		if (num_boxes == 0)
			throw new IllegalArgumentException("no boxes");
		if (num_boxes != Array.count(cells, c -> c.goal))
			throw new IllegalArgumentException("count(box) != count(goal)");

		// Make sure all goals are reachable by boxes
		PairVisitor pair_visitor = new PairVisitor(count, count);
		if (!are_all_goals_reachable_full(pair_visitor))
			throw new IllegalArgumentException("level contains unreachable goal");

		// Compute alive cells
		compute_alive(pair_visitor);

		// Remove useless alive cells (no goal dead-ends of alive cells)
		for (Cell a : cells)
			while (true) {
				Cell b = end_of_half_tunnel(a);
				if (b == null || a.box || a.goal)
					break;
				a.alive = false;
				a = b;
			}

		// Compress dead tunnels
		if (compress_tunnels)
			for (Cell a : cells)
				if (!a.alive && a.tunnel_entrance()) {
					Cell b = a.moves[0].cell.moves.length != 2 ? a.moves[1].cell : a.moves[0].cell;
					while (b.moves.length == 2 && b.moves[0].cell.moves.length == 2
							&& b.moves[1].cell.moves.length == 2) {
						Cell c = cells[b.moves[0].cell.id ^ b.moves[1].cell.id ^ b.id];
						// TODO delete b
						detach(a, b);
						//attach(a, c);
						detach(c, b);
						//attach(c, a);
						b = c;
					}
				}

		// Compress alive tunnels 
		if (compress_tunnels)
			for (Cell a : cells)
				if (a.alive && a.tunnel_entrance()) {
					Cell b = a.moves[0].cell.moves.length != 2 ? a.moves[1].cell : a.moves[0].cell;
					while (b.tunnel_interior()) {
						Cell c = cells[b.moves[0].cell.id ^ b.moves[1].cell.id ^ b.id];
						// TODO delete b
						detach(a, b);
						//attach(a, c);
						detach(c, b);
						//attach(c, a);
						b = c;
					}
				}

		// Put alive cells in front of dead cells
		int a = 0, b = cells.length - 1;
		while (true) {
			while (a < b && cells[a].alive)
				a += 1;
			while (a < b && !cells[b].alive)
				b -= 1;
			if (a >= b)
				break;
			Cell q = cells[a];
			cells[a] = cells[b];
			cells[b] = q;
			cells[a].id = a++;
			cells[b].id = b--;
		}
		alive = cells[a].alive ? a + 1 : a;
		assert Util.all(alive, e -> cells[e].alive);
		assert Util.all(alive, cells.length, e -> !cells[e].alive);

		// Compute distances to each goal
		compute_distances_to_each_goal(Array.count(cells, c -> c.goal), pair_visitor);

		// Compute box and goal bit sets
		int[] box = new int[(alive + 31) / 32];
		goal = new int[(alive + 31) / 32];
		for (Cell c : cells) {
			if (c.box)
				Bits.set(box, c.id);
			if (c.goal)
				Bits.set(goal, c.id);
		}
		start = new State(agent.id, box, 0, dist, 0, 0, 0);

		// Compute transforms
		transforms = new CellLevelTransforms(this, w + 1, buffer.length / (w + 1), grid);

		// Compute bottlenecks
		find_bottlenecks(agent, new int[cells.length], null, new int[1]);

		// Compute goal rooms
		has_goal_rooms = has_goal_rooms();
		has_goal_zone = has_goal_zone();
	}

	private static final boolean compress_tunnels = false;

	private boolean has_goal_zone() {
		for (Cell a : cells)
			if (a.goal)
				for (Move e : a.moves)
					if (e.cell.goal)
						return true;
		return false;
	}

	private boolean has_goal_rooms() {
		int room_count = 0;
		for (Cell a : cells) {
			if (a.bottleneck_tunnel() || a.room != -1)
				continue;
			visitor.init(a.id);
			while (!visitor.done()) {
				Cell b = cells[visitor.next()];
				b.room = room_count;
				for (Move e : b.moves)
					if (!e.cell.bottleneck_tunnel())
						visitor.try_add(e.cell.id);
			}
			room_count += 1;
		}

		if (Array.all(cells, c -> !c.bottleneck_tunnel()))
			return false;

		// count goals in each room
		int[] goals_in_room = new int[room_count];
		for (Cell a : cells)
			if (a.goal) {
				if (a.room == -1)
					return false;
				goals_in_room[a.room] += 1;
			}

		// check that all boxes are outside of goal rooms
		for (Cell a : cells)
			if (a.box && a.room != -1 && goals_in_room[a.room] > 0)
				return false;
		return true;
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

	void compute_distances_to_each_goal(int goals, PairVisitor pair_visitor) {
		for (Cell g : cells)
			g.distance_box = Array.ofInt(goals, Cell.Infinity);
		int[][] distance = Array.ofInt(cells.length, alive, Cell.Infinity);
		for (Cell g : cells)
			if (g.goal) {
				pair_visitor.init();
				for (Move e : g.moves) {
					pair_visitor.add(e.cell.id, g.id);
					distance[e.cell.id][g.id] = 0;
				}
				g.distance_box[g.goal_ordinal] = 0;

				while (!pair_visitor.done()) {
					final Cell agent = cells[pair_visitor.first()];
					final Cell box = cells[pair_visitor.second()];
					assert distance[agent.id][box.id] >= 0;
					assert distance[agent.id][box.id] != Cell.Infinity;
					box.distance_box[g.goal_ordinal] = Math.min(box.distance_box[g.goal_ordinal],
							distance[agent.id][box.id]);

					for (Move e : agent.moves) {
						// TODO moves included only if ! optimal
						Cell c = e.cell;
						if (c != box && pair_visitor.try_add(c.id, box.id))
							distance[c.id][box.id] = distance[agent.id][box.id] + e.dist;
						if (agent.alive && agent.rmove(e.dir) == box && pair_visitor.try_add(c.id, agent.id))
							distance[c.id][agent.id] = distance[agent.id][box.id] + e.dist;
					}
				}
			}
	}

	public boolean are_all_goals_reachable_full(PairVisitor visitor) {
		int[] num = new int[1];
		int[] box_ordinal = Array.ofInt(cells.length, p -> cells[p].box ? num[0]++ : -1);
		int num_boxes = num[0];
		if (num_boxes == 0)
			return true;

		boolean[][] can_reach = new boolean[num_boxes][num_boxes];
		main: for (Cell g : cells) {
			if (!g.goal)
				continue;
			int count = 0;
			if (g.box) {
				can_reach[box_ordinal[g.id]][g.goal_ordinal] = true;
				if (++count == num_boxes)
					continue main;
			}
			visitor.init();
			for (Move e : g.moves)
				visitor.add(e.cell.id, g.id);
			while (!visitor.done()) {
				final int a = visitor.first();
				final int b = visitor.second();
				for (Move e : cells[a].moves) {
					Cell c = e.cell;
					if (c.id == b)
						continue;
					visitor.try_add(c.id, b);
					Cell m = cells[a].rmove(e.dir);
					if (m != null && m.id == b && visitor.try_add(c.id, a))
						if (cells[a].box && !can_reach[box_ordinal[a]][g.goal_ordinal]) {
							can_reach[box_ordinal[a]][g.goal_ordinal] = true;
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
	private void compute_alive(PairVisitor visitor) {
		for (Cell b : cells) {
			if (b.goal) {
				b.alive = true;
				continue;
			}

			visitor.init();
			for (Move e : b.moves)
				visitor.add(e.cell.id, b.id);

			loop: while (!visitor.done()) {
				final Cell agent = cells[visitor.first()];
				final Cell box = cells[visitor.second()];

				for (Move e : agent.moves) {
					if (e.cell != box) {
						visitor.try_add(e.cell.id, box.id);
						continue;
					}
					final Cell q = e.cell.move(e.dir);
					if (q == null)
						continue;
					if (q.goal) {
						b.alive = true;
						break loop;
					}
					visitor.try_add(e.cell.id, q.id);
				}
			}
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
				a.moves[i] = a.moves[a.moves.length - 1];
				a.moves = Arrays.copyOf(a.moves, a.moves.length - 1);
			}
	}

	public char[] render(CellToChar ch) {
		for (Cell c : cells) {
			if (buffer[c.xy] == Wall || buffer[c.xy] == '\n')
				throw new Error();
			buffer[c.xy] = ch.fn(c);
		}
		return buffer;
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
		try (AutoTimer t = timer_isvalidlevel.open()) {
			CellLevel clone = new CellLevel(render(op), null);
			if (Array.count(clone.cells, c -> c.box) != Array.count(clone.cells, c -> c.goal))
				return false;

			PairVisitor visitor = new PairVisitor(clone.cells.length, clone.cells.length);
			return has_goal_rooms ? clone.are_all_goals_reachable_quick(visitor)
					: clone.are_all_goals_reachable_full(visitor);
		}
	}

	private boolean are_all_goals_reachable_quick(PairVisitor visitor) {
		// TODO assert original level is single_goal_room
		main: for (Cell g : cells) {
			if (!g.goal || g.box)
				continue;
			visitor.init();
			for (Move e : g.moves)
				visitor.add(e.cell.id, g.id);
			while (!visitor.done()) {
				final Cell a = cells[visitor.first()];
				final Cell b = cells[visitor.second()];
				for (Move e : a.moves) {
					if (e.cell == b)
						continue;
					visitor.try_add(e.cell.id, b.id);
					if (a.rmove(e.dir) == b && visitor.try_add(e.cell.id, a.id))
						if (a.box && !a.goal && !e.cell.goal)
							continue main;
				}
			}
			return false;
		}
		return true;
	}
}