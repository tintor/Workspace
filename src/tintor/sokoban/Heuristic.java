package tintor.sokoban;

import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.Hungarian;

final class Heuristic {
	private final int Infinity = Integer.MAX_VALUE / 2; // limitation due to Hungarian
	final Level level;
	final boolean optimal;
	final Cell[] boxes;
	final Hungarian hungarian;
	static final AutoTimer timer = new AutoTimer("heuristic");
	int deadlocks;
	int non_deadlocks;

	Heuristic(Level level, boolean optimal) {
		this.level = level;
		this.optimal = optimal;
		int num_boxes = level.num_boxes;
		hungarian = new Hungarian(level.num_boxes);
		boxes = new Cell[num_boxes];
	}

	public int evaluate(StateKey s) {
		try (AutoTimer t = timer.open()) {
			int bc = 0;
			for (Cell b : level.cells)
				if (b.alive && s.box(b)) {
					Array.copy(b.distance_box, 0, hungarian.costs[bc], 0, level.num_boxes);
					boxes[bc++] = b;
				}
			assert bc == boxes.length;

			int[] result = hungarian.execute();
			Array.for_each(result, (i, e) -> result[i] = boxes[e].distance_box[i]);
			if (Array.contains(result, Infinity)) {
				deadlocks += 1;
				return Integer.MAX_VALUE;
			}

			int h = Array.sum(result);
			assert h >= 0;
			non_deadlocks += 1;
			return optimal ? h : h * 3;
		}
	}
}