package tintor.sokoban;

import static tintor.common.Util.print;

import java.io.FileWriter;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import tintor.common.Array;
import tintor.common.CpuTimer;
import tintor.common.Flags;

// TODO: when we cleanup ClosedSet and OpenSet from new deadlock we can also cleanup PatternIndex

// TODO: less expensive (to compute) heuristic for levels with a lot of conflicts

// TODO: add 3x3 patterns to is_simple_deadlock checker

// TODO: Packing sequence from http://www.sokobano.de/wiki/index.php?title=JSoko_Solver

// microban1 - solved!
// microban2 - solved!
// microban3 - all except: 58(try without heuristic)
// microban4 - all except: 56(brute force, quickly solvable with parking), 57(23min), 75(40+min), 99
// microban5 - all except: 24 and 26
// simple - all except: 30 and 59
// original - 48 / 90 solved (44 under 7.5min, remaining 3 under 1 hour + level 18 in 7.5+ hours)

// BUG nabokosmos:37 no solution!
// BUG goal-zone deadlock doesn't seem to work for spiros:197
// BUG deadlock detection by heuristic doesn't seem to work (original:90 + weaken)

// TODO: look for Auction algorithm implementation as alternative to Hungarian algorithm

// TODO: count number of free goals at the start. If very low (ie. just 1, then turn off heuristic) microban4:57

// Make Original.java run levels in parallel now that we can measure CpuTime

// Break level 25

// Solved without unstuck:
// original:10 15h30m
// original:11 2m43s closed:200k

// TODO remove useless dead cell rooms (ie. original:25)

// Unsolved
// microban4:57 1) requires 1 box deadlock patterns (box right in front of tunnel, with agent on the left)
//              2) solving with -unstuck_period=20 -heuristic_mult=0 -goalzone=off
// original:19 has 3 useless dead and 1 useless alive cell at the top
// original:10 need patterns for bipartite deadlocks (add bipartite matching to Deadlock.check())
// original:23 [boxes:18 alive:104 space:73] 5 rooms (1 goal room) in a line with 4 doors between them
// original:39 goal room is separated by one way tunnel => optimal solution can be decomposed into two optimal solutions
// original:36, original:43, original:63 goal room has 2-cell wide entrance, but box can be pushed only through one cell
// original:35 no need to make cell at the dead level entrance alive if box can never be pushed there!
// TODO original:62 remove dead rooms with agent
// TODO original:63, original:69 goal_zone_entrance is wrong!
// original:74 goal room with 2 bottleneck entrances (try to solve it first by replacing 2 goal room entrances with box dispensers)

// TODO try to remove boxes to minimize is_valid_level patterns (need to allow more goals than boxes)

// TODO bug with negative garbage counter
// TODO kill switch - http port that stops solving (in order to be able to see prof output)

// Heuristic:
// TODO faster heuristic: how? (incremental heuristic) can we pre-compute a matching for a state and reuse that with a single box pushed 
// TODO add other heuristic implementation - and switch them with flag

// TODO concept of push bottleneck, even if there is no single cell that is bottleneck for agent, there could be one for boxes (and it can be used as goal room entrance)

// TODO push macros: if after push there is only one way to push the same box again (even with all other non-frozen boxes removed), then do that push immediately

// TODO: command line parameters:
// - turn off dead tunnels
// - turn off push macros
// - min / max state_space in microban

// TODO separate line in stats showing memory (total memory used and ETA to memory exhaustion)
// TODO auto switch to always cleaning when close to OOM

// TODO [non-optimal] trick for one original level:
//   add an extra wall to reduce 2-cell wide entrance to a goal room to 1-cell wide

// TODO split level into rooms and tunnels
//      - can we compress 2-cell wide tunnels? box can only move up or down one lane, agent can go around the box, more than one box can fit into tunnel
//      - compress tunnels
//      -   keep entrance and exit (if alive) to be able to park one box in tunnel
//      -   for alive tunnels, keep only one tunnel cell in the middle to be able to park (distance to enter 1, distance to exit = length-1 on both sides!)
//      - assign each cell to one room or tunnel (and group cells by tunnels / rooms)
//      - tunnel can be bottleneck or not
//      - room can be bottleneck or not
//      - build a graph of rooms and tunnels
//      - tunnels don't have to be straight (but they have to connect two different rooms, or two non-touching cells in the same room)
//      - tunnels can be alive (can push box through) or dead (or for agent)
//      - compute parking capacity of each room (max number of boxes in room without deadlocking)
//      - easy to count boxes / goals in each room
//      - easy to split level and solve each room separately (ie. push order in goal room)
//      - fork cells that connect 3 or 4 tunnels are considered regular rooms

// TODO push macro:
// - if box is pushed inside a tunnel with a box already inside (on goal) then keep pushing the box all the way through
// TODO goal cut:
// - if there are multiple pushes from a given State and some of them reduce the number of unreachable goal cells (by pushing into unreachable goal cell) then cut all other pushes
// TODO: separate Timer for partial deadlock.match inside frozen boxes from the global one
// TODO: OpenSet: keep StateArray index in map to avoid garbage (and expensive cleanup)
// TODO: keep track of AutoTimer total for the full Timer nesting path

// Performance:
// TODO: at the start greedily find order to fill goal nodes using matching from Heuristic (and trigger goal macros during search)
// TODO: specialization of OpenAddressingIntArrayHashMap with long[] instead of int[]
// TODO: add timer for growth operation (separate for disk and memory)
// TODO: tweak load factor of OpenAddressingHashMap for max perf
// TODO: try –XX:+UseG1GC
// TODO: keep counter of how many times each deadlock pattern was triggered
// TODO: load patterns.txt at startup
// TODO: remove timers for code sections < 1%
// TODO: web handler for Solver to be able to probe manually into the internals (or just observe search with better UI)
//       - also be able to switch flags as search is running
//       - be able to skip solving a current level (if very slow)
//       - render level in different ways (colors / layers)
//       - browse through deadlock patterns / closed set / open set / each state (all fields + link to prev state)
// TODO: speed up hungarian matching by starting from a good initial match (from the previous state)
// TODO: record all Microban performance runs (also with snapshot of all the code, git branch / tag?)
// TODO: how to teach solver to park boxes in rooms with single entrance (and to find out maximum parking space)?
// TODO: be able to save Solver state and resume it later
// TODO: +++ how to cut same distance (or worse) moves?
// TODO: +++ how to cut low influence moves?
// TODO: +++ be better at detecting deadlocks in far parts of the level

// TODO: store extra data for every level: boxes, alive, cells, state_space,
//       optimal_solution (steps and compute time), some_solution (steps and compute time)

// Misc:
// TODO: move unused common classes into a separate package 

// Deadlock:
// TODO: Take every 2 and 3 box subset from start position and initialize deadlock DB
//       with all REACHABLE deadlock patterns of size 2 and 3. This way ContainsFrozenBoxes can stop when it reaches 2 or 3 boxes.
// TODO: +++ deadlock patterns for goal cells (must keep track of which goals are empty and which are occupied by frozen boxes)
// TODO: +++ regenerate level (recompute alive / walkable / bottleneck cells) once a box becomes frozen on goal.
//       Will reduce number of live cells and make deadlock checking and heuristic faster and more accurate.
// TODO: can avoid calling matchesPattern() inside containsFrozenBoxes() if box push can be reversed
//       (can do a very cheap check here, just try to go around the box)
// TODO: pre-populate PatternIndex with level-independent deadlocks

// Search space reduction:
// TODO: +++ Take advantage of symmetry
// TODO: How to optimize goal room with single entrance? Two entrances?
// TODO: What states can we cut without eliminating: all solutions / optimal solution? (tunnels for example)
// TODO: Split level into sub-levels that can be solved independently

// Memory:
// TODO: +++ when a new deadlock pattern is found with >=3 boxes scan Closed and Open with new pattern to trim them
// TODO: general level loader class, knows only about wall cells, compresses level to only walkable interior cells

// Heuristic:
// TODO: +++ if level has a goal room with single entrance ==> speed up matching by matching from the goal room entrance
// TODO: should be exact if only one box is left?
// TODO: tell MatchingModel which solved boxes are frozen (so that it could potentially find a new deadlock)

// Other:
// TODO: IDA*
// TODO: [Last] How can search be parallelized?
//       - parallel explore(a)
//       - parallel pattern search
//       - run I-corral deadlock checker on Closed set (or on last explored node) in background
// TODO: Nightly mode - run all microban + original levels over night (catching OOMs) and report results
// TODO: Look at the sokoban PhD for more ideas.

@UtilityClass
public class Solver {
	@SneakyThrows
	static void printSolution(Level level, State[] solution) {
		FileWriter file = new FileWriter(level.name + "_solution.txt");
		int pushes = 0;
		for (int i = 0; i < solution.length; i++) {
			State s = solution[i];
			State n = i == solution.length - 1 ? null : solution[i + 1];
			Cell agent = level.cells[s.agent];
			pushes += s.pushes;
			if (i == solution.length - 1 || agent.dir[s.dir].cell.id != n.agent || s.dir != n.dir) {
				file.write(String.format("pushes:%s dist:%s heur:%s\n", pushes, s.dist, s.total_dist - s.dist));
				file.write(Code.emojify(level.render(s)));
			}
		}
		file.close();
	}

	static CpuTimer timer = new CpuTimer();

	static char hex(int a) {
		if (a >= 26 || a < 0)
			return '?';
		return (char) (a < 10 ? '0' + a : 'a' + a - 10);
	}

	final static Flags.Int trace = new Flags.Int("trace", 2);
	final static Flags.Text skip = new Flags.Text("skip", "");

	public static void main(String[] args) throws Exception {
		args = Sokoban.init(args, 1, 1);
		String[] skips = Array.map_inline(skip.value.split(","), s -> ":" + s);
		print("[%s]\n", args[0]);
		if (!args[0].contains(":")) {
			for (Level level : Level.loadAll(args[0]))
				if (Array.find(skips, s -> level.name.endsWith(s)) == null) {
					timer.time_ns = 0;
					print("%s\n", level.name);
					solve(level);
					print("\n");
				}
			return;
		}
		solve(Level.load(args[0]));
	}

	static Flags.Bool show_level_details = new Flags.Bool("show_level_details", false);

	static void solve(Level level) {
		print("cells:%s alive:%s boxes:%s state_space:%s goal_room_entrance:%s\n", level.cells.length,
				level.alive.length, level.num_boxes, level.state_space(), level.goal_section_entrance != null);
		level.print(level.start);

		if (show_level_details.value) {
			print("bottleneck\n");
			level.print(p -> p.bottleneck ? '.' : ' ');
			print("box_bottleneck\n");
			level.print(p -> p.box_bottleneck ? '.' : ' ');
			print("rooms\n");
			level.print(p -> hex(p.room));
		}

		AStarSolver solver = new AStarSolver(level, true);
		solver.trace = (int) trace.value;

		timer.open();
		State end = solver.solve();
		timer.close();
		if (end == null) {
			print("no solution! %s\n", timer);
			System.exit(0);
		} else {
			State[] solution = solver.extractPath(end);
			printSolution(level, solution);
			int pushes = 0;
			for (State s : solution)
				pushes += s.pushes;
			print("solved in %s pushes! %s\n", pushes, timer);
		}
		print("\n");
	}
}