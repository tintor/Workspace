package tintor.sokoban;

import java.util.Arrays;

import tintor.common.Array;

final class StateArray {
	private short[] agent_array = Array.EmptyShortArray;
	private int[] boxes_array = Array.EmptyIntArray;
	private int size;
	int garbage;

	int size() {
		return size;
	}

	void push(StateKey s) {
		int N = s.box.length;
		if (size >= agent_array.length) {
			agent_array = Arrays.copyOf(agent_array, Math.max(4, agent_array.length / 2 * 3));
			boxes_array = Arrays.copyOf(boxes_array, N * agent_array.length);
		}
		for (int i = 0; i < N; i++)
			boxes_array[size * N + i] = s.box[i];
		assert s.agent < 65536;
		agent_array[size] = (short) s.agent;
		size += 1;
	}

	StateKey pop() {
		size -= 1;
		int N = boxes_array.length / agent_array.length;
		int[] box = new int[N];
		for (int i = 0; i < N; i++)
			box[i] = boxes_array[size * N + i];
		return new StateKey((int) agent_array[size] & 0xFFFF, box);
	}

	void remove_if(StateKeyPredicate fn) {
		int N = boxes_array.length / agent_array.length;
		for (int i = 0; i < size; i++)
			if (fn.test(agent_array[i], boxes_array, i * N)) {
				size -= 1;
				agent_array[i] = agent_array[size];
				Array.copy(boxes_array, size * N, boxes_array, i * N, N);
				i -= 1;
			}
	}
}