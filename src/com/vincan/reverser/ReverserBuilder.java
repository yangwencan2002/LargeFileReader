package com.vincan.reverser;

/**
 * Reverser组装器，采用策略模式
 * 
 * @author wencanyang
 *
 */
public final class ReverserBuilder {

	public static final int TYPE_STRINGBUILDER = 1;

	public static final int TYPE_XOR = 2;

	// 可能还有更多...

	public static IStringReverser buildReverser(int type) {
		IStringReverser stringReverser = null;
		switch (type) {
		case TYPE_STRINGBUILDER:
			stringReverser = new StringBuilderReverser();
			break;
		case TYPE_XOR:
			stringReverser = new XORReverser();
			break;
		default:
			stringReverser = new StringBuilderReverser();
			break;
		}
		return stringReverser;
	}
}
