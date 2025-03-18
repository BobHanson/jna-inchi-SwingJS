package com.sun.jna;

import java.util.HashMap;
import java.util.Map;

public class NativeLibrary {
	private static Map<String, NativeLibrary> map = new HashMap<>();
	private String name;

	public NativeLibrary(String name) {
		this.name = name;
	}

	public static NativeLibrary getInstance(String name) {
		NativeLibrary instance = map.get(name);
		if (instance == null)
			map.put(name, instance = new NativeLibrary(name));
		return instance;
	}

	public String getName() {
		return name;
	}

}
