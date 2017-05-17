package tintor.sokoban;

import lombok.Cleanup;
import lombok.val;
import lombok.experimental.ExtensionMethod;
import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.For;
import tintor.common.Hungarian;

@ExtensionMethod(Array.class)
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
		@Cleanup val t = timer.open();
		int bc = 0;
		for (Cell b : level.cells)
			if (b.alive && s.box(b)) {
				Array.copy(b.distance_box, 0, hungarian.costs[bc], 0, level.num_boxes);
				boxes[bc++] = b;
			}
		assert bc == boxes.length;

		int[] result = hungarian.execute();
		For.each(result, (i, e) -> result[i] = boxes[e].distance_box[i]);
		if (result.contains(Infinity)) {
			deadlocks += 1;
			return Integer.MAX_VALUE;
		}

		int h = result.sum();
		assert h >= 0;
		non_deadlocks += 1;
		return optimal ? h : h * 3;
	}
}