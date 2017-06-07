package tintor.common;

import java.lang.instrument.Instrumentation;

public interface DeepSizeOf {
	long deepSizeOf(Instrumentation instrumentation);
}