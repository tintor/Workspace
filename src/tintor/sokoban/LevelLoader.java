package tintor.sokoban;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tintor.common.Util;

class LevelLoader {
	private static String preprocess(String line) {
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c != Code.Box && c != Code.Space && c != Code.Wall && c != Code.BoxGoal && c != Code.AgentGoal
					&& c != Code.Goal && c != Code.Agent)
				return "";
		}
		return line;
	}

	static int numberOfLevels(String filename) {
		int currentLevelNo = 0;
		boolean inside = false;
		Scanner sc = Util.scanner("data/sokoban/" + filename);
		while (sc.hasNextLine()) {
			String s = preprocess(sc.nextLine());
			if (!inside && !s.isEmpty()) {
				currentLevelNo += 1;
				inside = true;
			} else if (s.isEmpty())
				inside = false;
		}
		return currentLevelNo;
	}

	static final Pattern level_suffix = Pattern.compile("(.+):(\\d+)");

	static ArrayList<String> loadLevelLines(String filename) {
		int desiredLevelNo = 1;
		Matcher m = level_suffix.matcher(filename);
		if (m.matches()) {
			filename = m.group(1);
			desiredLevelNo = Integer.parseInt(m.group(2));
		}

		ArrayList<String> lines = new ArrayList<String>();
		int currentLevelNo = 0;
		boolean inside = false;
		Scanner sc = Util.scanner("data/sokoban/" + filename);
		while (sc.hasNextLine()) {
			String s = preprocess(sc.nextLine());
			if (!inside && !s.isEmpty()) {
				currentLevelNo += 1;
				inside = true;
			} else if (s.isEmpty())
				inside = false;

			if (inside && currentLevelNo == desiredLevelNo)
				lines.add(s);
		}
		return lines;
	}

	static char[] load(String filename) {
		final ArrayList<String> lines = loadLevelLines(filename);
		int w = 0;
		for (String s : lines)
			w = Math.max(w, s.length());
		int cells = lines.size() * (w + 1);
		char[] buffer = new char[cells];
		for (int row = 0; row < lines.size(); row++) {
			String s = lines.get(row);
			for (int col = 0; col < w; col++)
				buffer[row * (w + 1) + col] = col < s.length() ? s.charAt(col) : Code.Space;
			buffer[row * (w + 1) + w] = '\n';
		}
		return buffer;
	}
}