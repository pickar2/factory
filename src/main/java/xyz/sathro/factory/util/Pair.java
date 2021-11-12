package xyz.sathro.factory.util;

import lombok.*;

@AllArgsConstructor(staticName = "of")
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Pair<K, V> {
	private K key;
	private V value;
}
