package tintor.sokoban;

import java.util.ArrayList;
import java.util.Scanner;

import tintor.common.Regex;
import tintor.common.Util;

class LevelIO {
	static ArrayList<String> loadLevelLines(String filename) {
		int levelNo = -1;
		if (Regex.matches(filename, "^(.*):(\\d+)$")) {
			filename = Regex.group(1);
			levelNo = Integer.parseInt(Regex.group(2));
		}

		ArrayList<String> lines = new ArrayList<String>();
		boolean inside = levelNo == -1;
		Scanner sc = Util.scanFile(filename);
		while (sc.hasNextLine()) {
			String s = sc.nextLine();
			if (s.startsWith("'") || s.startsWith(";"))
				continue;
			if (inside) {
				if (s.trim().isEmpty())
					break;
				lines.add(s);
				continue;
			}
			if (Regex.matches(s, "^Level (\\d+)$") && Integer.parseInt(Regex.group(1)) == levelNo) {
				inside = true;
			}
		}
		return lines;
	}

	LevelIO(String filename) {
		lines = loadLevelLines(filename);

		int w = 0;
		for (String s : lines)
			w = Math.max(w, s.length());

		width = w;
	}

	final static char Box = '$';
	final static char Wall = '#';
	final static char BoxGoal = '*';
	final static char AgentGoal = '+';
	final static char Goal = '.';
	final static char Agent = '@';
	final static char Space = ' ';

	public static interface IndexToChar {
		char fn(int index);
	}

	void print(IndexToChar ch) {
		for (int i = 0; i < lines.size(); i++) {
			for (int j = 0; j < width; j++) {
				char c = (j < lines.get(i).length()) ? lines.get(i).charAt(j) : Space;
				if (c != Space && c != Wall)
					c = Space;
				int pos = old_to_new[i * width + j];
				if (pos != -1)
					c = ch.fn(pos);
				System.out.print(c);
			}
			System.out.println();
		}
	}

	final ArrayList<String> lines;
	final int width;
	protected int[] old_to_new;
}