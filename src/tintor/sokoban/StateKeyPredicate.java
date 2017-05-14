package tintor.sokoban;

interface StateKeyPredicate {
	boolean test(int agent, int[] box, int offset);
}