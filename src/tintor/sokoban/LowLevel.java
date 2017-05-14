package tintor.sokoban;

import java.util.ArrayList;
import java.util.Scanner;

import tintor.common.Array;
import tintor.common.BipartiteMatching;
import tintor.common.PairVisitor;
import tintor.common.Regex;
import tintor.common.Util;
import tintor.common.Visitor;

public final class LowLevel {
	private static String preprocess(String line) {
		if (line.startsWith("'") || line.startsWith(";") || line.trim().isEmpty() || Regex.matches(line, "^Level\\s+"))
			return "";
		return line;
	}

	static int numberOfLevels(String filename) {
		int currentLevelNo = 0;
		boolean inside = false;
		Scanner sc = Util.scanner("data/sokoban/" + filename);
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
		Scanner sc = Util.scanner("data/sokoban/" + filename);
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
		for (int p = 0; p < cells(); p++) {
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
		for (int i = 0; i < cells(); i++) {
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
		for (int i = 0; i < cells(); i++) {
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
		for (int i = 0; i < cells(); i++) {
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
		if (dir == Level.Down && pos + width < cells())
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
		Visitor visitor = new Visitor(cells());
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

	public boolean are_all_goals_reachable_quick(PairVisitor visitor) {
		// TODO assert original level is single_goal_room
		main: for (int g = 0; g < cells(); g++) {
			if (!goal(g) || box(g))
				continue;
			visitor.init();
			for (int dir = 0; dir < 4; dir++) {
				int a = move(g, dir);
				if (a == Level.Bad)
					continue;
				visitor.add(a, g);
			}
			while (!visitor.done()) {
				final int a = visitor.first();
				final int b = visitor.second();
				for (int dir = 0; dir < 4; dir++) {
					int c = move(a, dir);
					if (c == Level.Bad || c == b)
						continue;
					visitor.try_add(c, b);
					if (move(a, Level.reverseDir(dir)) == b && visitor.try_add(c, a))
						if (box(a) && !goal(a) && !goal(c))
							continue main;
				}
			}
			return false;
		}
		return true;
	}

	// TODO what deadlocks are found by are_all_goals_reachable_full, but not by are_all_goals_reachable_quick
	public boolean are_all_goals_reachable_full(PairVisitor visitor) {
		int[] num = new int[2];
		int[] box_ordinal = Array.ofInt(cells(), p -> box(p) ? num[0]++ : -1);
		int[] goal_ordinal = Array.ofInt(cells(), p -> goal(p) ? num[1]++ : -1);
		if (num[0] != num[1])
			return false;
		int num_boxes = num[0];
		if (num_boxes == 0)
			return true;

		boolean[][] can_reach = new boolean[num_boxes][num_boxes];
		main: for (int g = 0; g < cells(); g++) {
			if (!goal(g))
				continue;
			int count = 0;
			if (box(g)) {
				can_reach[box_ordinal[g]][goal_ordinal[g]] = true;
				if (++count == num_boxes)
					continue main;
			}
			visitor.init();
			for (int dir = 0; dir < 4; dir++) {
				int a = move(g, dir);
				if (a == Level.Bad)
					continue;
				visitor.add(a, g);
			}
			while (!visitor.done()) {
				final int a = visitor.first();
				final int b = visitor.second();
				for (int dir = 0; dir < 4; dir++) {
					int c = move(a, dir);
					if (c == Level.Bad || c == b)
						continue;
					visitor.try_add(c, b);
					if (move(a, Level.reverseDir(dir)) == b && visitor.try_add(c, a))
						if (box(a) && !can_reach[box_ordinal[a]][goal_ordinal[g]]) {
							can_reach[box_ordinal[a]][goal_ordinal[g]] = true;
							if (++count == num_boxes)
								continue main;
						}
				}
			}
			if (count == 0)
				return false;
		}
		return BipartiteMatching.maxBPM(can_reach) == num_boxes;
	}

	// TODO instead of searching forward N times => search backward once from every goal
	boolean[] compute_alive(PairVisitor visitor, boolean[] walkable) {
		boolean[] alive = new boolean[cells()];
		for (int b = 0; b < cells(); b++) {
			if (!walkable[b])
				continue;
			if (goal(b)) {
				alive[b] = true;
				continue;
			}

			visitor.init();
			for (int dir = 0; dir < 4; dir++) {
				int a = move(b, dir);
				if (a != Level.Bad)
					visitor.add(a, b);
			}

			loop: while (!visitor.done()) {
				final int s_agent = visitor.first();
				final int s_box = visitor.second();

				for (int dir = 0; dir < 4; dir++) {
					final int ap = move(s_agent, dir);
					if (ap == Level.Bad)
						continue;
					if (ap != s_box) {
						visitor.try_add(ap, s_box);
						continue;
					}
					final int bp = move(ap, dir);
					if (bp == Level.Bad)
						continue;
					if (goal(bp)) {
						alive[b] = true;
						break loop;
					}
					visitor.try_add(ap, bp);
				}
			}
		}

		// Remove useless alive cells (no goal dead-ends of alive cells)
		for (int i = 0; i < cells(); i++) {
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
	boolean[] minimize(boolean[] walkable) {
		boolean[] remove_column = Array.ofBool(width, x -> Util.all(height(), y -> buffer[y * width + x] == Space));
		boolean[] remove_row = Array.ofBool(height(), y -> Util.all(width, x -> buffer[y * width + x] == Space));

		int size = (width - Util.count(remove_column)) * (height() - Util.count(remove_row));
		if (size == buffer.length)
			return walkable;

		char[] new_buffer = new char[size];
		boolean[] new_walkable = new boolean[size];
		int w = 0;
		for (int y = 0; y < height(); y++)
			if (!remove_row[y])
				for (int x = 0; x < width; x++)
					if (!remove_column[x]) {
						new_buffer[w] = buffer[y * width + x];
						new_walkable[w] = walkable[y * width + x];
						w += 1;
					}
		buffer = new_buffer;
		width -= Util.count(remove_column);
		return new_walkable;
	}

	int height() {
		return buffer.length / width;
	}

	private int flip_hor(int a) {
		int x = a % width;
		int xp = width - 1 - x;
		return a - x + xp;
	}

	private int flip_ver(int a) {
		int x = a % width, y = a / width;
		int yp = height() - 1 - y;
		return yp * width + x;
	}

	private int transpose(int a) {
		assert height() == width - 1;
		int x = a % width, y = a / width;
		return x * width + y;
	}

	final static int Left = 0;
	final static int Up = 1;
	final static int Right = 2;
	final static int Down = 3;

	int transform_dir(int dir, int symmetry, boolean reverse) {
		int dd = dir;
		assert 0 <= dir && dir < 4;
		if (reverse && (symmetry & 4) != 0) {
			dir = (dir <= 1) ? Left ^ Up ^ dir : Right ^ Down ^ dir;
		}
		if ((symmetry & 1) != 0) { // flip left and right
			dir = (dir % 2 == 0) ? Left ^ Right ^ dir : dir;
		}
		if ((symmetry & 2) != 0) { // flip up and down
			dir = (dir % 2 == 1) ? Down ^ Up ^ dir : dir;
		}
		if (!reverse && (symmetry & 4) != 0) {
			dir = (dir <= 1) ? Left ^ Up ^ dir : Right ^ Down ^ dir;
		}
		if (!reverse)
			assert dd == transform_dir(dir, symmetry, true);
		return dir;
	}

	int transform(int a, int symmetry) {
		assert 0 <= a && a < cells();
		assert 0 < symmetry && symmetry < 8;
		if ((symmetry & 1) != 0) { // flip left and right
			assert a == flip_hor(flip_hor(a));
			a = flip_hor(a);
		}
		if ((symmetry & 2) != 0) { // flip up and down
			assert a == flip_ver(flip_ver(a));
			a = flip_ver(a);
		}
		if ((symmetry & 4) != 0) {
			assert a == transpose(transpose(a));
			a = transpose(a);
		}
		return a;
	}

	// ignores boxes
	boolean is_symmetric(boolean[] walkable, int symmetry) {
		if ((symmetry & 4) != 0 && height() != width - 1)
			return false;
		for (int a = 0; a < cells(); a++) {
			if (!walkable[a])
				continue;
			int b = transform(a, symmetry);
			if (!walkable[b] || goal(a) != goal(b))
				return false;
		}
		return true;
	}

	int cells() {
		return buffer.length;
	}

	final static char Box = '$';
	final static char Wall = '#';
	final static char BoxGoal = '*';
	final static char AgentGoal = '+';
	final static char Goal = '.';
	final static char Agent = '@';
	final static char Space = ' ';

	int width;
	final int dist; // initial distance of agent
	char[] buffer;
	protected int[] new_to_old;
	public final String name;
}