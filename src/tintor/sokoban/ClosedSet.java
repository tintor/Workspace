package tintor.sokoban;

import java.nio.ByteBuffer;

import org.junit.Assert;

import tintor.common.ExternalBTree;
import tintor.common.InlineChainingHashSet;
import tintor.common.Measurer;
import tintor.common.Timer;

// External set of all States for which we found optimal path from the start
class ClosedSet {
	static abstract class CompactStateBase extends InlineChainingHashSet.Element {
		byte agent;
		byte dir_and_is_push;
		short dist;

		static {
			// real size is 20, but gets aligned to 24
			Assert.assertEquals(24, Measurer.sizeOf(CompactStateBase.class));
		}
	}

	// 32 bytes
	final static class CompactState1 extends CompactStateBase {
		long box0;

		public boolean equals(Object o) {
			CompactState1 c = (CompactState1) o;
			return box0 == c.box0 && agent == c.agent;
		}

		protected int hashCode(Object context) {
			return State.hashCode((int) agent & 0xFF, box0, (Integer) context);
		}

		static {
			Assert.assertEquals(32, Measurer.sizeOf(CompactState1.class));
		}
	}

	final static class CompactState2 extends CompactStateBase {
		long box0;
		long box1;

		public boolean equals(Object o) {
			CompactState2 c = (CompactState2) o;
			return box0 == c.box0 && box1 == c.box1 && agent == c.agent;
		}

		protected int hashCode(Object context) {
			return State.hashCode((int) agent & 0xFF, box0, box1, (Integer) context);
		}

		static {
			Assert.assertEquals(40, Measurer.sizeOf(CompactState2.class));
		}
	}

	private final CompactState1 temp_compact1 = new CompactState1();
	private final CompactState2 temp_compact2 = new CompactState2();

	CompactStateBase compress(State s, boolean unique) {
		CompactStateBase c;
		assert box_length <= 128;
		if (box_length <= 64) {
			CompactState1 e = unique ? new CompactState1() : temp_compact1;
			e.box0 = s.box0;
			c = e;
		} else {
			CompactState2 e = unique ? new CompactState2() : temp_compact2;
			e.box0 = s.box0;
			e.box1 = s.box1;
			c = e;
		}
		c.agent = (byte) s.agent();
		assert !s.is_push || (0 <= s.dir && s.dir < 4);
		c.dir_and_is_push = (byte) (s.is_push ? s.dir + 4 : s.dir);
		c.dist = (short) s.dist();
		return c;
	}

	State decompress(CompactStateBase c) {
		if (c == null)
			return null;

		if (c instanceof CompactState1) {
			CompactState1 e = (CompactState1) c;
			int dir = c.dir_and_is_push >= 4 ? c.dir_and_is_push - 4 : c.dir_and_is_push;
			return new State((int) c.agent & 0xFF, e.box0, 0, c.dist, dir, c.dir_and_is_push >= 4);
		}

		CompactState2 e = (CompactState2) c;
		int dir = c.dir_and_is_push >= 4 ? c.dir_and_is_push - 4 : c.dir_and_is_push;
		return new State((int) c.agent & 0xFF, e.box0, e.box1, c.dist, dir, c.dir_and_is_push >= 4);
	}

	// set of CompactState2
	private final InlineChainingHashSet set;
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
		set = new InlineChainingHashSet(16, box_length);
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
			if (map.put(key(s), value(s))) {
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
			return map.contains(key(s));
		}
	}

	public State get(State s) {
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

			return new State(s.agent(), s.box0, s.box1, dist, dir, is_push);
		}
	}

	private byte[] value(State state) {
		ByteBuffer b = value_buffer;
		b.position(0);
		b.putShort((short) state.dist());
		b.put(state.dir);
		put(b, state.is_push);
		assert b.remaining() == 0;
		return b.array();
	}

	private byte[] key(State s) {
		ByteBuffer b = key_buffer;
		b.position(0);
		b.put((byte) s.agent());
		// TODO optimize (use less space)
		b.putLong(s.box0);
		b.putLong(s.box1);
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