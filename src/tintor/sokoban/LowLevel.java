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
	private static String preprocess(String line) {
		if (line.startsWith("'") || line.startsWith(";") || line.trim().isEmpty() || Regex.matches(line, "^Level\\s+"))
			return "";
		return line;
	}

	static ArrayList<String> loadLevelLines(String filename) {
		int desiredLevelNo = 1;
		if (Regex.matches(filename, "^(.*):(\\d+)$")) {
			filename = Regex.group(1);
			desiredLevelNo = Integer.parseInt(Regex.group(2));
		}

		ArrayList<String> lines = new ArrayList<String>();
		int currentLevelNo = 0;
		boolean inside = false;
		Scanner sc = Util.scanFile(filename);
		while (sc.hasNextLine()) {
			String s = preprocess(sc.nextLine());
			if (!inside && !s.isEmpty()) {
				currentLevelNo += 1;
				inside = true;
			} else if (s.isEmpty())
				inside = false;

			if (inside && currentLevelNo == desiredLevelNo)
				lines.add(s);
		}
		return lines;
	}

	LowLevel(String filename) {
		final ArrayList<String> lines = loadLevelLines("data/sokoban/" + filename);

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
		agent();
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
		if (wall(pos))
			return Level.Bad;
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

	int degree(int pos) {
		int count = 0;
		for (int dir = 0; dir < 4; dir += 1)
			if (move(pos, dir) != Level.Bad)
				count += 1;
		return count;
	}

	int end_of_half_tunnel(int pos, boolean[] alive) {
		if (!alive[pos] || degree(pos) > 2)
			return Level.Bad;
		int count = 0;
		int b = -1;
		for (int dir = 0; dir < 4; dir += 1)
			if (move(pos, dir) != Level.Bad && alive[move(pos, dir)]) {
				count += 1;
				b = move(pos, dir);
			}
		if (count != 1 || degree(b) != 2)
			return Level.Bad;
		count = 0;
		for (int dir = 0; dir < 4; dir += 1)
			if (move(b, dir) != Level.Bad && alive[move(b, dir)])
				count += 1;
		if (count != 2)
			return Level.Bad;
		return b;
	}

	boolean[] compute_walkable() {
		Visitor visitor = new Visitor(cells);
		for (int a : visitor.init(agent()))
			for (byte dir = 0; dir < 4; dir++) {
				int b = move(a, dir);
				if (b != Level.Bad && !visitor.visited(b))
					visitor.add(b);
			}
		// remove cells that are far from walkable cells
		for (int i = 0; i < buffer.length; i++)
			if (buffer[i] != '\n' && !is_close_to_walkable(i, visitor.visited()))
				buffer[i] = Space;
		return visitor.visited();
	}

	private boolean is_close_to_walkable(int pos, boolean[] walkable) {
		int x = pos % width, y = pos / width;
		for (int ax = Math.max(0, x - 1); ax <= Math.min(x + 1, width - 2); ax++)
			for (int ay = Math.max(0, y - 1); ay <= Math.min(y + 1, buffer.length / width - 1); ay++)
				if (walkable[ay * width + ax])
					return true;
		return false;
	}

	private static class Triple {
		final int agent, box, dist;

		Triple(int agent, int box, int dist) {
			this.agent = agent;
			this.box = box;
			this.dist = dist;
		}
	}

	boolean[] compute_alive(boolean[] walkable) {
		boolean[] alive = new boolean[cells];
		Deque<Triple> queue = new ArrayDeque<Triple>();
		final boolean[][] set = new boolean[cells][cells];

		for (int b = 0; b < cells; b++) {
			if (!walkable[b])
				continue;
			if (goal(b)) {
				alive[b] = true;
				continue;
			}

			queue.clear();
			for (int a = 0; a < cells; a++) {
				Arrays.fill(set[a], false);
				if (wall(a) || !is_next_to(a, b))
					continue;
				queue.push(new Triple(a, b, 0));
				set[a][b] = true;
			}

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
						alive[b] = true;
						queue.clear();
						break;
					}
					if (!set[ap][bp]) {
						set[ap][bp] = true;
						queue.push(new Triple(ap, bp, s.dist + 1));
					}
				}
			}
		}

		// Remove useless alive cells (no goal dead-ends of alive cells)
		for (int i = 0; i < cells; i++) {
			int a = i;
			while (true) {
				int b = end_of_half_tunnel(a, alive);
				if (b == -1 || box(a) || goal(a))
					break;
				alive[a] = false;
				a = b;
			}
		}
		return alive;
	}

	private boolean is_next_to(int a, int b) {
		return move(a, Level.Up) == b || move(a, Level.Down) == b || move(a, Level.Left) == b
				|| move(a, Level.Right) == b;
	}

	final static char Box = '$';
	final static char Wall = '#';
	final static char BoxGoal = '*';
	final static char AgentGoal = '+';
	final static char Goal = '.';
	final static char Agent = '@';
	final static char Space = ' ';

	final int width, cells;
	final char[] buffer;
	protected int[] new_to_old;
}