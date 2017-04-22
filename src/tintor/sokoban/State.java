package tintor.sokoban;

import org.junit.Assert;

import tintor.common.Bits;
import tintor.common.InlineChainingHashSet;
import tintor.common.Measurer;
import tintor.common.Visitor;
import tintor.common.Zobrist;

// State without boxes
abstract class StateBase extends InlineChainingHashSet.Element {
	StateBase(int agent, int dist, int dir, int pushes) {
		assert 0 <= agent && agent < 256 : agent;
		this.agent = (byte) agent;

		assert 0 <= dist && dist <= Short.MAX_VALUE : dist;
		this.dist = (short) dist;

		assert -1 <= dir && dir < 4;
		this.dir = (byte) dir;

		assert pushes == 0 || dir != -1;
		this.pushes = (byte) pushes;
	}

	public int agent() {
		return (int) agent & 0xFF;
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

	static {
		Assert.assertEquals(24, Measurer.sizeOf(StateBase.class));
	}

	static int hashCode(int agent, long box0, int alive) {
		int hash = Zobrist.hash(agent + alive);
		assert alive <= 64;
		for (int i = 0; i < alive; i++)
			if (Bits.test(box0, i))
				hash ^= Zobrist.hash(i);
		return hash;
	}

	static int hashCode(int agent, long box0, long box1, int alive) {
		int hash = Zobrist.hash(agent + alive);
		if (alive < 64) {
			for (int i = 0; i < alive; i++)
				if (Bits.test(box0, i))
					hash ^= Zobrist.hash(i);
		} else {
			assert alive <= 128;
			for (int i = 0; i < 64; i++)
				if (Bits.test(box0, i))
					hash ^= Zobrist.hash(i);
			for (int i = 64; i < alive; i++)
				if (Bits.test(box1, i - 64))
					hash ^= Zobrist.hash(i);
		}
		return hash;
	}

	// Identity (primary key)
	private final byte agent;

	// Properties of State
	final byte dir; // direction of move from previous state
	private final short dist;
	private final byte pushes; // number of box pushes from single call to State.move()

	// Transient fields for OpenSet
	private short total_dist; // = distance from start + heuristic to goal
	byte greedy;
}

final class State extends StateBase implements Comparable<State> {
	State(int agent, long box0, long box1, int dist, int dir, int pushes) {
		super(agent, dist, dir, pushes);
		assert agent >= 128 || !Bits.test(box0, box1, agent);
		this.box0 = box0;
		this.box1 = box1;
	}

	boolean box(int i) {
		assert i >= 0;
		if (i < 64)
			return Bits.test(box0, i);
		if (i < 128)
			return Bits.test(box1, i - 64);
		return false;
	}

	public int compareTo(State a) {
		if (greedy != a.greedy)
			return ((int) a.greedy & 0xFF) - ((int) greedy & 0xFF);
		return total_dist() - a.total_dist();
	}

	boolean equals(State s) {
		return box0 == s.box0 && agent() == s.agent() && box1 == s.box1;
	}

	@Override
	public boolean equals(Object o) {
		return equals((State) o);
	}

	public int hashCode(Object context) {
		return StateBase.hashCode(agent(), box0, box1, (Integer) context);
	}

	State prev(Level level) {
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
			return new State(a, box0, box1, dist, -1, 0);
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

		long nbox0 = box0;
		long nbox1 = box1;
		if (c < 64)
			nbox0 = Bits.clear(nbox0, c);
		else
			nbox1 = Bits.clear(nbox1, c - 64);
		if (b < 64)
			nbox0 = Bits.set(nbox0, b);
		else
			nbox1 = Bits.set(nbox1, b - 64);

		return new State(level.rmove(b, dir), nbox0, nbox1, dist() - pushes(), -1, 0);
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
			return new State(a, box0, box1, dist, dir, 0);
		}

		int b = level.move(a, dir);
		if (b == -1 || b >= level.alive)
			return null;
		if (!box(b)) {
			long nbox0 = box0;
			long nbox1 = box1;
			if (a < 64)
				nbox0 = Bits.clear(nbox0, a);
			else
				nbox1 = Bits.clear(nbox1, a - 64);
			if (b < 64)
				nbox0 = Bits.set(nbox0, b);
			else
				nbox1 = Bits.set(nbox1, b - 64);
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
				if (a < 64)
					nbox0 = Bits.clear(nbox0, a);
				else
					nbox1 = Bits.clear(nbox1, a - 64);
				if (b < 64)
					nbox0 = Bits.set(nbox0, b);
				else
					nbox1 = Bits.set(nbox1, b - 64);
				pushes += 1;
			}

			return new State(a, nbox0, nbox1, dist() + pushes, dir, pushes);
		}
		return null;
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

	static {
		Assert.assertEquals(40, Measurer.sizeOf(State.class));
	}

	final long box0;
	final long box1;
}