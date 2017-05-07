package tintor.common;

import java.util.Random;

public class HungarianTest {
	static Timer timer = new Timer();

	static final boolean perf = true;
	static final int n = perf ? 30 : 7;

	static int[] result;
	static int[] current = new int[n];
	static boolean[] used;
	static int min;
	static int[][] cost;

	static void brute(int p) {
		if (p == n) {
			int sum = 0;
			for (int i = 0; i < n; i++)
				sum += cost[current[i]][i];
			if (sum < min) {
				System.arraycopy(current, 0, result, 0, n);
				min = sum;
			}
			return;
		}
		for (int a = 0; a < n; a++)
			if (!used[a] && cost[a][p] != Inf) {
				used[a] = true;
				current[p] = a;
				brute(p + 1);
				used[a] = false;
			}
	}

	static int[] bruteForceMatching() {
		result = new int[n];
		for (int i = 0; i < n; i++)
			result[i] = i;
		used = new boolean[n];
		min = Inf;
		brute(0);
		return result;
	}

	static Timer timerA = new Timer();
	static Timer timerB = new Timer();

	static int Inf = Integer.MAX_VALUE / 2;

	static float add(float a, float b) {
		return (a == Inf || b == Inf) ? Inf : (a + b);
	}

	public static void main(String[] args) {
		Random rand = new Random(2);
		cost = new int[n][n];
		int iter = 0;
		Hungarian alg = new Hungarian(n);

		while (true) {
			double f = rand.nextDouble();
			for (int i = 0; i < n; i++)
				for (int j = 0; j < n; j++)
					alg.costs[i][j] = cost[i][j] = (rand.nextDouble() < f) ? Inf : rand.nextInt(100000000);

			if (iter % 100000 == 0) {
				timerA.total /= 100000;
				timerB.total /= 100000;
				Log.raw("test %d [%s vs %s]", iter / 100000, timerA.human(), timerB.human());
				timerA.total = 0;
				timerB.total = 0;
			}
			iter++;

			if (perf) {
				timerB.start();
				result = alg.execute();
				timerB.stop();
			} else {
				timerA.start();
				int[] result = bruteForceMatching();
				timerA.stop();
				float sum = 0;
				//Log.raw("BruteForce %s", timerA.human());
				for (int i = 0; i < n; i++) {
					//Log.raw("%d -> %d [%d]", result[i], i, cost[result[i]][i]);
					sum = add(sum, cost[result[i]][i]);
				}
				//Log.raw("sum %d", sum);
				float brute = sum;

				timerB.start();
				result = alg.execute();
				timerB.stop();
				sum = 0;
				//Log.raw("Hungarian %s", timerB.human());
				for (int i = 0; i < n; i++) {
					//Log.raw("%d -> %d [%d]", result[i], i, cost[result[i]][i]);
					sum = add(sum, cost[result[i]][i]);
				}
				//Log.raw("sum %d", sum);
				float hung = sum;

				if ((brute == Inf && hung != Inf) || (brute != Inf && Math.abs(1 - brute / hung) > 0.001))
					throw new Error();
				//Log.raw("");
			}
		}
	}
}