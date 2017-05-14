package tintor.sokoban;

import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.Hungarian;
import tintor.common.PairVisitor;

final class Heuristic {
	private final int Infinity = Integer.MAX_VALUE / 2; // limitation due to Hungarian
	final Level level;
	final boolean optimal;
	final int[] boxes;
	final int[][] distance_box; // distance[box][goal_orginal]
	final Hungarian hungarian;
	static final AutoTimer timer = new AutoTimer("heuristic");
	int deadlocks;
	int non_deadlocks;

	Heuristic(Level level, boolean optimal) {
		this.level = level;
		this.optimal = optimal;
		int num_goals = level.num_boxes;
		int num_boxes = level.num_boxes;
		hungarian = new Hungarian(level.num_boxes);

		boxes = new int[num_boxes];

		distance_box = Array.ofInt(level.alive, num_goals, Infinity);
		int w = 0;
		PairVisitor visitor = new PairVisitor(level.cells, level.alive);
		for (int g = 0; g < level.alive; g++)
			if (level.goal(g))
				compute_distances_from_goal(g, w++, visitor);
	}

	void compute_distances_from_goal(int goal, int goal_ordinal, PairVisitor visitor) {
		int[][] distance = Array.ofInt(level.cells, level.alive, Infinity);
		visitor.init();
		for (int a : level.moves[goal]) {
			visitor.add(a, goal);
			distance[a][goal] = 0;
		}
		distance_box[goal][goal_ordinal] = 0;

		while (!visitor.done()) {
			final int agent = visitor.first();
			final int box = visitor.second();
			assert distance[agent][box] >= 0;
			assert distance[agent][box] != Infinity;
			distance_box[box][goal_ordinal] = Math.min(distance_box[box][goal_ordinal], distance[agent][box]);

			for (int c : level.moves[agent]) {
				// TODO moves included only if ! optimal
				if (c != box && visitor.try_add(c, box))
					distance[c][box] = distance[agent][box] + 1;
				if (agent < level.alive && level.move(agent, level.delta[c][agent]) == box && visitor.try_add(c, agent))
					distance[c][agent] = distance[agent][box] + 1;
			}
		}
	}

	public int evaluate(StateKey s) {
		try (AutoTimer t = timer.open()) {
			int bc = 0;
			for (int b = 0; b < level.alive; b++)
				if (s.box(b)) {
					Array.copy(distance_box[b], 0, hungarian.costs[bc], 0, level.num_boxes);
					boxes[bc++] = b;
				}
			assert bc == boxes.length;

			int[] result = hungarian.execute();
			Array.for_each(result, (i, e) -> result[i] = distance_box[boxes[e]][i]);
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