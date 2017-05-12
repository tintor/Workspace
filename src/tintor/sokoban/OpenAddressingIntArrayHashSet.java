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
}