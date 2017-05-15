package tintor.sokoban;

public final class Cell {
	public static enum Dir {
		Left, Up, Right, Down;

		Dir reverse() {
			return Dir.values()[ordinal() ^ 2];
		}

		Dir cw() {
			return Dir.values()[(ordinal() + 1) % 4];
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

	boolean bottleneck_tunnel() {
		return bottleneck && moves.length == 2;
	}

	boolean real_tunnel() {
		return moves.length == 2 && moves[0].dir == moves[1].dir.reverse();
	}

	boolean tunnel() {
		return real_tunnel() || (moves.length == 2 && (moves[0].cell.real_tunnel() || moves[1].cell.real_tunnel()));
	}

	boolean tunnel_entrance() {
		return moves.length == 2 && ((moves[0].cell.moves.length == 2 && moves[1].cell.moves.length != 2)
				|| (moves[0].cell.moves.length != 2 && moves[1].cell.moves.length == 2));
	}

	boolean tunnel_interior() {
		return moves.length == 2 && (moves[0].cell.moves.length == 2 && moves[1].cell.moves.length == 2);
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

	Cell(Level level, int xy, char ch) {
		this.level = level;
		this.xy = xy;
		goal = ch == Level.Goal || ch == Level.AgentGoal || ch == Level.BoxGoal;
		box = ch == Level.Box || ch == Level.BoxGoal;
	}
}