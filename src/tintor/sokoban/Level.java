package tintor.sokoban;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.val;
import lombok.experimental.ExtensionMethod;
import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.BipartiteMatching;
import tintor.common.Bits;
import tintor.common.Flags;
import tintor.common.For;
import tintor.common.Util;
import tintor.sokoban.Cell.Dir;

// TODO move inside Cell class
final class Move {
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
}

@ExtensionMethod(Array.class)
public final class Level {
	public final String name;
	private final char[] buffer;
	// all three arrays are sorted in the same order: goals first, then non-goal alive, then dead cells
	public Cell[] cells;
	public Cell[] alive;
	public Cell[] goals;
	int[] goal_set;
	public final int num_boxes;
	public final CellVisitor visitor;
	public final State start;
	public final CellLevelTransforms transforms;
	public final Cell goal_section_entrance; // sections are rooms of alive cells only
	public final boolean has_goal_zone;

	private static final Flags.Bool enable_tunnels = new Flags.Bool("enable_tunnels", true);

	public static class MoreThan1024CellsError extends Error {
		private static final long serialVersionUID = 1L;
	}

	public static Iterable<Level> loadAll(String filename) {
		return Util.iterable(p -> {
			final int count = LevelLoader.numberOfLevels(filename);
			for (int i = 1; i <= count; i++)
				try {
					p.accept(load(filename + ":" + i));
				} catch (MoreThan1024CellsError e) {
				}
		});
	}

	private static final Flags.Int weaken = new Flags.Int("weaken", 0);

	public static Level load(String filename) {
		return new Level(LevelLoader.load(filename), filename, weaken.value);
	}

	private void weaken(Cell[] grid, int amount) {
		if (amount == 0)
			return;
		ArrayList<Cell> boxes = new ArrayList<>();
		ArrayList<Cell> goals = new ArrayList<>();
		for (Cell a : grid)
			if (a != null) {
				if (a.box)
					boxes.add(a);
				if (a.goal)
					goals.add(a);
			}
		ThreadLocalRandom rand = ThreadLocalRandom.current();
		for (int i = 0; i < amount; i++) {
			// remove one box
			int b = rand.nextInt(boxes.size());
			boxes.get(b).box = false;
			boxes.remove(b);
			// remove one goal
			int g = rand.nextInt(goals.size());
			goals.get(g).goal = false;
			goals.remove(g);
		}
	}

	Level(char[] buffer, String name, int weaken_amount) {
		int w = 0;
		while (buffer[w] != '\n')
			w++;
		assert buffer.length % (w + 1) == 0;
		this.name = name;
		this.buffer = buffer;

		Cell[] grid = create_cell_grid(w);
		weaken(grid, weaken_amount);
		connect_nearby_cells(grid, w);
		Cell agent = find_agent(grid);

		val p = move_agent_from_deadend(grid, agent);
		int dist = p.dist;
		agent = p.agent;

		remove_deadend_cells(grid, agent);
		visitor = new CellVisitor(buffer.length);
		remove_unreachable_cells(agent, grid);
		clean_buffer_chars(w);
		init_cells_goals_and_ids();

		if (name == null) {
			transforms = null;
			start = null;
			goal_section_entrance = null;
			has_goal_zone = false;
			num_boxes = 0;
			alive = null;
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
		if (!are_all_goals_reachable_full(pair_visitor, false))
			throw new IllegalArgumentException("level contains unreachable goal");

		compute_alive_cells(pair_visitor);
		remove_useless_alive_cells();

		// Set alive of all Moves
		for (Cell a : cells)
			for (Move am : a.moves)
				am.alive = a.alive && am.cell.alive;

		if (enable_tunnels.value) {
			compress_dead_tunnels(agent);
			compress_cell_ids();
		}
		pair_visitor.cells = cells;
		alive = Arrays.copyOf(cells, repartition(goals.length, c -> c.alive));
		compute_distances_to_each_goal(Array.count(cells, c -> c.goal), pair_visitor);
		start = compute_goal_and_start(agent, dist);
		transforms = new CellLevelTransforms(this, w + 1, buffer.length / (w + 1), grid);
		find_bottlenecks(agent, new int[cells.length], null, new int[1]);
		find_box_bottlenecks(cells[0], new int[alive.length], null, new int[1]);
		int room_count = compute_rooms();
		goal_section_entrance = goal_section_entrance(room_count);

		// At least three goals next to each other
		has_goal_zone = For.any(cells, a -> a.goal && Array.count(a.moves, e -> e.cell.goal) >= 2);
	}

	private Cell[] create_cell_grid(int w) {
		Cell[] grid = new Cell[buffer.length];
		for (int y = w + 1; y < buffer.length - w - 1; y += w + 1)
			for (int xy = y + 1; xy < y + w - 1; xy++)
				if (buffer[xy] != Code.Wall)
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
		if (Array.count(grid, c -> c != null && (buffer[c.xy] == Code.Agent || buffer[c.xy] == Code.AgentGoal)) != 1)
			throw new IllegalArgumentException("need exactly one agent");
		Cell agent = null;
		for (Cell c : grid)
			if (c != null && (buffer[c.xy] == Code.Agent || buffer[c.xy] == Code.AgentGoal))
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
		while (!agent.goal && agent.moves.length == 1) {
			Cell b = agent.moves[0].cell;
			if (b.box) {
				Move m = b.move(agent.moves[0].dir);
				if (m == null)
					break;
				Cell c = m.cell;
				if (c.box)
					break;
				b.box = false;
				c.box = true;
				buffer[c.xy] = c.goal ? Code.BoxGoal : Code.Box;
			}
			buffer[agent.xy] = Code.Wall;
			buffer[b.xy] = Code.Agent;
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
				buffer[a.xy] = Code.Wall;
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
			if (!visitor.visited(i) && buffer[i] != '\n' & buffer[i] != Code.Space)
				if (!is_close_to_visited(i, visitor.visited(), w + 1, buffer.length / (w + 1), false))
					buffer[i] = Code.Space;
	}

	private void init_cells_goals_and_ids() {
		int count = visitor.tail();
		// TODO move this after tunnel compression, as cell count will decrease
		if (count > 1024)
			throw new MoreThan1024CellsError(); // until I make compute_alive() faster
		cells = new Cell[count];
		Array.copy(visitor.queue(), 0, cells, 0, count);
		goals = Arrays.copyOf(cells, repartition(0, p -> p.goal));
		for (int i = 0; i < cells.length; i++)
			cells[i].id = i;
	}

	private void add_nice_walls(int w) {
		for (int i = 0; i < buffer.length; i++)
			if (!visitor.visited(i) && buffer[i] != '\n' && buffer[i] != Code.Wall)
				if (is_close_to_visited(i, visitor.visited(), w + 1, buffer.length / (w + 1), true))
					buffer[i] = Code.Wall;
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
			if (a.straight() || a.room > 0)
				continue;
			room_count += 1;
			for (Cell b : visitor.init(a)) {
				b.room = room_count;
				for (Move e : b.moves)
					if (!e.cell.straight() && e.dist == 1)
						visitor.try_add(e.cell);
			}
		}
		return room_count;
	}

	private void compress_dead_tunnels(Cell agent) {
		// TODO if agent is inside empty tunnel we can move agent to both sides to avoid breaking tunnel into two (will have 2 start states) 
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
						buffer[b.xy] = Code.DeadTunnel;
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

	private int repartition(int offset, Predicate<Cell> fn) {
		int p = offset, q = cells.length - 1;
		while (true) {
			while (p < q && fn.test(cells[p]))
				p += 1;
			while (p < q && !fn.test(cells[q]))
				q -= 1;
			if (p >= q)
				break;
			Cell e = cells[p];
			cells[p] = cells[q];
			cells[q] = e;
			cells[p].id = p++;
			cells[q].id = q--;
			assert fn.test(cells[p - 1]);
			assert !fn.test(cells[q + 1]);
		}
		final int count = fn.test(cells[p]) ? p + 1 : p;
		assert Util.all(count, e -> fn.test(cells[e]));
		assert Util.all(count, cells.length, e -> !fn.test(cells[e]));
		return count;
	}

	private Cell goal_section_entrance(int room_count) {
		Cell best = null;
		int best_size = Integer.MAX_VALUE;
		for (Cell b : alive)
			if (b.box_bottleneck) {
				int size = box_bottleneck_goal_zone_size(b);
				if (size < best_size) {
					best = b;
					best_size = size;
				}
			}
		return best_size < alive.length * 4 / 5 ? best : null;
	}

	public int state_space() {
		// Discount cells in alive tunnels (count them as 1 cell) and bottleneck tunnels (count them as 0 cells)
		int discount = 0;
		for (Cell a : alive)
			if (!a.straight())
				for (Move am : a.moves)
					if ((am.dir == Dir.Right || am.dir == Dir.Down) && am.cell.straight() && am.dist == 1) {
						// found a tunnel entrance
						int len = 1;
						Cell b = am.cell;
						while (true) {
							Move m = b.move(am.dir);
							if (m == null || m.dist != 1)
								break;
							Cell c = m.cell;
							if (c == null || !c.alive || c.goal || !c.straight())
								break;
							b = c;
							len += 1;
						}
						discount += b.bottleneck ? len : len - 1;
					}
		BigInteger agent_positions = BigInteger.valueOf(cells.length - discount - num_boxes);
		return Util.combinations(alive.length - discount, num_boxes).multiply(agent_positions).bitLength();
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

	private static int find_box_bottlenecks(Cell a, int[] discovery, Cell parent, int[] time) {
		assert a.alive;
		int children = 0;
		int low_a = discovery[a.id] = ++time[0];
		for (Move e : a.moves)
			if (e.cell.alive && e.dist == 1)
				if (discovery[e.cell.id] == 0) {
					children += 1;
					int low_b = find_box_bottlenecks(e.cell, discovery, a, time);
					low_a = Math.min(low_a, low_b);
					if (parent != null && low_b >= discovery[a.id])
						a.box_bottleneck = true;
				} else if (e.cell != parent)
					low_a = Math.min(low_a, discovery[e.cell.id]);
		if (parent == null && children > 1)
			a.box_bottleneck = true;
		return low_a;
	}

	private void compute_distances_to_each_goal(int goals, CellPairVisitor pair_visitor) {
		For.each(cells, g -> g.distance_box = Array.ofInt(goals, Cell.Infinity));
		int[][] distance = new int[cells.length][alive.length];
		for (Cell g : cells)
			if (g.goal) {
				pair_visitor.init();
				Array.fill(distance, Cell.Infinity);
				for (Move e : g.moves)
					if (pair_visitor.try_add(e.cell, g))
						distance[e.cell.id][g.id] = e.dist - 1;
				g.distance_box[g.id] = 0;

				while (!pair_visitor.done()) {
					final Cell agent = pair_visitor.first();
					final Cell box = pair_visitor.second();
					assert distance[agent.id][box.id] != Cell.Infinity;
					box.distance_box[g.id] = Math.min(box.distance_box[g.id], distance[agent.id][box.id]);

					for (Move e : agent.moves) {
						// TODO moves included only if ! optimal
						Cell c = e.cell;
						if (c != box && pair_visitor.try_add(c, box))
							distance[c.id][box.id] = distance[agent.id][box.id];//+ e.dist;
						Move m = agent.rmove(e.dir);
						if (agent.alive && m != null && m.cell == box && pair_visitor.try_add(c, agent))
							distance[c.id][agent.id] = distance[agent.id][box.id] + e.dist;
					}
				}
				/*print(p -> {
					if (p == g)
						return Code.GoalRoomEntrance;
					if (!p.alive)
						return Code.Dead;
					if (distance[cells[cells.length - 1].id][p.id] >= Cell.Infinity)
						return Code.Space;
					return (char) ((int) '0' + (distance[cells[cells.length - 1].id][p.id] % 10));
				});*/
			}
		for (Cell b : alive)
			b.distance_box_min = Array.min(b.distance_box);
	}

	private State compute_goal_and_start(Cell agent, int dist) {
		int[] box_set = new int[(alive.length + 31) / 32];
		goal_set = new int[(alive.length + 31) / 32];
		for (Cell c : cells) {
			if (c.box)
				Bits.set(box_set, c.id);
			if (c.goal)
				Bits.set(goal_set, c.id);
		}
		return new State(agent.id, box_set, 0, dist, 0, 0, 0);
	}

	public boolean are_all_goals_reachable_full(CellPairVisitor visitor, boolean is_valid_level) {
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
			int count = 0;
			if (g.box) {
				can_reach[box_ordinal[g.id]][g.id] = true;
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
						if (a.box && !can_reach[box_ordinal[a.id]][g.id]) {
							can_reach[box_ordinal[a.id]][g.id] = true;
							if (++count == num_boxes)
								continue main;
						}
				}
			}
			if (count == 0)
				return false;
		}
		if (is_valid_level) {
			@Cleanup val t = timer_max_bpm.open();
			return BipartiteMatching.maxBPM(can_reach) == num_boxes;
		} else {
			return BipartiteMatching.maxBPM(can_reach) == num_boxes;
		}
	}

	static final AutoTimer timer_max_bpm = new AutoTimer("max_bpm");

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
				return c.goal ? Code.AgentGoal : Code.Agent;
			if (s.box(c.id)) {
				if (!c.goal)
					return Code.Box;
				return LevelUtil.is_frozen_on_goal(c, s.box) ? Code.FrozenOnGoal : Code.BoxGoal;
			}
			if (!c.alive)
				return Code.Dead;
			if (c == goal_section_entrance)
				return Code.GoalRoomEntrance;
			return c.goal ? Code.Goal : Code.Space;
		});
	}

	public void print(CellToChar ch) {
		System.out.print(Code.emojify(render(ch)));
	}

	public void print(StateKey s) {
		System.out.print(Code.emojify(render(s)));
	}

	public boolean is_solved(int[] box_set) {
		for (int i = 0; i < box_set.length; i++)
			if ((box_set[i] | goal_set[i]) != goal_set[i])
				return false;
		return true;
	}

	public boolean is_solved_fast(int[] box_set) {
		assert num_boxes == Bits.count(box_set);
		return Arrays.equals(box_set, goal_set);
	}

	static final AutoTimer timer_isvalidlevel = new AutoTimer("is_valid_level");

	static final Flags.Bool all_goals_reachable_full = new Flags.Bool("all_goals_reachable_full", false);

	public boolean is_valid_level(char[] buffer, boolean allow_more_goals_than_boxes) {
		@Cleanup val t = timer_isvalidlevel.open();
		Level clone = new Level(buffer, null, 0);

		if (allow_more_goals_than_boxes) {
			if (Array.count(clone.cells, c -> c.box) > Array.count(clone.cells, c -> c.goal))
				return false;

			CellPairVisitor visitor = new CellPairVisitor(clone.cells.length, clone.cells.length, clone.cells);
			return clone.are_all_goals_reachable_quick(visitor, goal_section_entrance);
		} else {
			if (Array.count(clone.cells, c -> c.box) != Array.count(clone.cells, c -> c.goal))
				return false;

			CellPairVisitor visitor = new CellPairVisitor(clone.cells.length, clone.cells.length, clone.cells);
			return all_goals_reachable_full.value ? clone.are_all_goals_reachable_full(visitor, true)
					: clone.are_all_goals_reachable_quick(visitor, goal_section_entrance);
		}
	}

	private boolean are_all_goals_reachable_quick(CellPairVisitor visitor, Cell entrance) {
		main:
		for (Cell g : cells) {
			if (!g.goal || g.box)
				continue;
			visitor.init();
			for (Move e : g.moves) {
				visitor.add(e.cell, g);
				if (e.cell == entrance)
					continue main;
			}
			while (!visitor.done()) {
				final Cell a = visitor.first();
				final Cell b = visitor.second();
				if (a == entrance)
					continue;
				for (Move e : a.moves) {
					if (e.cell == b)
						continue;
					visitor.try_add(e.cell, b);
					Move m = a.rmove(e.dir);
					if (m != null && m.cell == b && visitor.try_add(e.cell, a))
						if (!a.goal && !e.cell.goal)
							continue main;
				}
			}
			return false;
		}
		return true;
	}

	// box_bottleneck splits alive cells into two sets
	// if all goals are in one set, return number of alive cells in that set,
	// otherwise return Integer.MAX_VALUE
	private int box_bottleneck_goal_zone_size(Cell b) {
		assert b.box_bottleneck;
		int result = Integer.MAX_VALUE;
		for (Move bm : b.moves)
			if (bm.cell.alive && bm.dist == 1) {
				int num_alive = 0;
				int num_goals = 0;
				for (Cell a : visitor.init(bm.cell).markVisited(b)) {
					num_alive += 1;
					if (a.goal)
						num_goals += 1;
					for (Move m : a.moves)
						if (m.dist == 1 && m.cell.alive)
							visitor.try_add(m.cell);
				}
				if (num_goals == (b.goal ? num_boxes - 1 : num_boxes))
					result = Math.min(result, num_alive);
			}
		return result;
	}
}