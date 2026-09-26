package mc.nala.servux.jei.util;

import java.util.Collection;
import java.util.stream.Collectors;

public final class StringUtil {
	private StringUtil() {
	}

	public static String intsToString(Collection<Integer> indexes) {
		return indexes.stream()
			.sorted()
			.map(i -> Integer.toString(i))
			.collect(Collectors.joining(", "));
	}
}
