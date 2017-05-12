package tintor.sokoban;

// Maps int[N] -> long. Very memory compact.
// Removing elements is efficient and doesn't leave garbage.
public final class OpenAddressingIntArrayHashMap extends OpenAddressingIntArrayHashBase {
	private long[] value;

	public OpenAddressingIntArrayHashMap(int N) {
		super(N);
		value = new long[mask + 1];
	}

	public long get(int[] k) {
		int a = get_internal(k);
		return a < 0 ? 0 : value[a];
	}

	public void insert_unsafe(int[] k, long v) {
		int a = insert_unsafe_internal(k);
		value[a] = v;
		grow();
	}

	public void update_unsafe(int[] k, long v) {
		int a = update_unsafe_internal(k);
		value[a] = v;
	}

	// returns true if insert
	public boolean insert_or_update(int[] k, long v) {
		int a = insert_or_update_internal(k);
		if (a < 0) {
			value[~a] = v;
			return false;
		}
		value[a] = v;
		grow();
		return true;
	}

	@Override
	protected void reinsert_all(int old_capacity, int[] old_key) {
		final long[] old_value = value;
		value = new long[value.length * 2];
		for (int b = 0; b < old_capacity; b++)
			if (!empty(b, old_key)) {
				int a = reinsert(b, old_key);
				value[a] = old_value[b];
			}
	}

	@Override
	protected void copy(int a, int b) {
		super.copy(a, b);
		value[a] = value[b];
	}
}