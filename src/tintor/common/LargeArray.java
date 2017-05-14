package tintor.common;

import java.util.Arrays;

public final class LargeArray {
	// Shift is less than 31 to make it easier on the Garbage Collector
	private final static int Shift = 24;
	private final static int Mask = (1 << Shift) - 1;

	public static int[][] create(long length) {
		int[][] array = new int[(int) (length >> Shift)][];
		for (int i = 0; i < array.length; i++) {
			array[i] = new int[length > Mask ? Mask + 1 : (int) length];
			length -= Mask + 1;
		}
		return array;
	}

	public static boolean check(int[][] array) {
		for (int i = 0; i < array.length - 1; i++)
			if (array[i].length != Mask + 1)
				return false;
		return array.length == 0 || array[array.length - 1].length > 0;
	}

	public static long length(int[][] array) {
		if (array.length == 0)
			return 0;
		return (long) (array.length - 1) * array[0].length + array[array.length - 1].length;
	}

	public static int[][] resize(int[][] array, long length) {
		int a = (int) (length >> Shift);
		if (a != array.length)
			array = Arrays.copyOf(array, a);
		for (int i = 0; i < array.length; i++) {
			int b = length > Mask ? Mask + 1 : (int) length;
			length -= Mask + 1;
			if (array[i] == null) {
				array[i] = new int[b];
				continue;
			}
			if (b != array[i].length)
				array[i] = Arrays.copyOf(array[i], b);
		}
		return array;
	}

	public static int get(int[][] array, long index) {
		int a = (int) (index >> Shift);
		int b = (int) index & Mask;
		return array[a][b];
	}

	public static void set(int[][] array, long index, int value) {
		int a = (int) (index >> Shift);
		int b = (int) index & Mask;
		array[a][b] = value;
	}
}