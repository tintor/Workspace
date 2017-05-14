package tintor.sokoban;

import java.util.ArrayDeque;

public final class IterativeDeepeningAStar {
	State solve(Level level) {
		Heuristic heuristic = new Heuristic(level, false);
		if (level.is_solved_fast(level.start.box))
			return level.start;

		ArrayDeque<State> stack = new ArrayDeque<State>();
		int total_dist_max = heuristic.evaluate(level.start);
		while (true) {
			assert stack.isEmpty();
			stack.push(level.start);

			int total_dist_cutoff_min = Integer.MAX_VALUE;
			while (!stack.isEmpty()) {
				State a = stack.pop();
				for (Move e : level.cells[a.agent].moves) {
					State b = null; // a.move(dir, level, context.optimal_macro_moves);
					if (b == null)
						break;
					// TODO cut move if possible

					b.set_heuristic(heuristic.evaluate(b));
					if (b.total_dist > total_dist_max) {
						total_dist_cutoff_min = Math.min(total_dist_cutoff_min, b.total_dist);
						continue;
					}
					stack.push(b);
				}
				// TODO reorder moves
			}
			if (total_dist_cutoff_min == Integer.MAX_VALUE)
				break;
			total_dist_max = total_dist_cutoff_min;
		}
		return null;
	}
}