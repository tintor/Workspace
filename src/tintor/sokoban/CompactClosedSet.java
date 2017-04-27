package tintor.sokoban;

import java.nio.ByteBuffer;

import tintor.common.CompactHashMap;
import tintor.common.Timer;

final class CompactClosedSet {
	private final CompactHashMap map;
	private final int key_size, value_size = 4, box_length;
	private final ByteBuffer buffer;
	private final ByteBuffer keyBuffer;
	private final ByteBuffer valueBuffer;
	private int size = 0;

	final Timer timer_add = new Timer();
	final Timer timer_contains = new Timer();
	final Timer timer_get = new Timer();

	CompactClosedSet(int box_length) {
		key_size = 1 + (box_length + 7) / 8;
		map = new CompactHashMap(key_size, value_size, 10, 6, new byte[key_size]);
		this.box_length = box_length;
		buffer = ByteBuffer.allocate(key_size + value_size);
		keyBuffer = ByteBuffer.allocate(key_size);
		valueBuffer = ByteBuffer.allocate(value_size);
	}

	double ratio() {
		return (double) map.size() / map.capacity();
	}

	int size() {
		return size;
	}

	boolean add(State s) {
		try (Timer t = timer_add.start()) {
			return !map.set(serialize(s, true));
		}
	}

	boolean contains(State s) {
		try (Timer t = timer_contains.start()) {
			return map.contains(serialize(s, false));
		}
	}

	State get(State s) {
		try (Timer t = timer_get.start()) {
			if (!map.get(serialize(s, false), valueBuffer.array()))
				return null;

			ByteBuffer b = valueBuffer;
			b.position(0);
			int dist = ((int) b.getShort()) & 0xFFFF;
			byte dir = b.get();
			int pushes = ((int) b.get()) & 0xFF;
			assert b.remaining() == 0;
			return new State(s.agent(), s.box0, s.box1, dist, dir, pushes);
		}
	}

	private byte[] serialize(State s, boolean value) {
		ByteBuffer b = value ? buffer : keyBuffer;
		b.position(0);
		b.put((byte) s.agent());
		int box_bytes = (box_length + 7) / 8;
		putLong(b, s.box0, box_bytes);
		putLong(b, s.box1, box_bytes - 8);
		assert b.remaining() == (value ? value_size : 0);
		if (value) {
			b.putShort((short) s.dist());
			b.put(s.dir);
			b.put((byte) s.pushes());
			assert b.remaining() == 0;
		}
		return b.array();
	}

	private static void putLong(ByteBuffer b, long a, int len) {
		for (int i = 0; i < Math.max(8, len); i++)
			b.put((byte) (a >>> (i * 8)));
	}
}