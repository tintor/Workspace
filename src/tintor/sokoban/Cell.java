package tintor.sokoban;

public final class Cell {
	public static enum Dir {
		Left, Up, Right, Down;

		Dir reverse;
		Dir next;

		static {
			for (Dir d : Dir.values()) {
				d.reverse = Dir.values()[d.ordinal() ^ 2];
				d.next = Dir.values()[(d.ordinal() + 1) % 4];
			}
		}
	}

	final Level level;
	final Move[] dir = new Move[4];
	Move[] moves;
	public final int xy;
	final boolean goal;
	int goal_ordinal = -1;
	boolean box;
	int id;
	boolean alive;
	public boolean bottleneck;
	int room; // 0 means door between rooms

	static final int Infinity = Integer.MAX_VALUE / 2; // limitation due to Hungarian
	int[] distance_box; // distance[goal_orginal]

	Cell(Level level, int xy, char ch) {
		this.level = level;
		this.xy = xy;
		goal = ch == Level.Goal || ch == Level.AgentGoal || ch == Level.BoxGoal;
		box = ch == Level.Box || ch == Level.BoxGoal;
	}

	boolean bottleneck_tunnel() {
		return bottleneck && tunnel();
	}

	public boolean tunnel() {
		return moves.length == 2;
	}

	public boolean tunnel_entrance() {
		return tunnel() && (!moves[0].cell.tunnel() || !moves[1].cell.tunnel());
	}

	public boolean tunnel_interior() {
		return tunnel() && moves[0].cell.tunnel() && moves[1].cell.tunnel();
	}

	int dist(Dir d) {
		for (Move m : moves)
			if (m.dir == d)
				return m.dist;
		throw new Error();
	}

	Move move(int d) {
		return dir[d];
	}

	Move move(Dir d) {
		return dir[d.ordinal()];
	}

	// move in reverse direction
	Move rmove(Dir d) {
		return dir[d.ordinal() ^ 2];
	}
}