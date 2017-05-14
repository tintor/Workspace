package tintor.sokoban;

public final class Cell {
	public static enum Dir {
		Left, Up, Right, Down
	}

	final CellLevel level;
	final Cell[] dir = new Cell[4];
	Move[] moves;
	public final int xy;
	final boolean goal;
	int goal_ordinal = -1;
	boolean box;
	int id;
	boolean alive;
	public boolean bottleneck;
	int room = -1;

	static final int Infinity = Integer.MAX_VALUE / 2; // limitation due to Hungarian
	int[] distance_box; // distance[goal_orginal]

	boolean bottleneck_tunnel() {
		return bottleneck && moves.length == 2;
	}

	boolean tunnel_entrance() {
		return moves.length == 2 && (moves[0].cell.moves.length != 2 || moves[1].cell.moves.length != 2);
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

	Cell move(Dir d) {
		return dir[d.ordinal()];
	}

	// move in reverse direction
	Cell rmove(Dir d) {
		return dir[d.ordinal() ^ 2];
	}

	Cell(CellLevel level, int xy, char ch) {
		this.level = level;
		this.xy = xy;
		goal = ch == CellLevel.Goal || ch == CellLevel.AgentGoal || ch == CellLevel.BoxGoal;
		box = ch == CellLevel.Box || ch == CellLevel.BoxGoal;
	}
}