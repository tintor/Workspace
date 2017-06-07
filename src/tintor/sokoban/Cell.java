package tintor.sokoban;

import tintor.common.Bits;

public final class Cell {
	public static enum Dir {
		Left, Up, Right, Down;

		Dir reverse;
		Dir next;
		Dir prev;

		static {
			for (Dir d : Dir.values()) {
				d.reverse = Dir.values()[d.ordinal() ^ 2];
				d.next = Dir.values()[(d.ordinal() + 1) % 4];
				d.prev = Dir.values()[(d.ordinal() + 3) % 4];
			}
		}
	}

	final Level level;
	final Move[] dir = new Move[4];
	Move[] moves;
	public final int xy;
	public boolean goal;
	public boolean sink;
	boolean box;
	int id;
	public boolean alive;
	public boolean bottleneck;
	public boolean box_bottleneck;
	int room; // 0 means door between rooms
	int goal_section_entrance; // 0 - inside, 1 - at the entrance, 2 - outside

	static final int Infinity = Integer.MAX_VALUE / 2; // limitation due to Hungarian
	int[] distance_box; // distance[goal_orginal]
	int distance_box_min; // min(distance_box)

	Cell(Level level, int xy, char ch) {
		this.level = level;
		id = this.xy = xy;
		goal = ch == Code.Goal || ch == Code.AgentGoal || ch == Code.BoxGoal;
		box = ch == Code.Box || ch == Code.BoxGoal;
		sink = ch == Code.Sink || ch == Code.AgentSink;
	}

	public boolean box(int[] boxes) {
		return id < boxes.length * 32 && Bits.test(boxes, id);
	}

	public boolean straight() {
		return moves.length == 2 && moves[0].dir == moves[1].dir.reverse;
	}

	public boolean connected_to(Cell b) {
		for (Move m : moves)
			if (m.cell == b)
				return true;
		return false;
	}

	Move move(int d) {
		return dir[d];
	}

	public Move move(Dir d) {
		return dir[d.ordinal()];
	}

	// move in reverse direction
	Move rmove(Dir d) {
		return dir[d.ordinal() ^ 2];
	}
}