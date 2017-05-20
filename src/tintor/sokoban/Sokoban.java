package tintor.sokoban;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import tintor.common.Flags;

@UtilityClass
public class Sokoban {
	@SneakyThrows
	public String[] init(String[] args, int min, int max) {
		for (Class<?> c : new Class<?>[] { Deadlock.class, OpenSet.class, ClosedSet.class, PatternIndex.class,
				Heuristic.class, State.class, AStarSolver.class, Level.class })
			Class.forName(c.getName());
		return Flags.parse(args, min, max);
	}
}
