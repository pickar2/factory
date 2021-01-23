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
}
