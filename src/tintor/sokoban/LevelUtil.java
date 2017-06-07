package tintor.sokoban;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import lombok.experimental.UtilityClass;
import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.BitArray;
import tintor.common.Bits;
import tintor.common.For;
import tintor.sokoban.Cell.Dir;

@UtilityClass
class Deadlock3x3 {
	// $$.  $$.  $$.  $#.  #$.  (any of the boxes can be replaced with wall, but not all of them)
	// $.$  $.#  $.$  $.$  $.#
	// .$$  .#    ##  .#$  .#.
	static boolean is_3x3_deadlock(Cell agent, Cell pushed_box, int[] boxes) {
		if (true)
			return false;

		// pushed_box on the side
		for (Dir dir : Dir.values()) {
			Move m = pushed_box.move(dir);
			if (m == null || m.dist > 1 || m.cell.box(boxes) || m.cell == agent)
				continue;
			if (is_3x3_deadlock_center(m.cell, boxes))
				return true;
		}
		// pushed_box on the corner
		for (Dir dir : Dir.values()) {
			Move a = pushed_box.move(dir);
			if (a != null && a.dist == 1) {
				Move m = a.cell.move(dir.next);
				if (m == null || m.dist > 1 || m.cell.box(boxes) || m.cell == agent)
					continue;
				if (is_3x3_deadlock_center(m.cell, boxes))
					return true;
			}

			Move b = pushed_box.move(dir.next);
			if (b != null && b.dist == 1) {
				Move m = b.cell.move(dir.prev);
				if (m == null || m.dist > 1 || m.cell.box(boxes) || m.cell == agent)
					continue;
				if (is_3x3_deadlock_center(m.cell, boxes))
					return true;
			}
		}
		return false;
	}

	private static final char[] decode = { Code.Space, Code.Goal, Code.Box, Code.BoxGoal, Code.Wall };

	private static int encode(char c) {
		for (int i = 0; i < decode.length; i++)
			if (c == decode[i])
				return i;
		throw new Error();
	}

	private static final int[] pow5 = new int[8];
	private static final BitArray is_3x3_deadlock = new BitArray(781250/*= (5^8)*2 */);

	@SneakyThrows
	private static void loadIs3x3Deadlock() {
		DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream("is_3x3_deadlock.raw")));
		for (int i = 0; i < is_3x3_deadlock.bits().length; i++)
			is_3x3_deadlock.bits()[i] = dis.readInt();
		dis.close();
	}

	{
		pow5[0] = 1;
		for (int i = 1; i < pow5.length; i++)
			pow5[i] = pow5[i - 1] * 5;
		loadIs3x3Deadlock();
	}

	private static boolean is_3x3_deadlock_center(Cell center, int[] boxes) {
		if (true)
			return false;

		int p = 00000000;
		for (Dir dir : Dir.values()) {
			// TODO finish
			int q = pow5[1 + dir.ordinal() * 2];
			Move m = center.move(dir);
			if (m == null)
				p += encode(Code.Wall) * q;
			else if (m.dist == 1 && m.cell.box(boxes))
				p += encode(m.cell.goal ? Code.BoxGoal : Code.Goal) * q;
			else if (m.dist == 1 && !m.cell.box(boxes))
				p += encode(m.cell.goal ? Code.Goal : Code.Space) * q;

			Move n = center.move(dir.next);
			// TODO corners
		}

		return is_3x3_deadlock.get(p);
	}
}

@UtilityClass
public class LevelUtil {
	// number of free goals that agent can't move to without pushing any box
	public static int count_unreachable_goals(StateKey s, Level level) {
		int count = Array.count(level.cells, a -> a.goal && !s.box(a));
		CellVisitor visitor = level.visitor;
		for (Cell a : visitor.init(level.cells[s.agent])) {
			if (a.goal)
				count -= 1;
			for (Move e : a.moves)
				if (!s.box(e.cell))
					visitor.try_add(e.cell);
		}
		return count;
	}

	public static boolean[] find_agent_reachable_cells(StateKey s, Level level, boolean[] agent_reachable) {
		if (agent_reachable == null)
			agent_reachable = new boolean[level.cells.length];
		else
			Arrays.fill(agent_reachable, false);

		CellVisitor visitor = level.visitor;
		for (Cell v : visitor.init(level.cells[s.agent])) {
			agent_reachable[v.id] = true;
			for (Move m : v.moves)
				if (!s.box(m.cell))
					visitor.try_add(m.cell);
		}
		return agent_reachable;
	}

	public static boolean is_simple_deadlock(Cell agent, Cell pushed_box, int[] boxes) {
		return is_2x2_deadlock(pushed_box, boxes) || is_tunnel_deadlock(agent, pushed_box, boxes)
				|| is_2x3_deadlock(pushed_box, boxes) || Deadlock3x3.is_3x3_deadlock(agent, pushed_box, boxes);
	}

	// #$.
	// .$#
	public static boolean is_2x3_deadlock(Cell pushed_box, int[] boxes) {
		Cell a = pushed_box;
		for (Dir dir : Dir.values()) {
			Move m = a.move(dir);
			if (m == null || m.dist > 1 || !m.cell.box(boxes))
				continue;
			Cell b = m.cell;
			if (a.goal && b.goal)
				continue;
			// Both A and B are boxes, and one of them is not on goal
			if (a.move(dir.prev) == null && b.move(dir.next) == null)
				return true;
			if (a.move(dir.next) == null && b.move(dir.prev) == null)
				return true;
		}
		return false;
	}

	// TODO measure how effective it is
	// TODO many other tunnel cases: see 3x3 deadlock patterns
	// is tunnel with 2 boxes without agent or goals in between
	public static boolean is_tunnel_deadlock(Cell agent, Cell pushed_box, int[] boxes) {
		if (pushed_box.moves.length == 2) {
			// look both ways
			for (Move m : pushed_box.moves)
				if (is_tunnel_deadlock_helper(pushed_box, m, agent, boxes))
					return true;
		}
		if (pushed_box.moves.length == 3) {
			// look only down the non-middle Move of the T
			val middle = Array.find(Dir.values(), d -> pushed_box.dir[d.ordinal()] == null).reverse;
			for (Move m : pushed_box.moves)
				if (m.dir != middle)
					if (is_tunnel_deadlock_helper(pushed_box, m, agent, boxes))
						return true;
		}
		return false;
	}

	private static boolean is_tunnel_deadlock_helper(Cell pushed_box, Move m, Cell agent, int[] boxes) {
		int goals = pushed_box.goal ? 1 : 0;
		while (m.dist == 1 && m.cell.alive && m.cell != agent) {
			Cell a = m.cell;
			if (a.goal)
				if (++goals >= 2)
					return false;
			if (a.box(boxes))
				return goals < 2 && (a.move(m.dir.prev) == null || a.move(m.dir.next) == null);
			if (!a.straight())
				break;
			if (a.moves.length != 2)
				throw new Error();
			m = a.move(m.dir);
		}
		return false;
	}

	private static boolean free_or_tunnel(Move a, int[] boxes) {
		return a != null && (!a.cell.box(boxes) || a.dist > 1);
	}

	// $$  $#  $#
	// $$  $#  $$
	private static boolean is_2x2_deadlock(Cell box, int[] boxes) {
		for (Dir dir : Dir.values()) {
			Move a = box.move(dir);
			if (free_or_tunnel(a, boxes))
				continue;
			Move b = box.move(dir.next);
			if (free_or_tunnel(b, boxes))
				continue;
			if (a == null && b == null)
				return !box.goal;
			if (a != null) {
				Move c = a.cell.move(dir.next);
				if (!free_or_tunnel(c, boxes))
					return !(box.goal && a.cell.goal && (b == null || b.cell.goal) && (c == null || c.cell.goal));
			}
			if (b != null) {
				Move c = b.cell.move(dir);
				if (!free_or_tunnel(c, boxes))
					return !(box.goal && b.cell.goal && (a == null || a.cell.goal) && (c == null || c.cell.goal));
			}
		}
		return false;
	}

	public static boolean is_frozen_on_goal(Cell box, int[] boxes) {
		if (!box.goal)
			return false;
		for (Dir dir : Dir.values()) {
			Move a = box.move(dir);
			if (free_or_tunnel(a, boxes))
				continue;
			Move b = box.move(dir.next);
			if (free_or_tunnel(b, boxes))
				continue;
			if (a == null && b == null)
				return true;
			if (a != null) {
				Move c = a.cell.move(dir.next);
				if (!free_or_tunnel(c, boxes))
					return true;
			}
			if (b != null) {
				Move c = b.cell.move(dir);
				if (!free_or_tunnel(c, boxes))
					return true;
			}
		}
		return false;
	}

	public static boolean is_reversible_push(StateKey s, int s_dir, Level level) {
		Cell agent = level.cells[s.agent];
		Dir dir = Dir.values()[s_dir];
		Move b = agent.move(dir);
		assert s.box(b.cell);
		Move c = b.cell.move(dir);
		if (c == null || s.box(c.cell))
			return false;

		if (around(agent, 1, s, s_dir) != null || around(agent, 3, s, s_dir) != null || is_cell_reachable(c.cell, s)) {
			int[] box = s.box.clone();
			Bits.clear(box, b.cell.id);
			Bits.set(box, s.agent);
			State s2 = new State(b.cell.id, box, 0, 0, (s_dir + 2) % 4, 1, c.cell.id);
			Cell agent2 = level.cells[s2.agent];
			Dir dir2 = Dir.values()[s2.dir];

			Move b2 = agent2.move(dir2);
			assert b2.cell == agent;
			assert s2.box(b2.cell);
			Move c2 = b2.cell.move(dir2);
			if (c2 == null || s.box(c2.cell))
				return false;

			return around(agent2, 1, s2, s2.dir) != null || around(agent2, 3, s2, s2.dir) != null
					|| is_cell_reachable(c2.cell, s2);
		}
		return false;
	}

	private static Cell around(Cell z, int side, StateKey s, int s_dir) {
		Move m = z.move(Dir.values()[(s_dir + side) % 4]);
		if (m == null || s.box(m.cell))
			return null;
		m = m.cell.move(Dir.values()[s_dir]);
		if (m == null || s.box(m.cell))
			return null;
		m = m.cell.move(Dir.values()[s_dir]);
		if (m == null || s.box(m.cell))
			return null;
		return m.cell;
	}

	// can agent move to C without pushing any box?
	public static boolean is_cell_reachable(Cell c, StateKey s) {
		CellVisitor visitor = c.level.visitor;
		for (Cell a : visitor.init(c.level.cells[s.agent]))
			for (Move e : a.moves) {
				if (e.cell == c)
					return true;
				if (!s.box(e.cell))
					visitor.try_add(e.cell);
			}
		return false;
	}

	public static Cell minimal_reachable_cell(Cell agent, int[] boxes) {
		CellVisitor visitor = agent.level.visitor;
		Cell min = agent;
		for (Cell a : visitor.init(agent)) {
			if (min.id < a.id)
				min = a;
			for (Move m : a.moves)
				if (!Bits.test(boxes, a.id))
					visitor.try_add(m.cell);
		}
		return min;
	}

	static boolean is_unsolved_corral(byte[] corral, StateKey s, Level level) {
		for (Cell c : level.goals)
			if (corral[c.id] > 0 && !s.box(c))
				return true;
		for (int i = level.goals.length; i < level.alive.length; i++)
			if (corral[i] == 0)
				return true;
		return false;
	}

	// if all boxes on border are either frozen or can only be pushed inside the corral
	static boolean is_i_corral(byte[] corral, StateKey s, Level level) {
		for (Cell b : level.alive)
			if (corral[b.id] == 0)
				for (Move m : b.moves)
					if ((m.dist > 1 || !s.box(m.cell.id)) && corral[m.cell.id] < 0) {
						Move n = b.rmove(m.dir);
						if (n != null && n.dist == 1 && n.alive && !s.box(n.cell) && corral[n.cell.id] < 0)
							return false;
					}
		return true;
	}

	static boolean is_pi_corral(byte[] corral, StateKey s, Level level, boolean[] agent_reachable) {
		if (!is_unsolved_corral(corral, s, level) || !is_i_corral(corral, s, level))
			return false;

		for (Cell b : level.alive)
			if (corral[b.id] == 0)
				for (Move m : b.moves)
					if (corral[m.cell.id] > 0 && m.alive) {
						Move n = b.rmove(m.dir);
						if (n != null && corral[n.cell.id] < 0 && !agent_reachable[n.cell.id])
							return false;
					}
		return true;
	}

	static void print_corral(Level level, byte[] corral, StateKey s) {
		level.print(p -> {
			if (corral[p.id] > 0)
				return Code.Goal;
			if (corral[p.id] == 0)
				return is_frozen_on_goal(p, s.box) ? Code.FrozenOnGoal : (p.goal ? Code.BoxGoal : Code.Box);
			return p.alive ? Code.Space : Code.Dead;
		});
	}

	private static boolean have_common_border(Level level, byte[] a, byte[] b, boolean[] common) {
		boolean result = false;
		for (int i = 0; i < a.length; i++)
			if (common[i] = (a[i] == 0 && b[i] == 0
					&& For.all(level.cells[i].moves, m -> a[m.cell.id] >= 0 && b[m.cell.id] >= 0)))
				result = true;
		return result;
	}

	private static final AutoTimer timer_pi_corral = new AutoTimer("pi_corral");

	public static byte[] build_corral(Cell c, StateKey s) {
		CellVisitor visitor = c.level.visitor;
		byte[] corral = Array.ofByte(c.level.cells.length, (byte) -1);
		for (Cell a : visitor.add(c)) {
			corral[a.id] = (byte) 1;
			for (Move m : a.moves)
				if (s.box(m.cell))
					corral[m.cell.id] = 0;
				else
					visitor.try_add(m.cell);
		}
		for (Cell a : c.level.cells)
			if (corral[a.id] == 0 && For.all(a.moves, m -> corral[m.cell.id] >= 0))
				corral[a.id] = (byte) 1;

		for (Cell a : c.level.cells)
			if (corral[a.id] > 0)
				assert For.all(a.moves, m -> corral[m.cell.id] >= 0);
		assert Array.count(corral, e -> e == 0) > 0;
		return corral;
	}

	public static byte[] find_pi_corral(StateKey s, Level level, boolean[] agent_reachable, ArrayList<byte[]> corrals,
			boolean[] common) {
		@Cleanup val t = timer_pi_corral.open();

		// Find all corrals
		CellVisitor visitor = level.visitor;
		corrals.clear();
		visitor.init();
		for (Cell c : level.cells)
			if (!visitor.visited(c) && !s.box(c) && !agent_reachable[c.id])
				corrals.add(build_corral(c, s));

		// Merge all corrals that share a border
		// TODO can we make it better than O(N^2)?
		for (byte[] a : corrals)
			if (a != null)
				for (int bi = 0; bi < corrals.size(); bi++) {
					byte[] b = corrals.get(bi);
					if (b != null && b != a && have_common_border(level, a, b, common)) {
						for (int i = 0; i < a.length; i++) {
							if (b[i] > 0 || common[i])
								a[i] = (byte) 1;
							else if (b[i] == 0 && !common[i])
								a[i] = (byte) 0;
						}

						assert Array.count(a, e -> e == 0) > 0;
						corrals.set(bi, null);

						for (Cell i : level.cells)
							if (a[i.id] > 0)
								assert For.all(i.moves, m -> a[m.cell.id] >= 0);
					}
				}

		// TODO if there are multiple corrals, choose: first, one with the least pushes, one with the most pushes?
		for (byte[] c : corrals)
			if (c != null && is_pi_corral(c, s, level, agent_reachable))
				return c;
		return null;
	}
}