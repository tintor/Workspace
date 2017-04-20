package tintor.sokoban;

import java.nio.ByteBuffer;

import tintor.common.ExternalBTree;
import tintor.common.InlineChainingHashSet;
import tintor.common.Timer;
import tintor.common.Zobrist;

// External set of all States for which we found optimal path from the start
class ClosedSet {
	static class CompactStateBase extends InlineChainingHashSet.Element {
		byte agent;
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
			return Zobrist.hashBitset(box0, 0) ^ Zobrist.hash(((int) agent & 0xFF) + 64);
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
			return Zobrist.hashBitset(box0, 0) ^ Zobrist.hashBitset(box1, 64)
					^ Zobrist.hash(((int) agent & 0xFF) + 128);
		}
	}

	private final CompactState1 temp_compact1 = new CompactState1();
	private final CompactState2 temp_compact2 = new CompactState2();

	CompactStateBase compress(StateBase s, boolean unique) {
		CompactStateBase c;
		if (box_length <= 64) {
			CompactState1 e = unique ? new CompactState1() : temp_compact1;
			State f = (State) s;
			e.box0 = f.box0;
			c = e;
		} else if (box_length <= 128) {
			CompactState2 e = unique ? new CompactState2() : temp_compact2;
			State2 f = (State2) s;
			e.box0 = f.box0;
			e.box0 = f.box1;
			c = e;
		} else
			throw new Error();

		c.agent = (byte) s.agent();
		c.dir = s.dir;
		c.dist = s.dist;
		c.is_push = s.is_push;
		return c;
	}

	StateBase decompress(CompactStateBase c) {
		if (c == null)
			return null;
		long box0 = 0;
		long box1 = 0;
		if (c.getClass() == CompactState1.class) {
			CompactState1 e = (CompactState1) c;
			box0 = e.box0;
			return new State((int) c.agent & 0xFF, box0, c.dist, c.dir, c.is_push);
		} else if (c.getClass() == CompactState2.class) {
			CompactState2 e = (CompactState2) c;
			box0 = e.box0;
			box1 = e.box1;
			return new State2((int) c.agent & 0xFF, box0, box1, c.dist, c.dir, c.is_push);
		} else
			throw new Error();
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

	public boolean add(StateBase s) {
		try (Timer t = timer_add.start()) {
			if (in_memory) {
				if (set.add(compress(s, true))) {
					size += 1;
					return true;
				}
				return false;
			}
			if (map.put(key(s), value(s))) {
				size += 1;
				return true;
			}
			return false;
		}
	}

	public boolean contains(StateBase s) {
		try (Timer t = timer_contains.start()) {
			if (in_memory)
				return set.contains(compress(s, false));
			return map.contains(key(s));
		}
	}

	public StateBase get(StateBase s) {
		try (Timer t = timer_get.start()) {
			if (in_memory)
				return decompress((CompactStateBase) set.get(compress(s, false)));

			byte[] value = map.get(key(s));
			if (value == null)
				return null;

			ByteBuffer b = ByteBuffer.wrap(value, 0, value.length);
			short dist = b.getShort();
			byte dir = b.get();
			boolean is_push = getBoolean(b);
			assert b.remaining() == 0;

			if (s instanceof State) {
				State e = (State) s;
				return new State(s.agent(), e.box0, dist, dir, is_push);
			} else {
				State2 e = (State2) s;
				return new State2(s.agent(), e.box0, e.box1, dist, dir, is_push);
			}
		}
	}

	private byte[] value(StateBase state) {
		ByteBuffer b = value_buffer;
		b.position(0);
		b.putShort(state.dist);
		b.put(state.dir);
		put(b, state.is_push);
		assert b.remaining() == 0;
		return b.array();
	}

	private byte[] key(StateBase s) {
		ByteBuffer b = key_buffer;
		b.position(0);
		b.put((byte) s.agent());
		if (s instanceof State) {
			State e = (State) s;
			// TODO optimize (use less space)
			b.putLong(e.box0);
		} else {
			State2 e = (State2) s;
			// TODO optimize (use less space)
			b.putLong(e.box0);
			b.putLong(e.box1);
		}
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