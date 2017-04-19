package tintor.sokoban;

import java.util.Arrays;

import tintor.common.InlineChainingHashSet;
import tintor.common.Zobrist;

final class State extends InlineChainingHashSet.Element implements Comparable<State> {
	State(int agent, boolean[] box) {
		this.agent = (short) agent;
		this.box = box;
	}

	boolean box(int i) {
		return i < box.length ? box[i] : false;
	}

	boolean equals(State s) {
		return agent == s.agent && Arrays.equals(box, s.box);
	}

	@Override
	public boolean equals(Object o) {
		return equals((State) o);
	}

	@Override
	public int hashCode() {
		return Zobrist.hash(box) ^ Zobrist.hash(agent + box.length);
	}

	int prevAgent(Level level) {
		int a = level.move(agent, Level.reverseDir(dir));
		assert 0 <= a && a < level.cells;
		return a;
	}

	boolean[] prevBox(Level level) {
		if (!is_push)
			return box;
		assert 0 <= dir && dir < 4;
		int b = level.move(agent, dir);
		assert b != -1;
		assert box(b);
		boolean[] nbox = box.clone();
		assert b < nbox.length : b + " " + nbox.length + " " + box.length;
		nbox[b] = false;
		nbox[agent] = true;
		return nbox;
	}

	State move(int dir, Level level) {
		assert 0 <= dir && dir < 4;
		int a = level.move(agent, dir);
		if (a == -1)
			return null;
		if (!box(a)) {
			State s = new State(a, box);
			s.dir = (byte) dir;
			s.is_push = false;
			return s;
		}
		int b = level.move(a, dir);
		if (b == -1 || b >= box.length)
			return null;
		if (!box(b) && !level.dead(b)) {
			boolean[] nbox = box.clone();
			nbox[a] = false;
			nbox[b] = true;
			State s = new State(a, nbox);
			s.dir = (byte) dir;
			s.is_push = true;
			return s;
		}
		return null;
	}

	public int compareTo(State a) {
		if (a.greedy_score != greedy_score)
			return a.greedy_score - greedy_score;

		//if (total_dist != a.total_dist)
		return total_dist - a.total_dist;
		//if (num_solved_boxes != a.num_solved_boxes)
		//	return num_solved_boxes - a.num_solved_boxes;

		//return 0;
	}

	// Identity (primary key)
	final short agent;
	final boolean[] box;

	// Properties of State
	byte dir = -1; // direction of move from previous state
	short dist;
	boolean is_push;

	// Transient fields for OpenSet
	short total_dist; // = distance from start + heuristic to goal
	short greedy_score;
}