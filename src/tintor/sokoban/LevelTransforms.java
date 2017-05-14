package tintor.sokoban;

import java.util.Arrays;

import tintor.common.Bits;
import tintor.common.Util;

final class LevelTransforms {
	LevelTransforms(Level level, boolean[] walkable, int[] old_to_new) {
		this.level = level;
		transforms = new int[7][];
		inv_transforms = new int[7][];
		dir_transforms = new int[7][];
		inv_dir_transforms = new int[7][];
		int w = 0;
		for (int symmetry = 1; symmetry <= 7; symmetry += 1)
			if (level.low.is_symmetric(walkable, symmetry)) {
				int[] mapping = new int[level.cells];
				int[] inv_mapping = new int[level.cells];
				for (int i = 0; i < level.low.cells(); i++)
					if (walkable[i]) {
						int a = old_to_new[i];
						int c = old_to_new[level.low.transform(i, symmetry)];
						mapping[c] = a;
						inv_mapping[a] = c;
					}
				transforms[w] = mapping;
				inv_transforms[w] = inv_mapping;

				for (int i = 0; i < level.alive; i++)
					assert 0 <= mapping[i] && mapping[i] < level.alive;
				for (int i = level.alive; i < level.cells; i++)
					assert level.alive <= mapping[i] && mapping[i] < level.cells;
				assert Util.all(level.cells, i -> inv_mapping[mapping[i]] == i);
				assert Util.sum(level.cells, i -> i - mapping[i]) == 0;

				dir_transforms[w] = new int[4];
				inv_dir_transforms[w] = new int[4];
				for (int a = 0; a < 4; a++) {
					int c = level.low.transform_dir(a, symmetry, false);
					dir_transforms[w][c] = a;
					inv_dir_transforms[w][a] = c;
				}

				w += 1;
			}
		transforms = Arrays.copyOf(transforms, w);
		transforms = new int[0][];
		inv_transforms = Arrays.copyOf(inv_transforms, w);
		inv_transforms = new int[0][];
		dir_transforms = Arrays.copyOf(dir_transforms, w);
		inv_dir_transforms = Arrays.copyOf(inv_dir_transforms, w);
	}

	private final Level level;

	// For all the level symmetries
	private int[][] transforms;
	private int[][] inv_transforms;

	private int[][] dir_transforms;
	private int[][] inv_dir_transforms;

	private int[] transform(int[] box, int[] mapping) {
		int[] out = new int[box.length];
		for (int i = 0; i < level.alive; i++)
			if (Bits.test(box, i))
				Bits.set(out, mapping[i]);
		return out;
	}

	/*	private int[] transform(int nbox0, int[] box, int[] mapping) {
			int[] out = new int[box.length];
			out[0] = nbox0;
			for (int i = 32; i < alive; i++)
				if (Bits.test(box, mapping[i]))
					Bits.set(out, i);
			return out;
		}
	
		private int transform_one(int[] box, int[] mapping) {
			int out = 0;
			for (int i = 0; i < Math.min(32, alive); i++)
				if (Bits.test(box, mapping[i]))
					out = Bits.set(out, i);
			return out;
		}*/

	private static int compare(int[] a, int[] b) {
		assert a.length == b.length;
		for (int i = 0; i < a.length; i++) {
			if (a[i] < b[i])
				return -1;
			if (a[i] > b[i])
				return 1;
		}
		return 0;
	}

	StateKey normalize(StateKey k) {
		if (transforms.length == 0)
			return k;

		int agent = k.agent;
		int[] box = k.box;

		for (int[] map : transforms) {
			int nagent = map[k.agent];
			if (nagent < agent) {
				agent = nagent;
				box = transform(k.box, map);
				continue;
			}

			if (nagent > agent)
				continue;

			//int nbox0 = transform_one(k.box, map);
			//if (nbox0 > box[0])
			//	continue;

			int[] nbox = transform(/*nbox0, */k.box, map);
			assert Arrays.equals(nbox, transform(k.box, map));
			if (compare(nbox, box) < 0) {
				agent = nagent;
				box = nbox;
			}
		}
		return box == k.box ? k : new StateKey(agent, box);
	}

	State normalize(State s) {
		if (s == null || transforms.length == 0)
			return s;
		assert s.symmetry == 0;

		int agent = s.agent;
		int[] box = s.box;
		int symmetry = 0;
		int dir = s.dir;
		int prev_agent = s.prev_agent;

		for (int i = 0; i < transforms.length; i++) {
			int[] map = transforms[i];
			int[] dmap = dir_transforms[i];

			int nagent = map[s.agent];
			if (nagent < agent) {
				agent = nagent;
				box = transform(s.box, map);
				symmetry = i + 1;
				dir = dmap[s.dir];
				prev_agent = map[s.prev_agent];
				continue;
			}

			if (nagent > agent)
				continue;

			//int nbox0 = transform_one(s.box, map);
			//if (nbox0 > box[0])
			//	continue;

			int[] nbox = transform(/*nbox0, */s.box, map);
			assert Arrays.equals(nbox, transform(s.box, map));
			if (compare(nbox, box) < 0) {
				agent = nagent;
				box = nbox;
				symmetry = i + 1;
				dir = dmap[s.dir];
				prev_agent = map[s.prev_agent];
			}
		}
		State q = s;
		if (box != s.box) {
			q = new State(agent, box, symmetry, s.dist, dir, s.pushes, prev_agent);
			q.set_heuristic(s.total_dist - s.dist);
		}
		assert ((StateKey) q).equals(normalize((StateKey) s));
		assert Arrays.equals(denormalize(q).box, s.box);
		assert denormalize(q).agent == s.agent;
		assert denormalize(q).pushes == s.pushes;
		assert denormalize(q).dist == s.dist;
		assert denormalize(q).total_dist == s.total_dist;
		assert denormalize(q).prev_agent == s.prev_agent;
		assert denormalize(q).dir == s.dir : denormalize(q).dir + " " + q.dir + " " + s.dir + " " + q.symmetry;
		assert denormalize(q).equals(s);
		return q;
	}

	State denormalize(State s) {
		if (s == null || s.symmetry == 0)
			return s;
		int[] map = inv_transforms[s.symmetry - 1];
		int[] dmap = inv_dir_transforms[s.symmetry - 1];
		State q = new State(map[s.agent], transform(s.box, map), 0, s.dist, dmap[s.dir], s.pushes, map[s.prev_agent]);
		q.set_heuristic(s.total_dist - s.dist);
		return q;
	}
}