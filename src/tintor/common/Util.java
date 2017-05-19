package tintor.common;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class Util {
	public static FileChannel newTempFile() {
		return Util.checkIOException(() -> FileChannel.open(Files.createTempFile(null, null), StandardOpenOption.CREATE,
				StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
				StandardOpenOption.READ));
	}

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			throw new Error(e);
		}
	}

	public static boolean try_sleep(long millis) {
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			return false;
		}
	}

	public static void join(Thread thread) {
		try {
			thread.join();
		} catch (InterruptedException e) {
			throw new Error(e);
		}
	}

	public static void close(FileChannel file) {
		try {
			file.close();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	public static void read(FileChannel file, ByteBuffer buffer, long offset) {
		try {
			if (file.read(buffer, offset) != buffer.capacity())
				throw new Error();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	public static void write(FileChannel file, ByteBuffer buffer, long offset) {
		try {
			if (file.write(buffer, offset) != buffer.capacity())
				throw new Error();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	public static String human(long a) {
		final long K = 1000;
		final long M = 1000_000;
		final long B = 1000_000_000;
		if (a < 0)
			return "-" + human(-a);
		if (a < 10 * K)
			return String.format("%d", a);
		if (a < 10 * M)
			return String.format("%dK", (a + K / 2) / K);
		if (a < 10 * B)
			return String.format("%dM", (a + M / 2) / M);
		return String.format("%dB", (a + B / 2) / B);
	}

	public static int[] compressToIntArray(boolean[] b) {
		int[] a = new int[(b.length + 31) / 32];
		for (int i = 0; i < b.length; i++)
			if (b[i])
				Bits.set(a, i);
		return a;
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

	public static Scanner scanner(String filename) {
		return checkIOException(() -> new Scanner(new File(filename)));
	}

	public static int sum(int range, IntUnaryOperator fn) {
		int s = 0;
		for (int i = 0; i < range; i++)
			s += fn.applyAsInt(i);
		return s;
	}

	public static int count(int range, IntPredicate fn) {
		int count = 0;
		for (int i = 0; i < range; i++)
			if (fn.test(i))
				count += 1;
		return count;
	}

	public static boolean all(int range, IntPredicate fn) {
		for (int i = 0; i < range; i++)
			if (!fn.test(i))
				return false;
		return true;
	}

	public static boolean all(int start, int end, IntPredicate fn) {
		for (int i = start; i < end; i++)
			if (!fn.test(i))
				return false;
		return true;
	}

	public static boolean any(int range, IntPredicate fn) {
		for (int i = 0; i < range; i++)
			if (fn.test(i))
				return true;
		return false;
	}

	public static FileWriter openWriter(String file) {
		return Util.checkIOException(() -> new FileWriter(file, false));
	}

	public static void write(FileWriter writer, char[] buffer) {
		Util.checkIOException(() -> writer.write(buffer, 0, buffer.length));
	}

	public static void flush(FileWriter writer) {
		Util.checkIOException(() -> writer.flush());
	}

	public static interface IOExceptionFunction<T> {
		T call() throws IOException;
	}

	public static <T> T checkIOException(IOExceptionFunction<T> fn) {
		try {
			return fn.call();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	public static interface IOExceptionFunctionVoid {
		void call() throws IOException;
	}

	public static void checkIOException(IOExceptionFunctionVoid fn) {
		try {
			fn.call();
		} catch (IOException e) {
			throw new Error(e);
		}
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

	public static long roundUpPowerOf2(long v) {
		v--;
		v |= v >> 1;
		v |= v >> 2;
		v |= v >> 4;
		v |= v >> 8;
		v |= v >> 16;
		v |= v >> 32;
		v++;
		return v;
	}
}