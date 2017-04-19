package tintor.common;

public interface ExternalMap {
	boolean contains(byte[] key);

	byte[] get(byte[] key);

	boolean put(byte[] key, byte[] value);
}