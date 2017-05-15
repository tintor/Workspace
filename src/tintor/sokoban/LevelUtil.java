package tintor.sokoban;

import tintor.common.Bits;
import tintor.sokoban.Cell.Dir;

class LevelUtil {
	static boolean is_reversible_push(State s, Level level) {
		Cell agent = level.cells[s.agent];
		Dir dir = Dir.values()[s.dir];
		Move b = agent.move(dir);
		assert s.box(b.cell);
		Move c = b.cell.move(dir);
		if (c == null || s.box(c.cell))
			return false;

		if (around(agent, 1, s) != null || around(agent, 3, s) != null || is_cell_reachable(c.cell, s)) {
			int[] box = s.box.clone();
			Bits.clear(box, b.cell.id);
			Bits.set(box, s.agent);
			State s2 = new State(b.cell.id, box, 0, s.dist, (s.dir + 2) % 4, 1, c.cell.id);
			Cell agent2 = level.cells[s2.agent];
			Dir dir2 = Dir.values()[s2.dir];

			Move b2 = agent2.move(dir2);
			assert b2.cell == agent;
			assert s2.box(b2.cell);
			Move c2 = b2.cell.move(dir2);
			if (c2 == null || s.box(c2.cell))
				return false;

			return around(agent2, 1, s2) != null || around(agent2, 3, s2) != null || is_cell_reachable(c2.cell, s2);
		}
		return false;
	}

	static Cell around(Cell z, int side, State s) {
		Move m = z.move(Dir.values()[(s.dir + side) % 4]);
		if (m == null || s.box(m.cell))
			return null;
		m = m.cell.move(Dir.values()[s.dir]);
		if (m == null || s.box(m.cell))
			return null;
		m = m.cell.move(Dir.values()[s.dir]);
		if (m == null || s.box(m.cell))
			return null;
		return m.cell;
	}

	// can agent move to C without pushing any box?
	static boolean is_cell_reachable(Cell c, StateKey s) {
		CellVisitor visitor = c.level.visitor;
		for (Cell a : visitor.init(c.level.cells[s.agent]))
			for (Move e : a.moves) {
				if (visitor.visited(e.cell) || s.box(e.cell))
					continue;
				if (e.cell == c)
					return true;
				visitor.add(e.cell);
			}
		return false;
	}
}