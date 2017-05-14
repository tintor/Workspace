package tintor.sokoban;

import java.util.Arrays;

import tintor.common.Bits;
import tintor.common.Visitor;
import tintor.sokoban.Cell.Dir;

class StateKey {
	final int agent;
	final int[] box;

	public StateKey(int agent, int[] box) {
		this.agent = agent;
		assert box != null && box.length > 0;
		this.box = box;

		assert agent >= box.length * 32 || !Bits.test(box, agent) : agent + " " + box.length;
	}

	public final boolean box(int i) {
		assert i >= 0;
		return i < box.length * 32 && Bits.test(box, i);
	}

	public final boolean box(Cell a) {
		return a.id < box.length * 32 && Bits.test(box, a.id);
	}

	public final boolean equals(StateKey s) {
		return agent == s.agent && Arrays.equals(box, s.box);
	}

	@Override
	public boolean equals(Object o) {
		return equals((StateKey) o);
	}
}

public final class State extends StateKey {
	final int symmetry;
	final int dir; // direction of move from previous state
	public final int dist;
	final int pushes; // number of box pushes from single call to State.move()
	int total_dist; // = distance from start + heuristic to goal
	final int prev_agent;

	State(int agent, int[] box, int symmetry, int dist, int dir, int pushes, int prev_agent) {
		super(agent, box);

		assert 0 <= symmetry && symmetry < 8;
		this.symmetry = symmetry;

		assert 0 <= dist;
		this.dist = dist;

		assert 0 <= dir && dir < 4;
		this.dir = dir;

		assert pushes >= 0;
		this.pushes = pushes;

		this.prev_agent = prev_agent;
	}

	public final boolean equals(State s) {
		return agent == s.agent && Arrays.equals(box, s.box) && symmetry == s.symmetry && dist == s.dist && dir == s.dir
				&& pushes == s.pushes && prev_agent == s.prev_agent;
	}

	@Override
	public boolean equals(Object o) {
		return equals((State) o);
	}

	public boolean is_initial() {
		return pushes == 0;
	}

	public static State deserialize(int agent, int[] box, long value) {
		final int mask = (1 << 13) - 1;
		int total_dist = (int) (value & mask);
		int dist = (int) ((value >>> 13) & mask);
		assert dist <= total_dist;
		int dir = (int) ((value >>> 26) & 3);
		int pushes = (int) (value >>> 28) & 0xF;
		int prev_agent = (int) ((value >>> 32) & 0xFF);
		int symmetry = (int) ((value >>> 40) & 0x7);
		assert prev_agent != 255 : String.format("%x", value);
		State q = new State(agent, box, symmetry, dist, dir, pushes, prev_agent);
		q.set_heuristic(total_dist - dist);
		return q;
	}

	private static boolean range(int a, int m) {
		return 0 <= a && a < m;
	}

	public long serialize() {
		long v = 1l << 63;
		assert range(total_dist, 1 << 13);
		assert 0 <= dist && dist <= total_dist : dist + " " + total_dist;
		assert range(dir, 4);
		assert range(pushes, 16);
		assert range(prev_agent, 256);
		assert range(symmetry, 8);
		v |= total_dist;
		v |= dist << 13;
		v |= dir << 26;
		v |= ((long) pushes) << 28;
		v |= ((long) prev_agent) << 32;
		v |= ((long) symmetry) << 40;
		return v;
	}

	public void set_heuristic(int heuristic) {
		assert heuristic >= 0 && heuristic < 65536 - dist : heuristic;
		this.total_dist = dist + heuristic;
	}

	StateKey prev(CellLevel level) {
		assert symmetry == 0;
		assert prev_agent < level.cells.length;
		if (equals(level.start))
			return null;
		Cell b = level.cells[agent];
		assert b.alive;
		assert !box(b);
		for (int i = 0; i < pushes - 1; i++) {
			b = b.rmove(Dir.values()[dir]);
			assert b.alive;
			assert !box(b);
		}

		Cell c = level.cells[agent].dir[dir];
		assert c.alive;
		assert box(c);

		int[] nbox = box.clone();
		Bits.clear(nbox, c.id);
		Bits.set(nbox, b.id);
		return new StateKey(prev_agent, nbox);
	}

	State push(Move m, CellLevel level, boolean optimal, int moves, int prev_agent) {
		Cell a = m.cell;
		Dir dir = m.dir;
		assert symmetry == 0;
		assert box(a.id);
		assert !box(a.rmove(dir).id);

		Cell b = a.move(dir);
		if (b == null || !b.alive || box(b))
			return null;

		int[] nbox = box.clone();
		Bits.clear(nbox, a.id);
		Bits.set(nbox, b.id);
		int pushes = m.dist;

		// keep pushing box until the end of tunnel
		while (pushes < 15 && can_force_push(a, b, dir, optimal, level)) {
			// don't even attempt pushing box into a tunnel if it can't be pushed all the way through
			Cell c = b.move(dir);
			if (c == null || !c.alive || box(c))
				return null;
			a = b;
			b = c;
			Bits.clear(nbox, a.id);
			Bits.set(nbox, b.id);
			pushes += b.dist(dir);
		}

		State s = new State(a.id, nbox, 0, dist + moves + pushes, dir.ordinal(), pushes, prev_agent);
		assert s.prev(level).equals(this);
		return s;
	}

	private boolean more_goals_than_boxes_in_room(Cell a, Cell door, CellLevel level) {
		assert door.moves.length == 2 && door.bottleneck;
		Visitor visitor = level.visitor;
		visitor.init(a.id);
		visitor.visited()[door.id] = true;
		int result = 0;
		while (!visitor.done()) {
			Cell b = level.cells[visitor.next()];
			if (b.goal)
				result += 1;
			if (box(b))
				result -= 1;
			for (Move c : b.moves)
				visitor.try_add(c.cell.id);
		}
		return result > 0;
	}

	private boolean can_force_push(Cell a, Cell b, Dir dir, boolean optimal, CellLevel level) {
		if (b.goal)
			return b.moves.length == 2 && b.bottleneck && !box(b.move(dir))
					&& more_goals_than_boxes_in_room(b.move(dir), b, level);

		// push through non-bottleneck tunnel
		if (a.moves.length == 2 && b.moves.length == 2)
			return true;

		// push through bottleneck tunnel (until agent can reach the other side)
		if (a.moves.length == 2 && a.bottleneck && b.bottleneck)
			return true;

		if (!optimal && b.bottleneck && b.moves.length == 3 && b.move(dir) != null)
			return true;

		if (!optimal && b.bottleneck && b.moves.length == 2)
			return true;

		return false;
	}
}