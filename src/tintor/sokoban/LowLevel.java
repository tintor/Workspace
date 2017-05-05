package tintor.sokoban;

import java.util.ArrayList;
import java.util.Scanner;

import tintor.common.ArrayDequeInt;
import tintor.common.BinaryHeapInt;
import tintor.common.BitMatrix;
import tintor.common.Regex;
import tintor.common.Util;
import tintor.common.Visitor;

class LowLevel {
	private static String preprocess(String line) {
		if (line.startsWith("'") || line.startsWith(";") || line.trim().isEmpty() || Regex.matches(line, "^Level\\s+"))
			return "";
		return line;
	}

	static int numberOfLevels(String filename) {
		int currentLevelNo = 0;
		boolean inside = false;
		Scanner sc = Util.scanFile("data/sokoban/" + filename);
		while (sc.hasNextLine()) {
			String s = preprocess(sc.nextLine());
			if (!inside && !s.isEmpty()) {
				currentLevelNo += 1;
				inside = true;
			} else if (s.isEmpty())
				inside = false;
		}
		return currentLevelNo;
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
		Scanner sc = Util.scanFile("data/sokoban/" + filename);
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

	static LowLevel load(String filename) {
		final ArrayList<String> lines = loadLevelLines(filename);

		int w = 0;
		for (String s : lines)
			w = Math.max(w, s.length());

		int cells = lines.size() * (w + 1);
		char[] buffer = new char[cells];
		for (int row = 0; row < lines.size(); row++) {
			String s = lines.get(row);
			for (int col = 0; col < w; col++)
				buffer[row * (w + 1) + col] = col < s.length() ? s.charAt(col) : Space;
			buffer[row * (w + 1) + w] = '\n';
		}

		return new LowLevel(w, buffer, filename);
	}

	private LowLevel(int w, char[] buffer, String name) {
		this.name = name;
		assert buffer.length % (w + 1) == 0;
		this.buffer = buffer;
		width = w + 1;
		cells = buffer.length;

		// try to move agent and remove walkable dead cells
		int a = agent();
		int d = 0;
		while (!goal(a) && degree(a) == 1) {
			int b = moves(a, 0);
			if (buffer[b] != Space)
				break;
			buffer[a] = Wall;
			buffer[b] = Agent;
			a = b;
			d += 1;
		}
		dist = d;

		// remove dead end cells
		for (int p = 0; p < cells; p++) {
			a = p;
			while (degree(a) == 1 && buffer[a] == Space) {
				int b = moves(a, 0);
				buffer[a] = Wall;
				a = b;
			}
		}
	}

	void check_boxes_and_goals() {
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
	}

	boolean check_boxes_and_goals_silent() {
		int boxes = 0, goals = 0;
		for (int i = 0; i < cells; i++) {
			if (box(i))
				boxes += 1;
			if (goal(i))
				goals += 1;
		}
		return boxes == goals;
	}

	public static interface IndexToChar {
		char fn(int index);
	}

	LowLevel clone(IndexToChar ch) {
		char[] copy = buffer.clone();
		for (int i = 0; i < new_to_old.length; i++)
			copy[new_to_old[i]] = ch.fn(i);
		return new LowLevel(width - 1, copy, null);
	}

	void print(IndexToChar ch) {
		System.out.print(render(ch));
	}

	char[] render(IndexToChar ch) {
		for (int i = 0; i < new_to_old.length; i++)
			buffer[new_to_old[i]] = ch.fn(i);
		return buffer;
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

	int moves(int pos, int i) {
		for (int dir = 0; dir < 4; dir += 1)
			if (move(pos, dir) != Level.Bad && i-- == 0)
				return move(pos, dir);
		return Level.Bad;
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
		visitor.add(agent());
		while (!visitor.done()) {
			int a = visitor.next();
			for (byte dir = 0; dir < 4; dir++) {
				int b = move(a, dir);
				if (b != Level.Bad && !visitor.visited(b))
					visitor.add(b);
			}
		}
		// remove cells that are far from walkable cells
		for (int i = 0; i < buffer.length; i++)
			if (!visitor.visited()[i] && buffer[i] != '\n' && !is_close_to_walkable(i, visitor.visited(), false))
				buffer[i] = Space;
		return visitor.visited();
	}

	void add_walls(boolean[] walkable) {
		for (int i = 0; i < buffer.length; i++)
			if (!walkable[i] && buffer[i] != '\n' && is_close_to_walkable(i, walkable, true))
				buffer[i] = Wall;
	}

	private boolean is_close_to_walkable(int pos, boolean[] walkable, boolean diagonal) {
		int x = pos % width, y = pos / width;
		for (int ax = Math.max(0, x - 1); ax <= Math.min(x + 1, width - 2); ax++)
			for (int ay = Math.max(0, y - 1); ay <= Math.min(y + 1, buffer.length / width - 1); ay++)
				if ((diagonal || ax == x || ay == y) && walkable[ay * width + ax])
					return true;
		return false;
	}

	class AreAllGoalsReachable {
		int num_goals;
		int[] goals;
		boolean[] reached = new boolean[cells];

		int manhattan_dist(int a, int b) {
			int ax = a % width, ay = a / width;
			int bx = b % width, by = b / width;
			return Math.abs(ax - bx) + Math.abs(ay - by);
		}

		int min_manhattan_dist_to_goal(int a) {
			int d = Integer.MAX_VALUE;
			for (int e : goals)
				if (!reached[e])
					d = Math.min(d, manhattan_dist(a, e));
			return d;
		}

		int make_pair(int a, int b) {
			int dist = manhattan_dist(a, b) + min_manhattan_dist_to_goal(b);
			assert 0 <= dist && dist < (1 << 8);
			dist -= 128;
			assert -128 <= dist && dist <= 127;
			assert !(dist < 0) || ((dist << 24) < 0);
			assert !(dist >= 0) || ((dist << 24) >= 0);

			assert 0 <= a && a < (1 << 12);
			assert 0 <= b && b < (1 << 12);
			return (dist << 24) | (a << 12) | b;
		}

		int update_pair(int pair) {
			return make_pair((pair >> 12) & 0xFFF, pair & 0xFFF);
		}

		void init_goals_without_boxes() {
			num_goals = 0;
			for (int i = 0; i < cells; i++)
				if (goal(i) && !box(i))
					num_goals += 1;
			goals = new int[num_goals];
			int w = 0;
			for (int i = 0; i < cells; i++)
				if (goal(i) && !box(i))
					goals[w++] = i;
		}

		// is every goal reachable by some box
		boolean run(BitMatrix visited) {
			BinaryHeapInt queue = new BinaryHeapInt();
			init_goals_without_boxes();
			int num_goals = goals.length;
			if (num_goals == 0)
				return true;
			final int agent = agent();
			for (int i = 0; i < cells; i++)
				if (box(i))
					queue.add(make_pair(agent, i));

			while (queue.size() > 0) {
				final int s = queue.removeMin();
				final int s_agent = (s >> 12) & 0xFFF;
				final int s_box = s & 0xFFF;

				for (int dir = 0; dir < 4; dir++) {
					final int ap = move(s_agent, dir);
					if (ap == Level.Bad)
						continue;
					if (ap != s_box) {
						if (visited.try_set(ap, s_box))
							queue.add(make_pair(ap, s_box));
						continue;
					}
					final int bp = move(ap, dir);
					if (bp != Level.Bad && visited.try_set(ap, bp)) {
						if (goal(bp) && !reached[bp]) {
							reached[bp] = true;
							if (--num_goals == 0)
								return true;
							for (int i = 0; i < queue.size(); i++)
								queue.set(i, update_pair(queue.get(i)));
							queue.heapify();
						}
						queue.add(make_pair(ap, bp));
					}
				}
			}
			return false;
		}
	}

	private static int make_pair(int a, int b) {
		assert 0 <= a && a < (1 << 12);
		assert 0 <= b && b < (1 << 12);
		return (a << 12) | b;
	}

	// TODO instead of searching forward N times => search backward once from every goal
	boolean[] compute_alive(ArrayDequeInt deque, BitMatrix visited, boolean[] walkable) {
		boolean[] alive = new boolean[cells];
		for (int b = 0; b < cells; b++) {
			if (!walkable[b])
				continue;
			if (goal(b)) {
				alive[b] = true;
				continue;
			}

			deque.clear();
			visited.clear();
			for (int dir = 0; dir < 4; dir++) {
				int a = move(b, dir);
				if (a != Level.Bad) {
					deque.addLast(make_pair(a, b));
					visited.set(a, b);
				}
			}

			while (!deque.isEmpty()) {
				final int s = deque.removeFirst();
				final int s_agent = (s >> 12) & 0xFFF;
				final int s_box = s & 0xFFF;

				for (int dir = 0; dir < 4; dir++) {
					final int ap = move(s_agent, dir);
					if (ap == Level.Bad)
						continue;
					if (ap != s_box) {
						if (visited.try_set(ap, s_box))
							deque.addLast(make_pair(ap, s_box));
						continue;
					}
					final int bp = move(ap, dir);
					if (bp == Level.Bad)
						continue;
					if (goal(bp)) {
						alive[b] = true;
						deque.clear();
						break;
					}
					if (visited.try_set(ap, bp))
						deque.addLast(make_pair(ap, bp));
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

	// remove empty rows and columns of cells
	void minimize() {
		// TODO
	}

	// ignores boxes
	boolean is_symetric(boolean[] walkable, boolean flipX, boolean flipY, boolean flipDiag) {
		int height = buffer.length / width;
		if (flipDiag && height != width - 1) {
			flipDiag = false;
			if (!flipX && !flipY)
				return false;
		}
		for (int i = 0; i < buffer.length; i++) {
			int j = i;
			if (flipX) {
				int x = j % width;
				int xp = width - 1 - x;
				j = j - x + xp;
			}
			if (flipY) {
				int x = j % width, y = j / width;
				int yp = height - 1 - y;
				j = yp * width + x;
			}
			if (flipDiag) {
				int x = j % width, y = j / width;
				j = x * width + y;
			}
			if (walkable[i] && goal(i) != goal(j))
				return false;
		}
		return true;
	}

	final static char Box = '$';
	final static char Wall = '#';
	final static char BoxGoal = '*';
	final static char AgentGoal = '+';
	final static char Goal = '.';
	final static char Agent = '@';
	final static char Space = ' ';

	final int width, cells;
	final int dist; // initial distance of agent
	final char[] buffer;
	protected int[] new_to_old;
	final String name;
}