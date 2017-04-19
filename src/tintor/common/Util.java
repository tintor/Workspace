package tintor.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class Util {
	public static String human(long a) {
		if (a <= 10000)
			return String.format("%d", a);
		if (a <= 10000000)
			return String.format("%dK", (a + 500) / 1000);
		return String.format("%dM", (a + 500000) / 1000000);
	}

	public static long compress(boolean[] b) {
		if (b.length > 64)
			throw new Error();
		long a = 0;
		for (int i = 0; i < b.length; i++)
			if (b[i])
				a |= 1l << i;
		return a;
	}

	public static long compress(boolean[] b, int part) {
		long a = 0;
		int e = Math.min(b.length - part * 64, 64);
		for (int i = 0; i < e; i++)
			if (b[i + part * 64])
				a |= 1l << i;
		return a;
	}

	public static void decompress(long a, int part, boolean[] b) {
		int e = Math.min(b.length - part * 64, 64);
		for (int i = 0; i < e; i++)
			b[i + part * 64] = (a & (1l << i)) != 0;
	}

	public static String toString(boolean[] v) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < v.length; i++) {
			if (!v[i])
				continue;
			if (b.length() > 0)
				b.append(' ');
			b.append(i);
		}
		return b.toString();
	}

	public static void updateOr(boolean[] a, boolean[] b) {
		assert a.length == b.length;
		for (int i = 0; i < a.length; i++)
			if (b[i])
				a[i] = true;
	}

	public static int count(boolean[] b) {
		int s = 0;
		for (boolean q : b) {
			if (q)
				s += 1;
		}
		return s;
	}

	public static BigInteger combinations(int a, int b) {
		BigInteger q = BigInteger.valueOf(a);
		for (int i = a - 1; i > a - b; i -= 1)
			q = q.multiply(BigInteger.valueOf(i));
		for (int i = 2; i <= b; i += 1)
			q = q.divide(BigInteger.valueOf(i));
		return q;
	}

	public static Scanner scanFile(String filename) {
		try {
			return new Scanner(new File(filename));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static interface IndexToInt {
		int operation(int index);
	}

	public static int sum(int range, IndexToInt g) {
		int s = 0;
		for (int i = 0; i < range; i++) {
			s += g.operation(i);
		}
		return s;
	}

	public static interface IndexToBool {
		boolean operation(int index);
	}

	public static int count(int range, IndexToBool g) {
		int count = 0;
		for (int i = 0; i < range; i++)
			if (g.operation(i))
				count += 1;
		return count;
	}

	public static boolean all(int range, IndexToBool p) {
		for (int i = 0; i < range; i++)
			if (!p.operation(i))
				return false;
		return true;
	}

	public static interface IndexToVoid {
		void operation(int index);
	}

	public static void for_each_true(boolean[] array, IndexToVoid f) {
		for (int i = 0; i < array.length; i++)
			if (array[i])
				f.operation(i);
	}

	public static int roundUpPowerOf2(int v) {
		v--;
		v |= v >> 1;
		v |= v >> 2;
		v |= v >> 4;
		v |= v >> 8;
		v |= v >> 16;
		v++;
		return v;
	}

	public static void shuffle(int[] array) {
		// Implementing Fisher–Yates shuffle
		Random rnd = ThreadLocalRandom.current();
		for (int i = array.length - 1; i > 0; i--) {
			int j = rnd.nextInt(i + 1);
			int a = array[j];
			array[j] = array[i];
			array[i] = a;
		}
	}
}