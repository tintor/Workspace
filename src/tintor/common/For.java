package tintor.common;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class For {
	public interface IntIntPredicate {
		boolean test(int value, int value2);
	}

	public static boolean all(int sizeA, int sizeB, IntIntPredicate fn) {
		for (int i = 0; i < sizeA; i++)
			for (int j = 0; j < sizeB; j++)
				if (!fn.test(i, j))
					return false;
		return true;
	}

	public static boolean any(int sizeA, int sizeB, IntIntPredicate fn) {
		for (int i = 0; i < sizeA; i++)
			for (int j = 0; j < sizeB; j++)
				if (fn.test(i, j))
					return true;
		return false;
	}

	public static boolean all(int size, IntPredicate fn) {
		for (int i = 0; i < size; i++)
			if (!fn.test(i))
				return false;
		return true;
	}

	public static boolean any(int size, IntPredicate fn) {
		for (int i = 0; i < size; i++)
			if (fn.test(i))
				return true;
		return false;
	}

	public static <T> boolean all(T[] array, Predicate<T> fn) {
		for (T e : array)
			if (!fn.test(e))
				return false;
		return true;
	}

	public static boolean any(int[] array, IntPredicate fn) {
		for (int e : array)
			if (fn.test(e))
				return true;
		return false;
	}

	public static <T> boolean any(T[] array, Predicate<T> fn) {
		for (T e : array)
			if (fn.test(e))
				return true;
		return false;
	}

	public interface ForEachInt {
		void call(int index, int element);
	}

	public static void each(int[] array, ForEachInt fn) {
		for (int i = 0; i < array.length; i++)
			fn.call(i, array[i]);
	}

	public static void each(int[] array, IntConsumer fn) {
		for (int e : array)
			fn.accept(e);
	}

	public static <T> void each(T[] array, Consumer<T> fn) {
		for (T e : array)
			fn.accept(e);
	}
}
