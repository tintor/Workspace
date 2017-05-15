package tintor.common;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public class For {
	public static <T> boolean all(T[] array, Predicate<T> fn) {
		for (T e : array)
			if (!fn.test(e))
				return false;
		return true;
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
