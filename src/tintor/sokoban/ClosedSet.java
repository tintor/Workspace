package tintor.sokoban;

import java.nio.ByteBuffer;

import tintor.common.ExternalBTree;
import tintor.common.InlineChainingHashSet;
import tintor.common.Timer;
import tintor.common.Util;
import tintor.common.Zobrist;

// External set of all States for which we found optimal path from the start
class ClosedSet {
	static class CompactStateBase extends InlineChainingHashSet.Element {
		short agent;
		byte dir;
		short dist;
		boolean is_push;
	}

	// 32 bytes
	final static class CompactState1 extends CompactStateBase {
		long box0;

		public boolean equals(Object o) {
			CompactState1 c = (CompactState1) o;
			return agent == c.agent && box0 == c.box0;
		}

		public int hashCode() {
			return Zobrist.hashBitset(box0, 0) ^ Zobrist.hash(agent + 64);
		}
	}

	final static class CompactState2 extends CompactStateBase {
		long box0;
		long box1;

		public boolean equals(Object o) {
			CompactState2 c = (CompactState2) o;
			return agent == c.agent && box0 == c.box0 && box1 == c.box1;
		}

		public int hashCode() {
			return Zobrist.hashBitset(box0, 0) ^ Zobrist.hashBitset(box1, 64) ^ Zobrist.hash(agent + 128);
		}
	}

	final static class CompactState3 extends CompactStateBase {
		long box0;
		long box1;
		long box2;

		public boolean equals(Object o) {
			CompactState3 c = (CompactState3) o;
			return agent == c.agent && box0 == c.box0 && box1 == c.box1 && box2 == c.box2;
		}

		public int hashCode() {
			return Zobrist.hashBitset(box0, 0) ^ Zobrist.hashBitset(box1, 64) ^ Zobrist.hashBitset(box2, 128)
					^ Zobrist.hash(agent + 196);
		}
	}

	private final CompactState1 temp_compact1 = new CompactState1();
	private final CompactState2 temp_compact2 = new CompactState2();
	private final CompactState3 temp_compact3 = new CompactState3();

	CompactStateBase compress(State s, boolean unique) {
		CompactStateBase c;
		if (s.box.length <= 64) {
			CompactState1 e = unique ? new CompactState1() : temp_compact1;
			e.box0 = Util.compress(s.box);
			c = e;
		} else if (s.box.length <= 128) {
			CompactState2 e = unique ? new CompactState2() : temp_compact2;
			e.box0 = Util.compress(s.box, 0);
			e.box1 = Util.compress(s.box, 1);
			c = e;
		} else if (s.box.length <= 196) {
			CompactState3 e = unique ? new CompactState3() : temp_compact3;
			e.box0 = Util.compress(s.box, 0);
			e.box1 = Util.compress(s.box, 1);
			e.box2 = Util.compress(s.box, 2);
			c = e;
		} else
			throw new Error();

		c.agent = (short) s.agent;
		c.dir = s.dir;
		c.dist = s.dist;
		c.is_push = s.is_push;
		return c;
	}

	State decompress(CompactStateBase c) {
		if (c == null)
			return null;
		State s = new State(c.agent, new boolean[box_length]);
		s.dir = c.dir;
		s.dist = c.dist;
		s.is_push = c.is_push;
		if (c.getClass() == CompactState1.class) {
			CompactState1 e = (CompactState1) c;
			Util.decompress(e.box0, 0, s.box);
		} else if (c.getClass() == CompactState2.class) {
			CompactState2 e = (CompactState2) c;
			Util.decompress(e.box0, 0, s.box);
			Util.decompress(e.box1, 1, s.box);
		} else if (c.getClass() == CompactState3.class) {
			CompactState3 e = (CompactState3) c;
			Util.decompress(e.box0, 0, s.box);
			Util.decompress(e.box1, 1, s.box);
			Util.decompress(e.box2, 2, s.box);
		} else
			throw new Error();
		return s;
	}

	// set of CompactStateBase
	private final InlineChainingHashSet set = new InlineChainingHashSet();
	private final ExternalBTree map;
	private final int key_size, value_size = 4, box_length;
	private final ByteBuffer key_buffer;
	private final ByteBuffer value_buffer;
	private int size = 0;
	private final boolean in_memory = true;
	final Timer timer_add = new Timer();
	final Timer timer_contains = new Timer();
	final Timer timer_get = new Timer();

	public ClosedSet(int box_length) {
		this.box_length = box_length;
		key_size = 2 + (box_length + 7) / 8;
		map = new ExternalBTree(512, key_size, value_size);
		key_buffer = ByteBuffer.allocate(key_size);
		value_buffer = ByteBuffer.allocate(value_size);
	}

	public double ratio() {
		return set.ratio();
	}

	public int size() {
		return size;
	}

	public boolean add(State s) {
		try (Timer t = timer_add.start()) {
			if (in_memory) {
				if (set.add(compress(s, true))) {
					size += 1;
					return true;
				}
				return false;
			}
			if (map.put(key(s.agent, s.box), value(s))) {
				size += 1;
				return true;
			}
			return false;
		}
	}

	public boolean contains(State s) {
		try (Timer t = timer_contains.start()) {
			if (in_memory)
				return set.contains(compress(s, false));
			return map.contains(key(s.agent, s.box));
		}
	}

	public State get(int agent, boolean[] box) {
		try (Timer t = timer_get.start()) {
			if (in_memory)
				return decompress((CompactStateBase) set.get(compress(new State(agent, box), false)));

			byte[] value = map.get(key(agent, box));
			if (value == null)
				return null;
			State state = new State(agent, box);
			ByteBuffer b = ByteBuffer.wrap(value, 0, value.length);
			state.dist = b.getShort();
			assert state.dist >= -1;
			state.dir = b.get();
			assert -1 <= state.dir && state.dir < 4 : state.dir;
			state.is_push = getBoolean(b);
			assert b.remaining() == 0;
			return state;
		}
	}

	private byte[] value(State state) {
		ByteBuffer b = value_buffer;
		b.position(0);
		b.putShort(state.dist);
		assert -1 <= state.dir && state.dir < 4 : state.dir;
		b.put(state.dir);
		put(b, state.is_push);
		assert b.remaining() == 0;
		return b.array();
	}

	private byte[] key(int agent, boolean[] box) {
		ByteBuffer b = key_buffer;
		b.position(0);
		b.putShort((short) agent);
		put(b, box);
		assert b.remaining() == 0;
		return b.array();
	}

	static void put(ByteBuffer b, boolean v) {
		b.put(v ? (byte) 12 : (byte) 37);
	}

	static boolean getBoolean(ByteBuffer b) {
		byte a = b.get();
		assert a == 37 || a == 12;
		return a == 12;
	}

	static void put(ByteBuffer b, boolean[] bitset) {
		for (int i = 0; i < bitset.length / 8; i++) {
			int o = i * 8;
			int c = 0;
			for (int j = 0; j < 8; j++)
				if (bitset[o + j])
					c |= 1 << j;
			b.put((byte) c);
		}
		if (bitset.length % 8 != 0) {
			int o = bitset.length / 8 * 8;
			int c = 0;
			for (int j = 0; j < bitset.length % 8; j++)
				if (bitset[o + j])
					c |= 1 << j;
			b.put((byte) c);
		}
	}
}