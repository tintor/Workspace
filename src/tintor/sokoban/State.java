package tintor.sokoban;

import java.util.Arrays;

import tintor.common.Bits;
import tintor.common.Visitor;

class StateKey {
	final int agent;
	final int[] box;

	public StateKey(int agent, int[] box) {
		assert 0 <= agent : agent;
		this.agent = agent;
		assert box != null && box.length > 0;
		this.box = box;

		assert agent >= box.length * 32 || !Bits.test(box, agent) : agent + " " + box.length;
	}

	public final boolean box(int i) {
		assert i >= 0;
		return i < box.length * 32 && Bits.test(box, i);
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

		assert 0 <= prev_agent;
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
		assert prev_agent < level.cells;
		if (equals(level.start))
			return null;
		assert dist > level.low.dist;
		int a = level.rmove(agent, dir);
		assert 0 <= a && a < level.cells;
		assert 0 <= dir && dir < 4;
		int b = agent;
		assert 0 <= b && b < level.alive;
		assert !box(b);
		for (int i = 0; i < pushes - 1; i++) {
			b = level.rmove(b, dir);
			assert 0 <= b && b < level.alive;
			assert !box(b);
		}

		int c = level.move(agent, dir);
		assert 0 <= c && c < level.alive;
		assert box(c);

		int[] nbox = box.clone();
		Bits.clear(nbox, c);
		Bits.set(nbox, b);
		return new StateKey(prev_agent, nbox);
	}

	State push(int a, int dir, Level level, boolean optimal, int moves, int prev_agent) {
		assert symmetry == 0;
		assert 0 <= prev_agent && prev_agent < level.cells;
		assert !box(prev_agent);
		assert box(a);
		assert !box(level.rmove(a, dir));

		int b = level.move(a, dir);
		if (b == -1 || b >= level.alive || box(b))
			return null;

		int[] nbox = box.clone();
		Bits.clear(nbox, a);
		Bits.set(nbox, b);
		int pushes = 1;

		// keep pushing box until the end of tunnel
		while (pushes < 15 && can_force_push(a, b, level, optimal)) {
			// don't even attempt pushing box into a tunnel if it can't be pushed all the way through
			int c = level.move(b, dir);
			if (c == -1 || c >= level.alive || box(c))
				return null;
			a = b;
			b = c;
			assert dir == level.delta[a][b];
			Bits.clear(nbox, a);
			Bits.set(nbox, b);
			pushes += 1;
		}

		State s = new State(a, nbox, 0, dist + moves + pushes, dir, pushes, prev_agent);
		assert s.prev(level).equals(this);
		return s;
	}

	private boolean more_goals_than_boxes_in_room(int a, int door, Level level) {
		assert level.degree(door) == 2 && level.bottleneck[door];
		Visitor visitor = level.visitor;
		visitor.init(a);
		visitor.visited()[door] = true;
		int result = 0;
		while (!visitor.done()) {
			int b = visitor.next();
			if (level.goal(b))
				result += 1;
			if (box(b))
				result -= 1;
			for (int c : level.moves[b])
				if (!visitor.visited(c))
					visitor.add(c);
		}
		return result > 0;
	}

	private boolean can_force_push(int a, int b, Level level, boolean optimal) {
		int dir = level.delta[a][b];

		if (level.goal(b))
			return level.degree(b) == 2 && level.bottleneck[b] && !box(level.move(b, dir))
					&& more_goals_than_boxes_in_room(level.move(b, dir), b, level);

		// push through non-bottleneck tunnel
		if (level.degree(a) == 2 && level.degree(b) == 2)
			return true;

		// push through bottleneck tunnel (until agent can reach the other side)
		if (level.degree(a) == 2 && level.bottleneck[a] && level.bottleneck[b])
			return true;

		if (!optimal && level.bottleneck[b] && level.degree(b) == 3 && level.move(b, dir) != Level.Bad)
			return true;

		if (!optimal && level.bottleneck[b] && level.degree(b) == 2)
			return true;

		return false;
	}
}