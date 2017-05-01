package tintor.common;

public interface IHashSet<T> extends Iterable<T> {
	int size();

	int capacity();

	void clear();

	boolean contains(T s);

	T get(T s);

	boolean set(T s);

	boolean insert(T s);

	boolean update(T s);

	boolean remove(T s);

	interface Remover<T> {
		T remove();
	}

	Remover<T> remover();
}