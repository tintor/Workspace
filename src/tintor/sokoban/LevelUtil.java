package tintor.sokoban;

import tintor.common.Bits;
import tintor.common.Visitor;
import tintor.sokoban.Cell.Dir;

class LevelUtil {
	static boolean is_reversible_push(State s, Level level) {
		Cell agent = level.cells[s.agent];
		Dir dir = Dir.values()[s.dir];
		Cell b = agent.move(dir);
		assert s.box(b);
		Cell c = b.move(dir);
		if (c == null || s.box(c))
			return false;

		if (around(agent, 1, s) != null || around(agent, 3, s) != null || is_cell_reachable(c, s)) {
			int[] box = s.box.clone();
			Bits.clear(box, b.id);
			Bits.set(box, s.agent);
			State s2 = new State(b.id, box, 0, s.dist, (s.dir + 2) % 4, 1, c.id);
			Cell agent2 = level.cells[s2.agent];
			Dir dir2 = Dir.values()[s2.dir];

			Cell b2 = agent2.move(dir2);
			assert b2 == agent;
			assert s2.box(b2);
			Cell c2 = b2.move(dir2);
			if (c2 == null || s.box(c2))
				return false;

			return around(agent2, 1, s2) != null || around(agent2, 3, s2) != null || is_cell_reachable(c2, s2);
		}
		return false;
	}

	static Cell around(Cell z, int side, State s) {
		z = z.move(Dir.values()[(s.dir + side) % 4]);
		if (z == null || s.box(z))
			return null;
		z = z.move(Dir.values()[s.dir]);
		if (z == null || s.box(z))
			return null;
		z = z.move(Dir.values()[s.dir]);
		if (z == null || s.box(z))
			return null;
		return z;
	}

	// can agent move to C without pushing any box?
	static boolean is_cell_reachable(Cell c, StateKey s) {
		Visitor visitor = c.level.visitor;
		c.level.visitor.init(s.agent);
		while (!visitor.done()) {
			Cell a = c.level.cells[visitor.next()];
			for (Move e : a.moves) {
				if (visitor.visited(e.cell.id) || s.box(e.cell))
					continue;
				if (e.cell == c)
					return true;
				visitor.add(e.cell.id);
			}
		}
		return false;
	}
}