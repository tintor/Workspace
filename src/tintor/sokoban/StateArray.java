package tintor.sokoban;

import java.util.Arrays;

import tintor.common.ArrayUtil;

final class StateArray {
	private byte[] agent_array = ArrayUtil.EmptyByteArray;
	private int[] boxes_array = ArrayUtil.EmptyIntArray;
	private int size;

	void try_cleanup() {
		if (size == 0 && agent_array.length > 0) {
			agent_array = ArrayUtil.EmptyByteArray;
			boxes_array = ArrayUtil.EmptyIntArray;
		}
	}

	int size() {
		return size;
	}

	void push(State s, int[] boxes) {
		int N = boxes.length;
		if (size >= agent_array.length) {
			agent_array = Arrays.copyOf(agent_array, Math.max(4, agent_array.length / 2 * 3));
			boxes_array = Arrays.copyOf(boxes_array, Math.max(4 * N, boxes_array.length / 2 * 3));
		}

		switch (boxes.length) {
		case 4:
			boxes[3] = (int) (s.box1 >>> 32);
		case 3:
			boxes[2] = (int) s.box1;
		case 2:
			boxes[1] = (int) (s.box0 >>> 32);
		case 1:
			boxes[0] = (int) s.box0;
		}

		for (int i = 0; i < N; i++)
			boxes_array[size * N + i] = boxes[i];
		agent_array[size] = (byte) s.agent();
		size += 1;
	}

	int pop(int[] key) {
		size -= 1;
		int N = key.length;
		for (int i = 0; i < N; i++)
			key[i] = boxes_array[size * N + i];
		return ((int) agent_array[size]) & 0xFF;
	}
}