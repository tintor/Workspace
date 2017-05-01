package tintor.sokoban;

import java.util.Arrays;

import tintor.common.Bits;
import tintor.common.Visitor;

class StateKey {
	int agent;
	int[] boxes;
}

class State_ extends StateKey {
	int dir; // direction of move from previous state
	int dist;
	int pushes; // number of box pushes from single call to State.move()
	int total_dist; // = distance from start + heuristic to goal
	int prev_agent;
}

// State without boxes
class StateBase {
	StateBase(int agent, int dist, int dir, int pushes, int prev_agent) {
		assert 0 <= agent && agent < 256 : agent;
		this.agent = (byte) agent;

		assert 0 <= dist && dist < (8 * 1024) : dist;
		this.dist = (short) dist;

		assert -1 <= dir && dir < 4;
		this.dir = (byte) dir;

		assert pushes >= 0;
		this.pushes = (byte) pushes;

		assert 0 <= prev_agent && prev_agent < 256;
		this.prev_agent = (byte) prev_agent;
	}

	public int agent() {
		return (int) agent & 0xFF;
	}

	public int prev_agent() {
		return (int) prev_agent & 0xFF;
	}

	public int dist() {
		return (int) dist & 0xFFFF;
	}

	public int total_dist() {
		return (int) total_dist & 0xFFFF;
	}

	public boolean is_push() {
		return pushes != 0;
	}

	public int pushes() {
		return (int) pushes & 0xFF;
	}

	public void set_heuristic(int heuristic) {
		assert heuristic >= 0 && heuristic < 65536 - dist() : heuristic;
		this.total_dist = (short) (dist() + heuristic);
	}

	// Identity (primary key)
	private final byte agent;

	// Properties of State
	final byte dir; // direction of move from previous state
	private final short dist;
	private final byte pushes; // number of box pushes from single call to State.move()

	// Transient fields for OpenSet
	private short total_dist; // = distance from start + heuristic to goal

	private final byte prev_agent;
}

final class State extends StateBase {
	State(int agent, int[] box, int dist, int dir, int pushes, int prev_agent) {
		super(agent, dist, dir, pushes, prev_agent);
		assert agent >= box.length * 32 || !Bits.test(box, agent);
		this.box = box;
	}

	boolean box(int i) {
		assert i >= 0;
		return i < box.length * 32 && Bits.test(box, i);
	}

	boolean equals(State s) {
		return agent() == s.agent() && Arrays.equals(box, s.box);
	}

	@Override
	public boolean equals(Object o) {
		return equals((State) o);
	}

	State prev(Level level) {
		if (equals(level.start))
			return null;
		assert dist() > level.low.dist;
		int a = level.rmove(agent(), dir);
		assert 0 <= a && a < level.cells;
		if (pushes() == 0) {
			int dist = dist() - 1;
			int prev = agent();
			// forced move: keep moving until the end of tunnel
			while (level.moves[a].length == 2 && dist > level.low.dist) {
				assert level.moves[a][0] == prev || level.moves[a][1] == prev;
				int next = prev ^ level.moves[a][0] ^ level.moves[a][1];
				assert next != a;
				if (box(next))
					break;
				prev = a;
				a = next;
				dist -= 1;
			}
			assert a != agent();
			assert dist >= level.low.dist;
			return new State(a, box, dist, -1, 0, 0);
		}

		assert 0 <= dir && dir < 4;
		int b = agent();
		assert 0 <= b && b < level.alive;
		assert !box(b);
		for (int i = 0; i < pushes() - 1; i++) {
			b = level.rmove(b, dir);
			assert 0 <= b && b < level.alive;
			assert !box(b);
		}

		int c = level.move(agent(), dir);
		assert 0 <= c && c < level.alive;
		assert box(c);

		int[] nbox = box.clone();
		Bits.clear(nbox, c);
		Bits.set(nbox, b);
		return new State(level.rmove(b, dir), nbox, dist() - pushes(), -1, 0, 0);
	}

	State move(int dir, Level level, boolean optimal) {
		assert 0 <= dir && dir < 4;
		int a = level.move(agent(), dir);
		if (a == -1)
			return null;
		if (!box(a)) {
			int dist = dist() + 1;
			int prev = agent();
			// forced move: keep moving until the end of tunnel
			while (level.moves[a].length == 2) {
				assert level.moves[a][0] == prev || level.moves[a][1] == prev;
				int next = prev ^ level.moves[a][0] ^ level.moves[a][1];
				assert next != a;
				// TODO try to push the box OR return null
				if (box(next))
					break;
				dir = level.delta[a][next];
				prev = a;
				a = next;
				dist += 1;
			}
			return new State(a, box, dist, dir, 0, 0);
		}
		return push(a, dir, level, optimal);
	}

	State push(int a, int dir, Level level, boolean optimal) {
		int b = level.move(a, dir);
		if (b == -1 || b >= level.alive || box(b))
			return null;

		int[] nbox = box.clone();
		Bits.clear(nbox, a);
		Bits.set(nbox, b);
		int pushes = 1;

		// keep pushing box until the end of tunnel
		while (can_force_push(a, b, level, optimal)) {
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

		return new State(a, nbox, dist() + pushes, dir, pushes, 0);
	}

	private boolean more_goals_than_boxes_in_room(int a, int door, Level level) {
		assert level.degree(door) == 2 && level.bottleneck[door];
		Visitor visitor = new Visitor(level.cells);
		visitor.visited()[door] = true;
		visitor.add(a);
		int result = 0;
		for (int b : visitor) {
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

	final int[] box;
}