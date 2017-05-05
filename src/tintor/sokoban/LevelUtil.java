package tintor.sokoban;

import tintor.common.Bits;
import tintor.common.Visitor;

class LevelUtil {
	static boolean is_reversible_push(State s, Level level) {
		int b = level.move(s.agent, s.dir);
		assert s.box(b);
		int c = level.move(b, s.dir);
		if (c == -1 || s.box(c))
			return false;

		if (around(s.agent, 1, s, level) != -1 || around(s.agent, 3, s, level) != -1
				|| is_cell_reachable(c, s, level)) {
			int[] box = s.box.clone();
			Bits.clear(box, b);
			Bits.set(box, s.agent);
			State s2 = new State(b, box, s.dist(), (s.dir + 2) % 4, 1, c);

			int b2 = level.move(s2.agent, s2.dir);
			assert b2 == s.agent;
			assert s2.box(b2);
			int c2 = level.move(b2, s2.dir);
			if (c2 == -1 || s.box(c2))
				return false;

			return around(s2.agent, 1, s2, level) != -1 || around(s2.agent, 3, s2, level) != -1
					|| is_cell_reachable(c2, s2, level);
		}
		return false;
	}

	static int around(int z, int side, State s, Level level) {
		z = level.move(z, (s.dir + side) % 4);
		if (z == -1 || s.box(z))
			return -1;
		z = level.move(z, s.dir);
		if (z == -1 || s.box(z))
			return -1;
		z = level.move(z, s.dir);
		if (z == -1 || s.box(z))
			return -1;
		return z;
	}

	// can agent move to C without pushing any box?
	static boolean is_cell_reachable(int c, StateKey s, Level level) {
		Visitor visitor = level.visitor;
		level.visitor.init(s.agent);
		while (!visitor.done()) {
			int a = visitor.next();
			for (int b : level.moves[a]) {
				if (visitor.visited(b) || s.box(b))
					continue;
				if (b == c)
					return true;
				visitor.add(b);
			}
		}
		return false;
	}
}