package tintor.common;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public final class Array {
	private Array() {
	}

	public static int[] clone(int[] array) {
		int[] narray = new int[array.length];
		copy(array, 0, narray, 0, array.length);
		return narray;
	}

	public static <T> T[] append(T[] array, T m) {
		array = Arrays.copyOf(array, array.length + 1);
		array[array.length - 1] = m;
		return array;
	}

	public static <T> T find(T[] array, Predicate<T> fn) {
		for (T e : array)
			if (fn.test(e))
				return e;
		return null;
	}

	public static <T> int count(T[] array, Predicate<T> fn) {
		int count = 0;
		for (T e : array)
			if (fn.test(e))
				count += 1;
		return count;
	}

	public static int[] concat(int[] a, int[] b) {
		int[] c = new int[a.length + b.length];
		copy(a, 0, c, 0, a.length);
		copy(b, 0, c, a.length, b.length);
		return c;
	}

	public static void copy(Object src, int srcPos, Object dest, int destPos, int length) {
		System.arraycopy(src, srcPos, dest, destPos, length);
	}

	public static boolean contains(int[] array, int value) {
		for (int a : array)
			if (a == value)
				return true;
		return false;
	}

	public static int sum(int[] array) {
		int sum = 0;
		for (int a : array)
			sum += a;
		return sum;
	}

	public static void for_each_true(boolean[] array, IntConsumer fn) {
		for (int i = 0; i < array.length; i++)
			if (array[i])
				fn.accept(i);
	}

	public static boolean[] ofBool(int size, IntPredicate fn) {
		boolean[] array = new boolean[size];
		for (int i = 0; i < size; i++)
			array[i] = fn.test(i);
		return array;
	}

	public static interface IntToIntFunction {
		int apply(int i);
	}

	public static int[] ofInt(int size, IntToIntFunction fn) {
		int[] array = new int[size];
		for (int i = 0; i < size; i++)
			array[i] = fn.apply(i);
		return array;
	}

	public static int[] ofInt(int size0, int value) {
		int[] array = new int[size0];
		Arrays.fill(array, value);
		return array;
	}

	public static int[][] ofInt(int size0, int size1, int value) {
		int[][] array = new int[size0][size1];
		for (int[] e : array)
			Arrays.fill(e, value);
		return array;
	}

	public static interface IntToTFunction<T> {
		T apply(int i);
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] make(int size, IntToTFunction<T> fn) {
		T first = fn.apply(0);
		T[] array = (T[]) java.lang.reflect.Array.newInstance(first.getClass(), size);
		array[0] = first;
		for (int i = 1; i < size; i++)
			array[i] = fn.apply(i);
		return array;
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

	// ==============

	public final static Object[] EmptyObjectArray = new Object[0];
	public final static long[] EmptyLongArray = new long[0];
	public final static byte[] EmptyByteArray = new byte[0];
	public final static int[] EmptyIntArray = new int[0];
	public final static short[] EmptyShortArray = new short[0];

	public static Object[] expand(Object[] array, int pos, int length) {
		Object[] narray = new Object[array.length + length];
		copy(array, 0, narray, 0, pos);
		copy(array, pos, narray, pos + length, array.length - pos);
		return narray;
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] create(T[] array, int length) {
		return (T[]) java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), length);
	}

	public static <T> T[] remove(T[] array, int pos) {
		return remove(array, pos, 1);
	}

	public static <T> T[] remove(T[] array, int pos, int length) {
		T[] narray = create(array, array.length - length);
		copy(array, 0, narray, 0, pos);
		copy(array, pos + length, narray, pos, array.length - pos - length);
		return narray;
	}

	public static int[] expand(int[] array, int pos, int length) {
		int[] narray = new int[array.length + length];
		copy(array, 0, narray, 0, pos);
		copy(array, pos, narray, pos + length, array.length - pos);
		return narray;
	}

	public static int[] remove(int[] array, int pos, int length) {
		if (array.length == length)
			return EmptyIntArray;
		int[] narray = new int[array.length - length];
		copy(array, 0, narray, 0, pos);
		copy(array, pos + length, narray, pos, array.length - pos - length);
		return narray;
	}

	public static byte[] expand(byte[] array, int pos, int length) {
		byte[] narray = new byte[array.length + length];
		copy(array, 0, narray, 0, pos);
		copy(array, pos, narray, pos + length, array.length - pos);
		return narray;
	}

	public static byte[] remove(byte[] array, int pos, int length) {
		if (array.length == length)
			return EmptyByteArray;
		byte[] narray = new byte[array.length - length];
		System.arraycopy(array, 0, narray, 0, pos);
		System.arraycopy(array, pos + length, narray, pos, array.length - pos - length);
		return narray;
	}

	public static boolean equals(byte[] first, int first_offset, byte[] second, int second_offset, int length) {
		for (int i = 0; i < length; i++)
			if (first[i + first_offset] != second[i + second_offset])
				return false;
		return true;
	}

	public static boolean equals(int[] first, int first_offset, int[] second, int second_offset, int length) {
		for (int i = 0; i < length; i++)
			if (first[i + first_offset] != second[i + second_offset])
				return false;
		return true;
	}
}
