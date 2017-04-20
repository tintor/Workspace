package tintor.sokoban;

import org.junit.Assert;

import tintor.common.Bits;
import tintor.common.InlineChainingHashSet;
import tintor.common.Measurer;
import tintor.common.Zobrist;

// TODO: test performance without "long box1"
abstract class StateBase extends InlineChainingHashSet.Element implements Comparable<StateBase> {
	StateBase(int agent, int dist, int dir, boolean is_push) {
		assert 0 <= agent && agent < 256;
		this.agent = (byte) agent;

		assert 0 <= dist && dist <= Short.MAX_VALUE;
		this.dist = (short) dist;

		assert -1 <= dir && dir < 4;
		this.dir = (byte) dir;

		assert dir != -1 || !is_push;
		this.is_push = is_push;
	}

	abstract StateBase prev(Level level);

	abstract StateBase move(int dir, Level level);

	public int compareTo(StateBase a) {
		if (a.greedy_score != greedy_score)
			return a.greedy_score - greedy_score;

		return total_dist - a.total_dist;
	}

	public int agent() {
		return (int) agent & 0xFF;
	}

	// Identity (primary key)
	protected final byte agent;

	// Properties of State
	final byte dir; // direction of move from previous state
	final short dist;
	final boolean is_push;

	// Transient fields for OpenSet
	short total_dist; // = distance from start + heuristic to goal
	byte greedy_score;
}

final class State extends StateBase {
	State(int agent, long box0, int dist, int dir, boolean is_push) {
		super(agent, dist, dir, is_push);
		this.box0 = box0;
	}

	boolean box(int i) {
		assert i >= 0;
		return i < 64 && Bits.test(box0, i);
	}

	boolean equals(State s) {
		return box0 == s.box0 && agent == s.agent;
	}

	@Override
	public boolean equals(Object o) {
		return equals((State) o);
	}

	@Override
	public int hashCode() {
		// TODO: pass level.alive to avoid iterating all
		int hash = Zobrist.hashBitset(box0, 0) ^ Zobrist.hash(agent() + 128);
		return hash;
	}

	StateBase prev(Level level) {
		int a = level.move(agent(), Level.reverseDir(dir));
		assert 0 <= a && a < level.cells;
		if (!is_push)
			return new State(a, box0, dist - 1, -1, false);

		assert 0 <= dir && dir < 4;
		int b = level.move(agent(), dir);
		assert 0 <= b && b < level.alive;

		long nbox0 = box0;
		nbox0 = Bits.clear(nbox0, b);
		nbox0 = Bits.set(nbox0, agent);
		return new State(a, nbox0, dist - 1, -1, false);
	}

	StateBase move(int dir, Level level) {
		assert 0 <= dir && dir < 4;
		int a = level.move(agent(), dir);
		if (a == -1)
			return null;
		if (!box(a))
			return new State(a, box0, dist + 1, dir, false);

		int b = level.move(a, dir);
		if (b == -1 || b >= level.alive)
			return null;
		if (!box(b)) {
			long nbox0 = box0;
			nbox0 = Bits.clear(nbox0, a);
			nbox0 = Bits.set(nbox0, b);
			return new State(a, nbox0, dist + 1, dir, true);
		}
		return null;
	}

	final long box0;

	static {
		Assert.assertEquals(32, Measurer.sizeOf(State.class));
	}
}

final class State2 extends StateBase {
	State2(int agent, long box0, long box1, int dist, int dir, boolean is_push) {
		super(agent, dist, dir, is_push);
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

	boolean equals(State2 s) {
		return box0 == s.box0 && agent == s.agent && box1 == s.box1;
	}

	@Override
	public boolean equals(Object o) {
		return equals((State2) o);
	}

	@Override
	public int hashCode() {
		// TODO: pass level.alive to avoid iterating all
		int hash = Zobrist.hashBitset(box0, 0) ^ Zobrist.hash(agent() + 128);
		hash ^= Zobrist.hashBitset(box1, 64);
		return hash;
	}

	StateBase prev(Level level) {
		int a = level.move(agent(), Level.reverseDir(dir));
		assert 0 <= a && a < level.cells;
		if (!is_push)
			return new State2(a, box0, box1, dist - 1, -1, false);

		assert 0 <= dir && dir < 4;
		int b = level.move(agent(), dir);
		assert 0 <= b && b < level.alive;

		long nbox0 = box0;
		long nbox1 = box1;
		if (b <= 64)
			nbox0 = Bits.clear(nbox0, b);
		else
			nbox1 = Bits.clear(nbox1, b - 64);
		if (agent <= 64)
			nbox0 = Bits.set(nbox0, agent);
		else
			nbox1 = Bits.set(nbox1, agent - 64);
		return new State2(a, nbox0, nbox1, dist - 1, -1, false);
	}

	StateBase move(int dir, Level level) {
		assert 0 <= dir && dir < 4;
		int a = level.move(agent(), dir);
		if (a == -1)
			return null;
		if (!box(a))
			return new State2(a, box0, box1, dist + 1, dir, false);

		int b = level.move(a, dir);
		if (b == -1 || b >= level.alive)
			return null;
		if (!box(b)) {
			long nbox0 = box0;
			long nbox1 = box1;
			if (a <= 64)
				nbox0 = Bits.clear(nbox0, a);
			else
				nbox1 = Bits.clear(nbox1, a - 64);
			if (b <= 64)
				nbox0 = Bits.set(nbox0, b);
			else
				nbox1 = Bits.set(nbox1, b - 64);
			return new State2(a, nbox0, nbox1, dist + 1, dir, true);
		}
		return null;
	}

	final long box0;
	final long box1;

	static {
		Assert.assertEquals(40, Measurer.sizeOf(State2.class));
	}
}