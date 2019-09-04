package com.vincan.reverser;

/**
 * {@link StringBuffer#reverse()}
 * 
 * @author wencanyang
 *
 */
public class StringBuilderReverser implements IStringReverser {

	@Override
	public String reverse(String input) {
		if (input == null || input.length() <= 1) {
			return input;
		}
		return new StringBuilder(input).reverse().toString();
	}

}
