package tintor.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import lombok.SneakyThrows;

// TODO perf: replace root Node with hash bucket array
// TODO perf: use binary search for large nodes
// TODO perf: use sun.misc.Unsafe for key comparison
// TODO perf: use int[] arrays for keys instead of byte[] for keys

// internal node byte format:
// keys: 2
// key[0]: K
// key[n-1]: K
// ...
// pointer[0]: 4
// pointer[n]: 4

// leaf node byte format:
// keys: 2
// key[0]: K
// value[0]: V
// ...
// key[n-1]: K
// value[n-1]: V

final class BufferStorage {
	private final FileChannel fc;
	private final int buffer_size;
	private final Map<Integer, ByteBuffer> clean = new WeakHashMap<Integer, ByteBuffer>();

	@SneakyThrows
	BufferStorage(int buffer_size) {
		this.buffer_size = buffer_size;
		fc = FileChannel.open(Files.createTempFile("BTree", null), StandardOpenOption.CREATE,
				StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
				StandardOpenOption.READ);
	}

	public int cleanCache() {
		int c = clean.size();
		//clean.clear();
		return c;
	}

	private void write(ByteBuffer buffer, long offset) {
		buffer.clear();
		try {
			if (fc.write(buffer, offset) != buffer.capacity())
				throw new Error();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	private void read(ByteBuffer buffer, long offset) {
		try {
			if (fc.read(buffer, offset) != buffer.capacity())
				throw new Error();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	ByteBuffer get(int index) {
		ByteBuffer buffer = clean.get(index);
		if (buffer != null)
			return buffer;
		buffer = create();
		read(buffer, (long) buffer.capacity() * index);
		//if (clean.size() >= 10000)
		//	clean.clear();
		clean.put(index, buffer);
		return buffer;
	}

	void store(ByteBuffer buffer, int index) {
		write(buffer, (long) buffer.capacity() * index);
		//if (clean.size() >= 10000)
		//	clean.clear();
		clean.put(index, buffer);
	}

	ByteBuffer create() {
		return ByteBuffer.allocate(buffer_size);
	}
}

public final class ExternalBTree implements ExternalMap {
	private int depth;
	private int root_id;
	private int node_count;
	private ByteBuffer root;

	// stack used by put to go back when propagating splits
	private int[] path_node;
	private int[] path_k;

	private final BufferStorage storage;
	private final int key_size;
	private final int value_size;

	public ExternalBTree(int node_size, int key_size, int value_size) {
		this.key_size = key_size;
		this.value_size = value_size;
		storage = new BufferStorage(node_size);
	}

	int cleanCache() {
		return storage.cleanCache();
	}

	private int compareKey(ByteBuffer node, int k, byte[] key) {
		assert key.length == key_size;
		for (int i = 0; i < key_size; i++) {
			byte a = node.get(2 + key_size * k + i);
			if (a != key[i])
				return a - key[i];
		}
		return 0;
	}

	private static int compare(byte[] a, byte[] b) {
		for (int i = 0; i < Math.min(a.length, b.length); i++)
			if (a[i] != b[i])
				return a[i] - b[i];
		return a.length - b.length;
	}

	private byte[] key(ByteBuffer node, int k) {
		node.position(2 + key_size * k);
		byte[] key = new byte[key_size];
		node.get(key);
		return key;
	}

	private int sizeLeaf(ByteBuffer node) {
		return 2 + keys(node) * (key_size + value_size);
	}

	private int sizeInternal(ByteBuffer node) {
		return 6 + keys(node) * (key_size + 4);
	}

	private int keys(ByteBuffer node) {
		return node.getShort(0);
	}

	private void setKeys(ByteBuffer node, int keys) {
		node.putShort(0, (short) keys);
	}

	private void setKey(ByteBuffer node, int k, byte[] key) {
		assert key.length == key_size;
		node.position(2 + key_size * k);
		node.put(key);
	}

	private int pointer(ByteBuffer node, int v) {
		return node.getInt(2 + key_size * keys(node) + 4 * v);
	}

	private void setPointer(ByteBuffer node, int v, int pointer) {
		node.putInt(2 + key_size * keys(node) + 4 * v, pointer);
	}

	private byte[] value(ByteBuffer node, int v) {
		node.position(2 + key_size * keys(node) + value_size * v);
		byte[] value = new byte[value_size];
		node.get(value);
		return value;
	}

	private void setValue(ByteBuffer node, int v, byte[] value) {
		assert value_size == value.length;
		node.position(2 + key_size * keys(node) + value_size * v);
		node.put(value);
	}

	private void insertInLeafNode(ByteBuffer node, int k, byte[] key, byte[] value) {
		byte[] bb = node.array();
		int keys = keys(node);
		System.arraycopy(bb, 2 + keys * key_size + k * 4, bb, 2 + (keys + 1) * key_size + (k + 1) * value_size,
				(keys - k) * value_size);
		System.arraycopy(bb, 2 + k * key_size, bb, 2 + (k + 1) * key_size, (keys - k) * key_size + k * value_size);
		setKeys(node, keys + 1);
		setKey(node, k, key);
		setValue(node, k, value);
	}

	private void insertInInternalNode(ByteBuffer node, int k, byte[] key, int pointer) {
		byte[] bb = node.array();
		int keys = keys(node);
		System.arraycopy(bb, 2 + keys * key_size + k * 4, bb, 2 + (keys + 1) * key_size + (k + 1) * 4,
				(keys + 1 - k) * 4);
		System.arraycopy(bb, 2 + k * key_size, bb, 2 + (k + 1) * key_size, (keys - k) * key_size + k * 4);
		setKeys(node, keys + 1);
		setKey(node, k, key);
		setPointer(node, k, pointer);
	}

	private void copyLeaf(int startK, int endK, int startP, int endP, ByteBuffer node, byte[][] overflow_keys,
			byte[][] overflow_values) {
		setKeys(node, endK - startK);
		for (int i = 0; i < endK - startK; i++)
			setKey(node, i, overflow_keys[startK + i]);
		for (int i = 0; i < endP - startP; i++)
			setValue(node, i, overflow_values[startP + i]);
	}

	private void copyInternal(int startK, int endK, int startP, int endP, ByteBuffer node, byte[][] overflow_keys,
			int[] overflow_pointers) {
		setKeys(node, endK - startK);
		for (int i = 0; i < endK - startK; i++)
			setKey(node, i, overflow_keys[startK + i]);
		for (int i = 0; i < endP - startP; i++)
			setPointer(node, i, overflow_pointers[startP + i]);
	}

	private byte[] splitLeafNode(ByteBuffer node, int k, byte[] key, byte[] value, ByteBuffer node1) {
		int keys = keys(node);
		final byte[][] overflow_keys = new byte[keys + 1][];
		final byte[][] overflow_values = new byte[keys + 1][];

		for (int i = 0; i < k; i++)
			overflow_keys[i] = key(node, i);
		overflow_keys[k] = key;
		for (int i = k; i < keys; i++)
			overflow_keys[i + 1] = key(node, i);

		for (int i = 0; i < k; i++)
			overflow_values[i] = value(node, i);
		overflow_values[k] = value;
		for (int i = k; i < keys; i++)
			overflow_values[i + 1] = value(node, i);

		int e = keys / 2 + 1;
		copyLeaf(0, e, 0, e, node1, overflow_keys, overflow_values);
		copyLeaf(e, keys(node) + 1, e, keys + 1, node, overflow_keys, overflow_values);
		assert keys(node1) + keys(node) == keys + 1;
		assert Math.abs(keys(node) - keys(node1)) <= 1;
		return overflow_keys[e - 1];
	}

	private byte[] splitInternalNode(ByteBuffer node, int k, byte[] key, int pointer, ByteBuffer node1) {
		int keys = keys(node);
		final byte[][] overflow_keys = new byte[keys + 1][];
		final int[] overflow_pointers = new int[keys + 2];

		for (int i = 0; i < k; i++)
			overflow_keys[i] = key(node, i);
		overflow_keys[k] = key;
		for (int i = k; i < keys; i++)
			overflow_keys[i + 1] = key(node, i);

		for (int i = 0; i < k; i++)
			overflow_pointers[i] = pointer(node, i);
		overflow_pointers[k] = pointer;
		for (int i = k; i < keys + 1; i++)
			overflow_pointers[i + 1] = pointer(node, i);

		int e = (keys + 1) / 2;
		copyInternal(0, e, 0, e + 1, node1, overflow_keys, overflow_pointers);
		copyInternal(e + 1, keys + 1, e + 1, keys + 2, node, overflow_keys, overflow_pointers);
		assert keys(node1) + keys(node) == keys;
		assert Math.abs(keys(node) - keys(node1)) <= 1;
		return overflow_keys[e];
	}

	private void createInitialRoot(byte[] key, byte[] value) {
		root = storage.create();
		root_id = node_count++;
		setKeys(root, 1);
		setKey(root, 0, key);
		setValue(root, 0, value);
		storage.store(root, root_id);
		depth = 1;
	}

	private void createNewRoot(byte[] key, int node1_id, int node_id) {
		root = storage.create();
		root_id = node_count++;
		setKeys(root, 1);
		setKey(root, 0, key);
		setPointer(root, 0, node1_id);
		setPointer(root, 1, node_id);
		path_node = new int[depth];
		path_k = new int[depth];
		storage.store(root, root_id);
		depth += 1;
	}

	public boolean put(byte[] key, byte[] value) {
		if (depth == 0) {
			createInitialRoot(key, value);
			assert check();
			return true;
		}

		int k, level;
		int node_id = root_id;
		ByteBuffer node = root;
		for (level = 0; level < depth - 1; level++) {
			int keys = keys(node);
			for (k = 0; k < keys; k++)
				if (compareKey(node, k, key) >= 0)
					break;
			path_node[level] = node_id;
			path_k[level] = k;
			node_id = pointer(node, k);
			node = storage.get(node_id);
		}
		int keys = keys(node);
		for (k = 0; k < keys; k++) {
			int cmp = compareKey(node, k, key);
			if (cmp == 0) {
				// In-place update
				setValue(node, k, value);
				storage.store(node, node_id);
				return false;
			}
			if (cmp >= 0)
				break;
		}

		if (sizeLeaf(node) + key_size + value_size <= node.capacity()) {
			insertInLeafNode(node, k, key, value);
			storage.store(node, node_id);
			return true;
		}

		ByteBuffer node1 = storage.create();
		int node1_id = node_count++;
		key = splitLeafNode(node, k, key, value, node1);
		storage.store(node1, node1_id);
		storage.store(node, node_id);

		while (level > 0) {
			level -= 1;
			k = path_k[level];
			node_id = path_node[level];
			node = storage.get(node_id);
			int pointer = node1_id;

			if (sizeInternal(node) + key_size + 4 <= node.capacity()) {
				insertInInternalNode(node, k, key, pointer);
				storage.store(node, node_id);
				return true;
			}

			node1 = storage.create();
			node1_id = node_count++;
			key = splitInternalNode(node, k, key, pointer, node1);
			storage.store(node1, node1_id);
			storage.store(node, node_id);
		}

		createNewRoot(key, node1_id, node_id);
		return true;
	}

	@Override
	public byte[] get(byte[] key) {
		if (depth == 0)
			return null;
		int k;
		ByteBuffer node = root;
		for (int i = 0; i < depth - 1; i++) {
			int keys = keys(node);
			for (k = 0; k < keys; k++) {
				if (compareKey(node, k, key) >= 0)
					break;
			}
			node = storage.get(pointer(node, k));
		}
		int keys = keys(node);
		for (k = 0; k < keys; k++)
			if (compareKey(node, k, key) == 0)
				return value(node, k);
		return null;
	}

	@Override
	public boolean contains(byte[] key) {
		if (depth == 0)
			return false;
		int k;
		ByteBuffer node = root;
		for (int i = 0; i < depth - 1; i++) {
			int keys = keys(node);
			for (k = 0; k < keys; k++) {
				int cmp = compareKey(node, k, key);
				if (cmp == 0)
					return true;
				if (cmp >= 0)
					break;
			}
			node = storage.get(pointer(node, k));
		}
		int keys = keys(node);
		for (k = 0; k < keys; k++) {
			int cmp = compareKey(node, k, key);
			if (cmp == 0)
				return true;
			if (cmp >= 0)
				return false;
		}
		return false;
	}

	// ====================

	private boolean check() {
		assert depth >= 0;
		if (depth == 0) {
			assert root_id == 0;
			assert node_count == 0;
			return true;
		}
		Check c = check(root, 0);
		c.nodes.add(root_id);
		Collections.sort(c.nodes);
		assert c.nodes.size() == node_count : c.nodes.toString() + " " + node_count;
		for (int i = 0; i < c.nodes.size(); i++)
			assert c.nodes.get(i) == i;
		return true;
	}

	static class Check {
		byte[] min_key;
		byte[] max_key;
		ArrayList<Integer> nodes = new ArrayList<Integer>();
	}

	private Check check(ByteBuffer node, int level) {
		// keys in increasing order
		for (int i = 1; i < keys(node); i++)
			assert compare(key(node, i - 1), key(node, i)) < 0;

		//assert keys(node) <= max_keys_per_node;

		// if leaf node
		if (level == depth - 1) {
			//assert keys(node) >= (level == 0 ? 1 : (max_keys_per_node / 2));

			Check c = new Check();
			c.min_key = key(node, 0);
			c.max_key = key(node, keys(node) - 1);
			return c;
		}

		// internal node
		//assert keys(node) >= (level == 0 ? 1 : (max_keys_per_node / 2));

		Check c = new Check();
		for (int i = 0; i < keys(node) + 1; i++) {
			assert 0 <= pointer(node, i) && pointer(node, i) < node_count;

			Check ci = check(storage.get(pointer(node, i)), level + 1);

			assert i == 0 || compare(key(node, i - 1), ci.min_key) < 0 : toHex(key(node, i - 1)) + " vs "
					+ toHex(ci.min_key) + " cmp:" + compare(key(node, i - 1), ci.min_key);
			assert i == keys(node) || compare(ci.max_key, key(node, i)) <= 0 : toHex(ci.max_key) + " vs "
					+ toHex(key(node, i)) + " cmp:" + compare(ci.max_key, key(node, i));

			c.nodes.addAll(ci.nodes);
			c.nodes.add(pointer(node, i));
			if (i == 0)
				c.min_key = ci.min_key;
			if (i == keys(node))
				c.max_key = ci.max_key;
		}
		return c;
	}

	public int nodeCount() {
		return node_count;
	}

	private static String toHex(byte[] a) {
		StringBuilder b = new StringBuilder();
		for (byte v : a)
			b.append(String.format("%02x", v));
		return b.toString();
	}

	private void print(int node_id, int level) {
		ByteBuffer node = storage.get(node_id);
		int keys = keys(node);
		if (level == depth - 1) {
			for (int i = 0; i < keys; i++)
				System.out.printf("%s : %s\n", toHex(key(node, i)), toHex(value(node, i)));
			return;
		}
		for (int i = 0; i <= keys; i++)
			print(pointer(node, i), level + 1);
	}

	public void print() {
		if (node_count > 0)
			print(root_id, 0);
	}

	private static String space(int a) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < a; i++)
			b.append(' ');
		return b.toString();
	}

	private void printTree(int node_id, int level) {
		System.out.printf("%snode %d\n", space(level * 3), node_id);
		ByteBuffer node = storage.get(node_id);
		int keys = keys(node);
		if (level == depth - 1) {
			for (int i = 0; i < keys; i++)
				System.out.printf("%s%s : %s\n", space(level * 3), toHex(key(node, i)), toHex(value(node, i)));
			return;
		}
		for (int i = 0; i < keys; i++) {
			printTree(pointer(node, i), level + 1);
			System.out.printf("%s%s\n", space(level * 3), toHex(key(node, i)));
		}
		printTree(pointer(node, keys), level + 1);
	}

	public void printTree() {
		if (node_count > 0)
			printTree(root_id, 0);
	}

	static byte[] convert(int a) {
		byte[] b = new byte[4];
		b[3] = (byte) (a & 0xFF);
		a >>>= 8;
		b[2] = (byte) (a & 0xFF);
		a >>>= 8;
		b[1] = (byte) (a & 0xFF);
		a >>>= 8;
		b[0] = (byte) (a & 0xFF);
		return b;
	}

	static int convert(byte[] v) {
		int value = ((int) v[0]) & 0xFF;
		value <<= 8;
		value |= ((int) v[1]) & 0xFF;
		value <<= 8;
		value |= ((int) v[2]) & 0xFF;
		value <<= 8;
		value |= ((int) v[3]) & 0xFF;
		return value;
	}

	// Build the largest tree
	public static void main(String[] args) {
		int node_size = 512;
		ExternalBTree map = new ExternalBTree(node_size, 8, 8);
		int n = 1000 * 1000;
		byte[] b = new byte[8];

		long a = 0;
		for (int m = 0; m < 1000;) {
			long ta = System.nanoTime();
			for (int i = 0; i < n;) {
				a += 2305843009213693951l; //= rand.nextLong();
				long aa = a;
				for (int j = 7; j >= 0; j--) {
					b[j] = (byte) (aa & 0xFF);
					aa >>>= 8;
				}
				if (map.put(b, b))
					i += 1;
			}
			m += 1;
			int cc = map.cleanCache();
			long tb = System.nanoTime();
			double epn = m * 1e6 / map.nodeCount();
			double bpe = (double) map.nodeCount() * node_size / (m * 1e6);
			double eta = (double) (1000 - m) * ((tb - ta) * 1e-9) / 60;
			System.out.printf("%d, eta %.0f min, cache %d, nodes %d, elements per node %f, bytes per element %f\n", m,
					eta, cc, map.nodeCount(), epn, bpe);
		}
	}

	// Sequential:
	// node=512 dirty=1000 put 2587, contains 941, get 966

	// node=256 dirty=100000 put 2009, contains 657, get 707
	// node=256 dirty=10000 put 1948, contains 677, get 768
	// node=256 dirty=1000 put 2001, contains 655, get 813
	// node=256 dirty=0 put 3954, contains 358, get 423

	// Random:
	// node=128 dirty=1000 put 5837, contains 2181, get 2408
	// node=192 dirty=1000 put 5463, contains 2033, get 2106
	// node=256 dirty=1000 put 5298, contains 2096, get 2055
	// node=384 dirty=1000 put 5508, contains 2096, get 2158

	// node=256 dirty=0 put 3656, contains 633, get 662

	// node=512 dirty=10 put 5456, contains 2167, get 2220
	// node=512 dirty=100 put 5315, contains 2199, get 2241
	// node=512 dirty=1000 put 5400, contains 2189, get 2319
	// node=512 dirty=10000 put 5122, contains 2329, get 2281

	// node=1024 dirty=1000 put 5649, contains 2477, get 2588

	// node=2048 dirty=1000 put 6813, contains 3741, get 3698

	public static void main2(String[] args) {
		ExternalBTree map = new ExternalBTree(256, 4, 4);
		int n = 10 * 1000 * 1000;
		byte[][] bytes = new byte[n][4];
		int[] order = new int[n];
		for (int i = 0; i < n; i++) {
			bytes[i] = convert(i);
			order[i] = i;
		}

		long ta = System.nanoTime();
		for (int i = 0; i < n; i++)
			map.put(bytes[order[i]], bytes[i]);
		long tb = System.nanoTime();
		for (int i = 0; i < n; i++)
			map.contains(bytes[order[i]]);
		long tc = System.nanoTime();
		for (int i = 0; i < n; i++)
			map.get(bytes[order[i]]);
		long td = System.nanoTime();
		System.out.printf("put %.0f, contains %.0f, get %.0f\n", (double) (tb - ta) / n, (double) (tc - tb) / n,
				(double) (td - tc) / n);
	}
}