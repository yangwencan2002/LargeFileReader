package com.vincan.reverser;

/**
 * 位异或
 * 
 * @author wencanyang
 *
 */
public class XORReverser implements IStringReverser {

	@Override
	public String reverse(String input) {
		if (input == null || input.length() <= 1) {
			return input;
		}
		char[] str = input.toCharArray();
		int begin = 0;
		int end = input.length() - 1;
		while (begin < end) {
			str[begin] = (char) (str[begin] ^ str[end]);
			str[end] = (char) (str[begin] ^ str[end]);
			str[begin] = (char) (str[end] ^ str[begin]);
			begin++;
			end--;
		}
		return new String(str);
	}

}
