package tintor.sokoban;

import java.util.Arrays;

import tintor.common.Array;
import tintor.common.Bits;
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

	StateKey prev(Level level) {
		assert symmetry == 0;
		assert prev_agent < level.cells.length;
		if (equals(level.start))
			return null;
		Cell b = level.cells[agent];
		assert b.alive;
		assert !box(b);
		for (int i = 0; i < pushes - 1; i++) {
			b = b.rmove(Dir.values()[dir]).cell;
			assert b.alive;
			assert !box(b);
		}

		Cell c = level.cells[agent].dir[dir].cell;
		assert c.alive;
		assert box(c);

		int[] nbox = Array.clone(box);
		Bits.clear(nbox, c.id);
		Bits.set(nbox, b.id);
		return new StateKey(prev_agent, nbox);
	}

	State push(Move m, Level level, boolean optimal, int moves, int prev_agent) {
		Cell a = m.cell;
		assert symmetry == 0;
		assert box(a.id);

		Dir dir = m.exit_dir;
		Move am = a.move(dir);
		if (am == null || !am.alive || box(am.cell))
			return null;
		assert am.dir == am.exit_dir;

		int[] nbox = Array.clone(box);
		Bits.clear(nbox, a.id);
		Bits.set(nbox, am.cell.id);
		int new_dist = dist + moves + m.dist - 1 + am.dist;
		int pushes = 1;

		// TODO increase push limit
		// keep pushing box until the end of tunnel
		while (pushes < 15 && can_force_push(a, am.cell, dir, optimal, level)) {
			// don't even attempt pushing box into a tunnel if it can't be pushed all the way through
			Move c = am.cell.move(dir);
			if (c == null || !c.alive || box(c.cell))
				return null;
			a = am.cell;
			am = c;
			Bits.clear(nbox, a.id);
			Bits.set(nbox, am.cell.id);
			new_dist += c.dist;
			pushes += 1;
		}

		State s = new State(a.id, nbox, 0, new_dist, dir.ordinal(), pushes, prev_agent);
		assert s.prev(level).equals(this);
		return s;
	}

	private boolean more_goals_than_boxes_in_room(Cell a, Cell door, Level level) {
		assert door.moves.length == 2 && door.bottleneck;
		int result = 0;
		for (Cell b : level.visitor.init(a).markVisited(door)) {
			if (b.goal)
				result += 1;
			if (box(b))
				result -= 1;
			for (Move c : b.moves)
				level.visitor.try_add(c.cell);
		}
		return result > 0;
	}

	private boolean can_force_push(Cell a, Cell b, Dir dir, boolean optimal, Level level) {
		if (b.goal) {
			// TODO if a box is pushed inside a tunnel with a box already inside (on goal) and a goal in between,
			// then keep pushing the box all the way through to that goal

			return b.moves.length == 2 && b.bottleneck && !box(b.move(dir).cell)
					&& more_goals_than_boxes_in_room(b.move(dir).cell, b, level);
		}

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