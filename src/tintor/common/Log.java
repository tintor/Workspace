package tintor.common;

import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

public class Log {
	static enum Level {
		ALL, FINE, INFO, WARNING, ERROR, OFF
	}

	private static long start_millis = System.currentTimeMillis();
	public static Level level = Level.ALL;
	public static boolean raw = false;

	private static void log(Level level, String format, Object... args) {
		if (Log.level.ordinal() > level.ordinal())
			return;

		if (raw) {
			System.out.printf(format + "\n", args);
			return;
		}

		StackTraceElement frame = callerFrame();
		long millis = System.currentTimeMillis() - start_millis;

		String cn = frame.getClassName();
		int i = cn.lastIndexOf('.');
		if (i != -1)
			cn = cn.substring(i + 1, cn.length());

		System.out.printf("%d.%03d %s:%d %s %s\n", millis / 1000, millis % 1000, cn, frame.getLineNumber(),
				level.name().charAt(0), String.format(format, args));
	}

	private static StackTraceElement callerFrame() {
		JavaLangAccess access = SharedSecrets.getJavaLangAccess();
		Throwable throwable = new Throwable();
		int depth = access.getStackTraceDepth(throwable);
		boolean lookingForLogger = true;
		for (int ix = 0; ix < depth; ix++) {
			// Calling getStackTraceElement directly prevents the VM
			// from paying the cost of building the entire stack frame.
			StackTraceElement frame = access.getStackTraceElement(throwable, ix);

			if (lookingForLogger) {
				if (frame.getClassName().equals(Log.class.getName()))
					lookingForLogger = false;
			} else {
				if (!frame.getClassName().equals(Log.class.getName()))
					return frame;
			}
		}
		return null;
	}

	public static void fine(String format, Object... args) {
		log(Level.FINE, format, args);
	}

	public static void info(String format, Object... args) {
		log(Level.INFO, format, args);
	}

	public static void warning(String format, Object... args) {
		log(Level.WARNING, format, args);
	}

	public static void error(String format, Object... args) {
		log(Level.ERROR, format, args);
	}
}
