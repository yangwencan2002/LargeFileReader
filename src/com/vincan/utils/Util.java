package com.vincan.utils;

/**
 * 工具类
 * @author wencanyang
 *
 */
public final class Util {

	private Util() {

	}

	public static boolean isEmpty(CharSequence cs) {
		if (cs == null || cs.length() == 0) {
			return true;
		}
		return false;
	}
}
