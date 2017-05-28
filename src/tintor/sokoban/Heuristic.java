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
	final boolean[] frozen;

	Heuristic(Level level, boolean optimal) {
		this.level = level;
		this.optimal = optimal;
		int num_boxes = level.num_boxes;
		hungarian = new Hungarian(level.num_boxes);
		boxes = new Cell[num_boxes];
		frozen = new boolean[num_boxes];
	}

	private static final Flags.Int heuristic_mult = new Flags.Int("heuristic_mult", 3);

	public int evaluate(StateKey s) {
		if (heuristic_mult.value == 0)
			return 0;
		@Cleanup val t = timer.open();
		int bc = 0;
		for (Cell c : level.goals)
			frozen[c.id] = s.box(c) && LevelUtil.is_frozen_on_goal(c, s.box);
		for (Cell b : level.alive)
			if (s.box(b)) {
				if (b.goal && frozen[b.id])
					for (Cell goal : level.goals)
						hungarian.costs[bc][goal.id] = goal == b ? 0 : Infinity;
				else
					for (Cell goal : level.goals)
						hungarian.costs[bc][goal.id] = frozen[goal.id] ? Infinity : b.distance_box[goal.id];
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
		return optimal ? h : h * (int) heuristic_mult.value;
	}
}