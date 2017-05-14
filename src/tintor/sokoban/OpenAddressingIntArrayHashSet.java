package tintor.sokoban;

// Set of int[N]. Very memory compact.
final class OpenAddressingIntArrayHashSet extends OpenAddressingIntArrayHashBase {
	public OpenAddressingIntArrayHashSet(int N) {
		super(N);
	}

	public void insert_unsafe(int[] k) {
		insert_unsafe_internal(k);
		grow();
	}

	public boolean insert(int[] k) {
		int a = insert_or_update_internal(k);
		if (a < 0)
			return false;
		grow();
		return true;
	}
}