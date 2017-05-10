package tintor.common;

public final class Prime {
	public final static int Near10k = 10007;
	public final static int Near100k = 100003;
	public final static int Near1M = 1000003;
	public final static int Near10M = 9999991;
	public final static int Near50M = 49999991;

	// Execution time:
	// 14 us for 50M
	// 21 us for 100M
	// 25 us for 200M
	// 71 us for 200M with long instead of int
	public static int primeNear(int a) {
		if (isPrime(a))
			return a;
		int d = (a % 2 == 0) ? 1 : 2;
		while (true) {
			if (isPrime(a + d))
				return a + d;
			if (isPrime(a - d))
				return a - d;
			d += 2;
		}
	}

	public static long primeBelow(long a) {
		if (isPrime(a))
			return a;
		int d = (a % 2 == 0) ? 1 : 2;
		while (true) {
			a -= d;
			if (isPrime(a))
				return a;
		}
	}

	public static boolean isPrime(long a) {
		if (a < 2)
			return false;
		if (a == 2)
			return true;
		if (a % 2 == 0)
			return false;
		long d = (long) Math.sqrt(a);
		for (int i = 3; i <= d; i += 2) {
			if (a % i == 0)
				return false;
		}
		return true;
	}

	public static boolean isPrime(int a) {
		if (a < 2)
			return false;
		if (a == 2)
			return true;
		if (a % 2 == 0)
			return false;
		int d = (int) Math.sqrt(a);
		for (int i = 3; i <= d; i += 2) {
			if (a % i == 0)
				return false;
		}
		return true;
	}

	public static void main(String[] argv) {
		int e = 16;
		while (true) {
			System.out.printf("%d,", primeBelow(e));
			if ((int) (e * 2.0) <= e)
				break;
			e = (int) (e * 2.0);
		}
	}
}