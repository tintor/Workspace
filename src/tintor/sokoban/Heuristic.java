package tintor.sokoban;

import lombok.Cleanup;
import lombok.val;
import lombok.experimental.ExtensionMethod;
import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.Flags;
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
	long deadlocks;
	long non_deadlocks;

	Heuristic(Level level, boolean optimal) {
		this.level = level;
		this.optimal = optimal;
		int num_boxes = level.num_boxes;
		hungarian = new Hungarian(level.num_boxes);
		boxes = new Cell[num_boxes];
	}

	private static boolean EnableGoalRoomHeuristic = false;
	private static final Flags.Int heuristic_mult = new Flags.Int("heuristic_mult", 3, "");

	public int evaluate(StateKey s) {
		@Cleanup val t = timer.open();
		int prefix = 0;
		int bc = 0;
		for (Cell b : level.cells)
			if (b.alive && s.box(b)) {
				Cell entrance = level.goal_section_entrance;
				boolean found = false;
				if (EnableGoalRoomHeuristic && entrance != null && b.room != entrance.room)
					for (int i = 0; i < level.num_boxes; i++)
						if (b.distance_box[i] != Infinity && entrance.distance_box[i] != Infinity) {
							if (b.distance_box[i] < entrance.distance_box[i])
								break;
							prefix += b.distance_box[i] - entrance.distance_box[i];
							found = true;
							break;
						}
				if (found)
					Array.copy(entrance.distance_box, 0, hungarian.costs[bc], 0, level.num_boxes);
				else
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

		int h = prefix + result.sum();
		assert h >= 0;
		non_deadlocks += 1;
		return optimal ? h : h * (int) heuristic_mult.value;
	}
}