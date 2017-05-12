package tintor.sokoban;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import tintor.common.Util;

final class ValueList {
	volatile int index;
	long value;
	ValueList next;

	ValueList(int index, long value, ValueList next) {
		this.index = index;
		this.value = value;
		this.next = next;
	}

	void write(FileChannel file, ByteBuffer buffer) {
		buffer.position(0);
		buffer.putLong(0, value);
		Util.write(file, buffer, index * 8L);
	}
}

// Maps int[N] -> long. Very memory compact.
// Removing elements is efficient and doesn't leave garbage.
public final class OpenAddressingIntArrayHashMapDisk extends OpenAddressingIntArrayHashBase {
	volatile FileChannel file;
	private final ByteBuffer buffer = ByteBuffer.allocate(8);

	// Thread can only read from it.
	volatile ValueList value_list = new ValueList(-1, 0, null);

	private static final ConcurrentLinkedQueue<WeakReference<OpenAddressingIntArrayHashMapDisk>> queue = new ConcurrentLinkedQueue<>();
	private static final ArrayList<WeakReference<OpenAddressingIntArrayHashMapDisk>> list = new ArrayList<>();
	private static final Thread thread = new Thread(OpenAddressingIntArrayHashMapDisk::value_list_writer);

	static {
		thread.setDaemon(true);
		thread.start();
	}

	private static void value_list_writer() {
		while (true) {
			while (!queue.isEmpty())
				list.add(queue.poll());

			boolean idle = true;
			Iterator<WeakReference<OpenAddressingIntArrayHashMapDisk>> it = list.iterator();
			while (it.hasNext()) {
				OpenAddressingIntArrayHashMapDisk map = it.next().get();
				if (map == null) {
					it.remove();
					continue;
				}

				ValueList a = map.value_list;
				if (a.index == -1)
					continue;
				while (a.next != null) {
					if (a.next.index != -1)
						a.next.write(map.file, map.buffer);
					a.next = a.next.next;
				}
				a.write(map.file, map.buffer);
				a.index = -1;
				idle = false;
			}
			if (idle)
				Util.sleep(100);
		}
	}

	public OpenAddressingIntArrayHashMapDisk(int N) {
		super(N);
		file = Util.newTempFile();
		queue.add(new WeakReference<OpenAddressingIntArrayHashMapDisk>(this));
	}

	@Override
	protected void finalize() {
		Util.close(file);
	}

	public long get(int[] k) {
		int a = get_internal(k);
		return a < 0 ? 0 : value(a);
	}

	public void insert_unsafe(int[] k, long v) {
		int a = insert_unsafe_internal(k);
		set_value(a, v);
		grow();
	}

	public void update_unsafe(int[] k, long v) {
		int a = update_unsafe_internal(k);
		set_value(a, v);
	}

	// returns true if insert
	public boolean insert_or_update(int[] k, long v) {
		int a = insert_or_update_internal(k);
		if (a < 0) {
			set_value(~a, v);
			return false;
		}
		set_value(a, v);
		grow();
		return true;
	}

	@Override
	protected void reinsert_all(int old_capacity, int[] old_key) {
		while (value_list.index != -1)
			Util.sleep(100);

		final FileChannel old_file = file;
		file = Util.newTempFile();
		for (int b = 0; b < old_capacity; b++)
			if (!empty(b, old_key)) {
				int a = reinsert(b, old_key);
				buffer.position(0);
				Util.read(old_file, buffer, b * 8L);
				set_value(a, buffer.getLong(0));
			}
		Util.close(old_file);
	}

	@Override
	protected void copy(int a, int b) {
		super.copy(a, b);
		set_value(a, value(b));
	}

	private long value(int a) {
		//for (ValueList e = value_list; e != null; e = e.next)
		//	if (e.index == a)
		//		return e.value;

		buffer.position(0);
		Util.read(file, buffer, a * 8L);
		return buffer.getLong(0);
	}

	private void set_value(int a, long v) {
		buffer.position(0);
		buffer.putLong(0, v);
		Util.write(file, buffer, a * 8L);
		//value_list = new ValueList(a, v, value_list);
	}
}