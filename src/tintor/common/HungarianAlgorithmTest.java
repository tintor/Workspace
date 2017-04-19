package tintor.common;

import java.util.Arrays;
import java.util.Random;

public class HungarianAlgorithmTest {
	static Timer timer = new Timer();

	public static void main(String[] args) {
		int n = 10;
		Random rand = new Random();
		HungarianAlgorithm hung = new HungarianAlgorithm(n, n);
		for (int i = 0; i < n; i++)
			for (int j = 0; j < n; j++)
				hung.cost[i][j] = rand.nextDouble();

		int[] result = null;
		try (Timer t = timer.start()) {
			for (int i = 0; i < 10000; i++) {
				result = hung.execute();
			}
		}
		timer.total /= 10000;
		System.out.println(Arrays.toString(result));
		System.out.println(timer.human());
	}
}