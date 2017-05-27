package tintor.sokoban;

import java.io.FileWriter;
import java.util.Arrays;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import tintor.common.Array;
import tintor.common.AutoTimer;
import tintor.common.Bits;
import tintor.common.Util;
import tintor.sokoban.Cell.Dir;

final class PatternList {
	public long[] array_box = Array.EmptyLongArray;

	public int size;
	public int[] end;
	private final int box_length;

	public PatternList(int level_num_boxes, int level_alive) {
		end = new int[level_num_boxes + 1];
		box_length = (level_alive + 31) / 32;
	}

	public void clear() {
		size = 0;
		Arrays.fill(end, 0);
		array_box = Array.EmptyLongArray;
	}

	public void add(int[] box, int num_boxes) {
		if (matches(box, 0, num_boxes))
			return;

		int N = (box_length + 1) / 2;
		if (array_box.length == size * N)
			array_box = Arrays.copyOf(array_box, Math.max(N, array_box.length * 2));

		int x = end[num_boxes];
		System.arraycopy(array_box, x * N, array_box, (x + 1) * N, (size - x) * N);
		for (int i = 0; i < N; i++) {
			long b = intLow(box[i * 2]);
			if (box.length > i * 2 + 1)
				b |= intHigh(box[i * 2 + 1]);
			array_box[x * N + i] = b;
		}
		size += 1;
		for (int i = num_boxes; i < end.length; i++)
			end[i] += 1;
	}

	static long intLow(int a) {
		return a & 0xFFFFFFFFl;
	}

	static long intHigh(int a) {
		return ((long) a) << 32;
	}

	public boolean matches(int[] box, int offset, int num_boxes) {
		return matches_internal(box, offset, num_boxes) != -1;
	}

	public int matches_internal(int[] box, int offset, int num_boxes) {
		int N = (box_length + 1) / 2;

		long box0 = intLow(box[offset + 0]);
		if (box_length > 1)
			box0 |= intHigh(box[offset + 1]);
		if (N == 1) {
			for (int i = 0; i < end[num_boxes]; i++)
				if ((box0 | array_box[i]) == box0)
					return i;
			return -1;
		}

		long box1 = intLow(box[offset + 2]);
		if (box_length > 3)
			box1 |= intHigh(box[offset + 3]);
		if (N == 2) {
			for (int i = 0; i < end[num_boxes]; i++)
				if ((box0 | array_box[i * 2]) == box0 && (box1 | array_box[i * 2 + 1]) == box1)
					return i;
			return -1;
		}

		long[] lbox = new long[N];
		lbox[0] = box0;
		lbox[1] = box1;
		for (int i = 2; i < N; i++) {
			long b = intLow(box[i * 2]);
			if (box_length > i * 2 + 1)
				b |= intHigh(box[i * 2 + 1]);
			lbox[i] = b;
		}
		for (int i = 0; i < end[num_boxes]; i++)
			if (matches_one(i, lbox))
				return i;
		return -1;
	}

	private boolean matches_one(int index, long[] box) {
		for (int i = 0; i < box.length; i++)
			if ((box[i] | array_box[index * box.length + i]) != box[i])
				return false;
		return true;
	}
}

public final class PatternIndex {
	private final PatternList[] pattern_index;
	private final PatternList[] pattern_index_near;
	private final PatternList[] pattern_index_new;
	private final FileWriter pattern_file;
	private final FileWriter pattern_raw_file;
	private final Level level;
	private final int box_length;
	int[] histogram;
	private final OpenAddressingIntArrayHashSet patterns;

	private static final AutoTimer timer_add = new AutoTimer("pattern.add");
	private static final AutoTimer timer_match = new AutoTimer("pattern.match");

	@SneakyThrows
	public PatternIndex(Level level) {
		this.level = level;
		box_length = (level.alive.length + 31) / 32;
		pattern_index = Array.make(level.cells.length, i -> new PatternList(level.num_boxes, level.alive.length));
		pattern_index_near = Array.make(level.cells.length, i -> new PatternList(level.num_boxes, level.alive.length));
		pattern_index_new = Array.make(level.cells.length, i -> new PatternList(level.num_boxes, level.alive.length));
		histogram = new int[level.num_boxes];
		pattern_file = new FileWriter(level.name + "_patterns.txt");
		pattern_raw_file = new FileWriter(level.name + "_patterns_raw.txt");
		patterns = new OpenAddressingIntArrayHashSet((level.cells.length + 31) / 32 + box_length);
	}

	public void load(String file) {
		// TODO load patterns.txt
	}

	public int size() {
		return patterns.size();
	}

	public void add(boolean[] agent, int[] box, int num_boxes, boolean verbose) {
		@Cleanup val t = timer_add.open();
		int[] p = Array.concat(Util.compressToIntArray(agent), box);
		if (!patterns.insert(p))
			return;

		// TODO use level transforms and add all pattern variations

		addToFile(agent, box, verbose);
		histogram[num_boxes - 1] += 1;
		for (Cell b : level.alive)
			if (Bits.test(box, b.id))
				for (Move a : b.moves)
					if (agent[a.cell.id])
						pattern_index_near[a.cell.id].add(box, num_boxes);
		for (int a = 0; a < level.cells.length; a++)
			if (agent[a]) {
				pattern_index[a].add(box, num_boxes);
				pattern_index_new[a].add(box, num_boxes);
			}
	}

	@SneakyThrows
	private void addToFile(boolean[] agent, int[] box, boolean verbose) {
		char[] render = level.render(p -> {
			if (p.id < box.length * 32 && Bits.test(box, p.id))
				return Code.Box;
			if (agent[p.id])
				return !p.alive ? Code.Dead : Code.Space;
			return Code.Goal;
		});
		if (verbose)
			System.out.print(Code.emojify(render));
		pattern_file.write(Code.emojify(render));
		pattern_raw_file.write(render);
	}

	@SneakyThrows
	public void flush() {
		pattern_file.flush();
		pattern_raw_file.flush();
	}

	private boolean looksLikeAPush(Cell agent, int[] box, int offset) {
		for (Dir dir : Dir.values()) {
			// if B is box
			Move b = agent.move(dir);
			if (b != null && b.cell.alive && Bits.test(box, offset, box_length, b.cell.id)) {
				// and if S (opposite from B) is empty
				Move s = agent.rmove(dir);
				if (s != null && (!s.cell.alive || !Bits.test(box, offset, box_length, s.cell.id)))
					return true;
			}
		}
		return false;
	}

	public boolean matches(int agent, int[] box, int offset, int num_boxes, boolean incremental) {
		@Cleanup val t = timer_match.open();
		assert looksLikeAPush(level.cells[agent], box, offset);
		return (incremental ? pattern_index_near : pattern_index)[agent].matches(box, offset, num_boxes);
	}

	public boolean matchesNew(int agent, int[] box, int offset, int num_boxes) {
		@Cleanup val t = timer_match.open();
		assert looksLikeAPush(level.cells[agent], box, offset);
		return pattern_index_new[agent].matches(box, offset, num_boxes);
	}

	public boolean hasNew() {
		for (PatternList p : pattern_index_new)
			if (p.size > 0)
				return true;
		return false;
	}

	public void clearNew() {
		for (PatternList p : pattern_index_new)
			p.clear();
	}
}