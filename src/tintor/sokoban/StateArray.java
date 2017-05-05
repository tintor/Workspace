package tintor.sokoban;

import java.util.Arrays;

import tintor.common.ArrayUtil;

final class StateArray {
	private short[] agent_array = ArrayUtil.EmptyShortArray;
	private int[] boxes_array = ArrayUtil.EmptyIntArray;
	private int size;

	int size() {
		return size;
	}

	void push(StateKey s) {
		int N = s.box.length;
		if (size >= agent_array.length) {
			agent_array = Arrays.copyOf(agent_array, Math.max(4, agent_array.length / 2 * 3));
			boxes_array = Arrays.copyOf(boxes_array, Math.max(4 * N, boxes_array.length / 2 * 3));
		}
		for (int i = 0; i < N; i++)
			boxes_array[size * N + i] = s.box[i];
		assert s.agent <= 65536;
		agent_array[size] = (short) s.agent;
		size += 1;
	}

	StateKey pop() {
		size -= 1;
		int N = boxes_array.length / agent_array.length;
		int[] box = new int[N];
		for (int i = 0; i < N; i++)
			box[i] = boxes_array[size * N + i];
		return new StateKey(((int) agent_array[size]) & 0xFFFF, box);
	}
}