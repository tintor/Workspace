package tintor.sokoban;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Scanner;

import tintor.common.Regex;
import tintor.common.Util;
import tintor.common.Visitor;

class LowLevel {
	static ArrayList<String> loadLevelLines(String filename) {
		int levelNo = -1;
		if (Regex.matches(filename, "^(.*):(\\d+)$")) {
			filename = Regex.group(1);
			levelNo = Integer.parseInt(Regex.group(2));
		}

		ArrayList<String> lines = new ArrayList<String>();
		boolean inside = levelNo == -1;
		Scanner sc = Util.scanFile(filename);
		while (sc.hasNextLine()) {
			String s = sc.nextLine();
			if (s.startsWith("'") || s.startsWith(";"))
				continue;
			if (inside) {
				if (s.trim().isEmpty())
					break;
				lines.add(s);
				continue;
			}
			if (Regex.matches(s, "^Level (\\d+)$") && Integer.parseInt(Regex.group(1)) == levelNo) {
				inside = true;
			}
		}
		return lines;
	}

	LowLevel(String filename) {
		final ArrayList<String> lines = loadLevelLines(filename);

		int w = 0;
		for (String s : lines)
			w = Math.max(w, s.length());
		width = w + 1;

		cells = lines.size() * (w + 1);
		buffer = new char[cells];
		for (int row = 0; row < lines.size(); row++) {
			String s = lines.get(row);
			for (int col = 0; col < w; col++)
				buffer[row * (w + 1) + col] = col < s.length() ? s.charAt(col) : Space;
			buffer[row * (w + 1) + w] = '\n';
		}

		int boxes = 0, goals = 0;
		for (int i = 0; i < cells; i++) {
			if (box(i))
				boxes += 1;
			if (goal(i))
				goals += 1;
		}

		if (boxes == 0)
			throw new IllegalArgumentException("no boxes");
		if (boxes != goals)
			throw new IllegalArgumentException("count(box) != count(goal) " + boxes + " vs. " + goals);
		agent = agent();
	}

	public static interface IndexToChar {
		char fn(int index);
	}

	void print(IndexToChar ch) {
		for (int i = 0; i < new_to_old.length; i++)
			buffer[new_to_old[i]] = ch.fn(i);
		System.out.print(buffer);
	}

	boolean wall(int i) {
		return buffer[i] == Wall || buffer[i] == '\n';
	}

	boolean goal(int i) {
		return buffer[i] == AgentGoal || buffer[i] == BoxGoal || buffer[i] == Goal;
	}

	boolean box(int i) {
		return buffer[i] == BoxGoal || buffer[i] == Box;
	}

	int agent() {
		int a = -1;
		for (int i = 0; i < cells; i++) {
			if (buffer[i] == AgentGoal || buffer[i] == Agent) {
				if (a != -1)
					throw new IllegalArgumentException("multiple agents");
				a = i;
			}
		}
		if (a == -1)
			throw new IllegalArgumentException("no agent");
		return a;
	}

	int move(int pos, int dir) {
		int m = Level.Bad;
		if (dir == Level.Left && (pos % width) > 0)
			m = pos - 1;
		if (dir == Level.Right && (pos + 1) % width != 0)
			m = pos + 1;
		if (dir == Level.Up && pos >= width)
			m = pos - width;
		if (dir == Level.Down && pos + width < cells)
			m = pos + width;
		return (m != Level.Bad && !wall(m)) ? m : Level.Bad;
	}

	boolean[] compute_walkable() {
		Visitor visitor = new Visitor(cells);
		for (int a : visitor.init(agent))
			for (byte dir = 0; dir < 4; dir++) {
				int b = move(a, dir);
				if (b != Level.Bad && !visitor.visited(b))
					visitor.add(b);
			}
		return visitor.visited();
	}

	private static class Triple {
		final int agent, box, dist;

		Triple(int agent, int box, int dist) {
			this.agent = agent;
			this.box = box;
			this.dist = dist;
		}
	}

	int[] compute_min_dist_to_solve() {
		int[] result = new int[cells];
		Deque<Triple> queue = new ArrayDeque<Triple>();
		final boolean[][] set = new boolean[cells][cells];

		for (int b = 0; b < cells; b++) {
			if (wall(b)) {
				result[b] = Integer.MAX_VALUE;
				continue;
			}
			if (goal(b)) {
				result[b] = 0;
				continue;
			}

			queue.clear();
			for (int a = 0; a < cells; a++) {
				Arrays.fill(set[a], false);
				if (wall(a) || !is_next_to(a, b, width))
					continue;
				queue.push(new Triple(a, b, 0));
				set[a][b] = true;
			}

			int min_dist = Integer.MAX_VALUE;
			while (!queue.isEmpty()) {
				final Triple s = queue.pollFirst();
				for (int dir = 0; dir < 4; dir++) {
					final int ap = move(s.agent, dir);
					if (ap == Level.Bad)
						continue;
					if (ap != s.box) {
						if (!set[ap][s.box]) {
							set[ap][s.box] = true;
							queue.push(new Triple(ap, s.box, s.dist + 1));
						}
						continue;
					}
					final int bp = move(ap, dir);
					if (bp == Level.Bad)
						continue;
					if (goal(bp)) {
						min_dist = s.dist + 1;
						queue.clear();
						break;
					}
					if (!set[ap][bp]) {
						set[ap][bp] = true;
						queue.push(new Triple(ap, bp, s.dist + 1));
					}
				}
			}
			result[b] = min_dist;
		}
		return result;
	}

	private static boolean is_next_to(int a, int b, int width) {
		int ax = a % width, ay = a / width;
		int bx = b % width, by = b / width;
		return Math.abs(ax - bx) + Math.abs(ay - by) == 1;
	}

	final static char Box = '$';
	final static char Wall = '#';
	final static char BoxGoal = '*';
	final static char AgentGoal = '+';
	final static char Goal = '.';
	final static char Agent = '@';
	final static char Space = ' ';

	final int width, cells, agent;
	final char[] buffer;
	protected int[] new_to_old;
}