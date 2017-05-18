package tintor.common;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
public final class Pair<A, B> {
	public final A first;
	public final B second;
}