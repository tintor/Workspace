package tintor.sokoban;

import java.util.Arrays;

import tintor.common.HungarianAlgorithm;
import tintor.common.Util;
import tintor.common.Visitor;

abstract class Model {
	protected Level level;

	protected void init(Level level) {
		this.level = level;
	}

	abstract int evaluate(State s, State prev);
}

final class ZeroModel extends Model {
	public int evaluate(State s, State prev) {
		return 0;
	}
}

final class OneModel extends Model {
	public int evaluate(State s, State prev) {
		assert level.compacted;
		if (prev != null && s.box == prev.box)
			return prev.total_dist - prev.dist;

		int q = 0;
		for (int i = 0; i < level.cells; i++)
			if (s.box(i) && !level.goal(i))
				q += 1;
		return q;
	}
}

// TODO instead of using simple distance over live cells, solve level with single box and using number of pushes 
final class SimpleModel extends Model {
	int[] goal_distance;

	protected void init(Level level) {
		this.level = level;

		goal_distance = new int[level.cells];
		for (int i = 0; i < level.goal.length; i++)
			goal_distance[i] = -1;

		Visitor visitor = level.visitor.init();
		for (int i = 0; i < level.goal.length; i++)
			if (level.goal[i]) {
				goal_distance[i] = 0;
				visitor.add(i);
			}
		for (int a : visitor)
			for (int b : level.moves[a])
				if (!visitor.visited(b) && !level.dead(b) && !level.goal(b)) {
					goal_distance[b] = goal_distance[a] + 1;
					visitor.add(b);
				}
	}

	public int evaluate(State s, State prev) {
		assert level.compacted;
		if (prev != null && s.box == prev.box)
			return prev.total_dist - prev.dist;

		int q = 0;
		for (int i = 0; i < level.cells; i++)
			if (s.box(i)) {
				if (goal_distance[i] == -1)
					return Integer.MAX_VALUE;
				q += goal_distance[i];
			}
		return q;
	}
}

// Better model: assign goal to each box and find minimal such matching
final class MatchingModel extends Model {
	int[] boxes;
	int[][] distance_box;
	HungarianAlgorithm hungarian;

	protected void init(Level level) {
		this.level = level;
		int num_goals = Util.count(level.goal);
		int num_boxes = Util.count(level.start.box);
		hungarian = new HungarianAlgorithm(num_boxes, num_goals);

		boxes = new int[num_boxes];

		Visitor visitor = level.visitor;
		distance_box = new int[level.alive][num_goals];
		for (int a = 0; a < level.alive; a++)
			Arrays.fill(distance_box[a], -1); // not reachable
		int w = 0;
		for (int g = 0; g < level.alive; g++) {
			if (!level.goal[g])
				continue;
			distance_box[g][w] = 0;
			for (int a : visitor.init(g))
				for (int b : level.moves[a]) {
					if (b >= level.alive || visitor.visited(b))
						continue;
					// TODO debug
					/*if (level.canMove(b, dir) == -1)
						continue;*/
					distance_box[b][w] = distance_box[a][w] + 1;
					visitor.add(b);
				}
			w += 1;
		}
	}

	public int evaluate(State s, State prev) {
		if (prev != null && s.box == prev.box)
			return prev.total_dist - prev.dist - agent_to_nearest_box_distance(prev) + agent_to_nearest_box_distance(s);

		int w = 0;
		for (int i = 0; i < level.alive; i++)
			if (s.box[i])
				boxes[w++] = i;
		assert w == boxes.length;

		for (int i = 0; i < boxes.length; i++)
			for (int j = 0; j < distance_box[0].length; j++) {
				int d = distance_box[boxes[i]][j];
				hungarian.cost[i][j] = d < 0 ? Double.POSITIVE_INFINITY : d;
			}

		int[] goal = hungarian.execute();
		for (int i = 0; i < boxes.length; i++)
			if (goal[i] < 0)
				return Integer.MAX_VALUE;

		int sum = 0;
		for (int i = 0; i < boxes.length; i++)
			sum += distance_box[boxes[i]][goal[i]];

		return sum + agent_to_nearest_box_distance(s);
	}

	int agent_to_nearest_box_distance(State s) {
		int dist = Integer.MAX_VALUE;
		for (int i = 0; i < level.alive; i++)
			if (s.box[i])
				dist = Math.min(level.agent_distance[s.agent][i], dist);
		return dist - 1;
	}
}