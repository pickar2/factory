package xyz.sathro.factory.util;

import org.joml.Matrix3d;
import org.joml.Vector3d;

public class Maths {
	public static final float PI = (float) Math.PI;
	public static final float PIOver2 = (float) (Math.PI / 2);
	private static final double log2 = Math.log(2);

	public static int log2(int number) {
		return getMinBitCount(number) - 1;
	}

	public static double log2(double number) {
		return Math.log(number) / log2;
	}

	public static int clamp(int number, int min, int max) {
		return Math.max(min, Math.min(number, max));
	}

	public static float clamp(float number, float min, float max) {
		return Math.max(min, Math.min(number, max));
	}

	public static double clamp(double number, double min, double max) {
		return Math.max(min, Math.min(number, max));
	}

	public static int getMinBitCount(int integer) {
		int a = integer >> 16;
		int b = 0;
		if (a != 0) {
			b += 16;
			integer = a;
		}
		a = integer >> 8;
		if (a != 0) {
			b += 8;
			integer = a;
		}
		a = integer >> 4;
		if (a != 0) {
			b += 4;
			integer = a;
		}
		a = integer >> 2;
		if (a != 0) {
			b += 2;
			integer = a;
		}
		a = integer >> 1;
		if (a != 0) {
			return b + 2;
		}
		return b - integer + 2;
	}

	public static int floor(double number) {
		int xi = (int) number;
		return number < xi ? xi - 1 : xi;
	}

	public static short min(short first, short second) {
		return first < second ? first : second;
	}

	public static short max(short first, short second) {
		return first > second ? first : second;
	}

	public static Matrix3d diagonalFromVector(Vector3d vector) {
		return new Matrix3d(vector.x, 0, 0, 0, vector.y, 0, 0, 0, vector.z);
	}

	public static int asTwoInt16(int first, int second) {
		return ((first << 16) & 0xFFFF0000) | (second & 0x0000FFFF);
	}

	public static int ceilDiv(double a, double b) {
		return (int) Math.ceil(a / b);
	}

	public static double cosDeg(double deg) {
		return Math.cos(Math.toRadians(deg));
	}

	public static float cosDeg(float deg) {
		return (float) Math.cos(Math.toRadians(deg));
	}

	public static double sinDeg(double deg) {
		return Math.sin(Math.toRadians(deg));
	}

	public static float sinDeg(float deg) {
		return (float) Math.sin(Math.toRadians(deg));
	}

	public static int intPow(int value, int power) {
		int ret = value;
		for (int i = 1; i < power; i++) {
			ret *= value;
		}

		return ret;
	}

	public static double round(double value, int digitsAfterPoint) {
		final double scale = intPow(10, digitsAfterPoint);
		return Math.round(value * scale) / scale;
	}

	public static String fixedPrecision(double value, int digits) {
		value = round(value, digits);
		final String str = String.valueOf(value);

		return str + "0".repeat(digits - str.length() + String.valueOf(Maths.floor(value)).length() + 1);
	}

	public static String fixedNumberSize(String number, int size) {
		if (number.length() > size + 1) {
			return number.substring(0, size + 1);
		} else {
			return "0".repeat(size - number.length() + 1) + number;
		}
	}

	public static double timeToDeltaMs(long startTime) {
		return (System.nanoTime() - startTime) / 1_000_000D;
	}

	public static String timeToDeltaMs(long startTime, int digits) {
		return fixedPrecision(timeToDeltaMs(startTime), digits);
	}
}
