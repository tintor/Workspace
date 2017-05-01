package tintor.common;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Stack;

public class InstrumentationAgent {
	private static Instrumentation instrumentation;

	public static void premain(String agentArgs, Instrumentation instrumentation) {
		InstrumentationAgent.instrumentation = instrumentation;
	}

	public static long sizeOf(Object obj) {
		return isSharedFlyweight(obj) ? 0 : instrumentation.getObjectSize(obj);
	}

	public static long deepSizeOf(Object obj) {
		Map<Object, Object> visited = new IdentityHashMap<Object, Object>();
		Stack<Object> stack = new Stack<Object>();
		stack.push(obj);
		long result = 0;
		while (!stack.isEmpty())
			result += internalSizeOf(stack.pop(), stack, visited);
		return result;
	}

	private static boolean isSharedFlyweight(Object obj) {
		// optimization - all of our fly weights are Comparable
		if (obj instanceof Comparable) {
			if (obj instanceof Enum)
				return true;
			if (obj instanceof String)
				return (obj == ((String) obj).intern());
			if (obj instanceof Boolean)
				return (obj == Boolean.TRUE || obj == Boolean.FALSE);
			if (obj instanceof Integer)
				return (obj == Integer.valueOf((Integer) obj));
			if (obj instanceof Short)
				return (obj == Short.valueOf((Short) obj));
			if (obj instanceof Byte)
				return (obj == Byte.valueOf((Byte) obj));
			if (obj instanceof Long)
				return (obj == Long.valueOf((Long) obj));
			if (obj instanceof Character)
				return (obj == Character.valueOf((Character) obj));
		}
		return false;
	}

	private static long internalSizeOf(Object obj, Stack<Object> stack, Map<Object, Object> visited) {
		if (obj == null || visited.containsKey(obj) || isSharedFlyweight(obj))
			return 0;

		Class<?> clazz = obj.getClass();
		if (clazz.isArray()) {
			if (!clazz.getComponentType().isPrimitive())
				for (int i = 0; i < Array.getLength(obj); i++)
					stack.add(Array.get(obj, i));
		} else {
			// add all non-primitive fields to the stack
			while (clazz != null) {
				for (Field field : clazz.getDeclaredFields())
					if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
						field.setAccessible(true);
						try {
							stack.add(field.get(obj));
						} catch (IllegalAccessException ex) {
							throw new RuntimeException(ex);
						}
					}
				clazz = clazz.getSuperclass();
			}
		}
		visited.put(obj, null);
		return sizeOf(obj);
	}
}