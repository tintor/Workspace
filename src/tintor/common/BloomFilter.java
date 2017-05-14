package tintor.common;

import java.util.concurrent.ThreadLocalRandom;

public final class BloomFilter {
	public final int[] seed;
	public final int[][] bits;
	public final int mask;

	public BloomFilter(int capacity, int functions) {
		capacity = Util.roundUpPowerOf2(Math.max(32, capacity));
		ThreadLocalRandom rand = ThreadLocalRandom.current();
		seed = Array.ofInt(functions, i -> rand.nextInt());
		bits = new int[functions][capacity / 32];
		mask = capacity - 1;
	}

	public double load() {
		int m = 0;
		for (int func = 0; func < bits.length; func++)
			m = Math.max(m, Bits.count(bits[func]));
		return (double) m / bits[0].length;
	}

	public void add(int[] key) {
		for (int func = 0; func < bits.length; func++)
			Bits.set(bits[func], MurmurHash3.hash(key, seed[func]) & mask);
	}

	public boolean might_contain(int[] key) {
		for (int func = 0; func < bits.length; func++)
			if (!Bits.test(bits[func], MurmurHash3.hash(key, seed[func])))
				return false;
		return true;
	}
}