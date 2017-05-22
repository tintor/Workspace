package tintor.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class Flags {
	private final static Map<String, Flag> flags = new HashMap<>();

	private static class Flag {
		Flag(String name) {
			flags.put(name, this);
		}

		void set(String arg) {
		}
	}

	public static class Text extends Flag {
		public String value;

		public Text(String name, String def) {
			super(name);
			value = def;
		}

		void set(String arg) {
			value = arg;
		}

		public String get() {
			return value;
		}
	}

	public static class Int extends Flag {
		public int value;

		public Int(String name, int def) {
			super(name);
			value = def;
		}

		void set(String arg) {
			try {
				value = Integer.parseInt(arg);
			} catch (NumberFormatException e) {
				throw new Error("expecting integer instead of [" + arg + "]");
			}
		}
	}

	public static class Real extends Flag {
		public double value;

		public Real(String name, double def) {
			super(name);
			value = def;
		}

		void set(String arg) {
			try {
				value = Long.parseLong(arg);
			} catch (NumberFormatException e) {
				throw new Error("expecting number instead of [" + arg + "]");
			}
		}
	}

	public static class Bool extends Flag {
		public boolean value;

		public Bool(String name, boolean def) {
			super(name);
			value = def;
		}

		void set(String arg) {
			if (!arg.equals("on") && !arg.equals("off"))
				throw new Error("expecting on/off instead of [" + arg + "]");
			value = arg.equalsIgnoreCase("on");
		}
	}

	public static String[] parse(String[] args, int min, int max) {
		Pattern pattern = Pattern.compile("-([0-9a-zA-Z_]+)(=(.+|'(.+)'))?");
		ArrayList<String> remaining = new ArrayList<>();
		for (String arg : args) {
			if (arg.equals("-help")) {
				for (Map.Entry<String, Flag> e : flags.entrySet()) {
					// TODO print default value of each flag
					Log.raw("%s - %s", e.getKey(), e.getValue().getClass().getSimpleName());
				}
				System.exit(0);
			}
			Matcher m = pattern.matcher(arg);
			if (!m.matches()) {
				remaining.add(arg);
				continue;
			}
			Flag flag = flags.get(m.group(1));
			if (flag == null)
				throw new Error("unknown option " + arg);
			if (m.group(2) == null) {
				if (flag instanceof Bool) {
					flag.set("on");
					continue;
				}
				throw new Error("expecting value for " + arg);
			}
			flag.set(m.group(4) != null ? m.group(4) : m.group(3));
		}
		if (remaining.size() < min)
			throw new Error("expecting at least " + min + "arg(s)");
		if (remaining.size() > max)
			throw new Error("expecting at most " + max + "arg(s)");
		return remaining.toArray(new String[remaining.size()]);
	}
}