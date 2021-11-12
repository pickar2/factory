package xyz.sathro.factory.test;

import xyz.sathro.factory.util.Maths;

public class CubicBitStorage {
	public static final long ONES = 0xFFFFFFFFFFFFFFFFL;
	public final long mask;
	public final int size;
	public final int sizeLog2;
	public final int doubleSizeLog2;
	public final int stride;

	public final long[] storage;

	public CubicBitStorage(int sideSize, int stride) {
		this.size = sideSize;
		this.sizeLog2 = Maths.log2(sideSize);
		this.doubleSizeLog2 = sizeLog2 << 1;
		this.stride = stride;
		this.mask = (1L << stride) - 1;

		this.storage = new long[(int) Math.ceil(stride * sideSize * sideSize * sideSize / 64d)];
	}

	// TODO: move this away
	public static String longToString(long value) {
		final StringBuilder builder = new StringBuilder();
		for (int i = 63; i >= 0; i--) {
			builder.append(value >> i & 1);
		}

		return builder.toString();
	}

	public int bits(int x, int y, int z) {
		return (z << doubleSizeLog2 | y << sizeLog2 | x) * stride;
	}

	public int bits(int index) {
		return index * stride;
	}

	public int storageIndex(int bits) {
		return bits >> 6;
	}

	public int bitIndex(int bits) {
		return bits & 63;
	}

	public long get(int index) {
		return getFromBits(bits(index));
	}

	public long get(int x, int y, int z) {
		return getFromBits(bits(x, y, z));
	}

	@SuppressWarnings("ShiftOutOfRange")
	private long getFromBits(int bits) {
		final int storageIndex = storageIndex(bits);
		final int bitIndexInv = 64 - bitIndex(bits);
		final int bitIndexInvMinusStride = bitIndexInv - stride;

		if (bitIndexInvMinusStride >= 0) {
			return (storage[storageIndex] & mask << bitIndexInvMinusStride) >> bitIndexInvMinusStride & mask;
		} else {
			return (storage[storageIndex] & (ONES ^ ONES << bitIndexInv)) << stride - bitIndexInv & (mask ^ mask >> bitIndexInv) |
			       (storage[storageIndex + 1] & mask << bitIndexInvMinusStride) >> bitIndexInvMinusStride & mask >> bitIndexInv;
		}
	}

	public void set(int index, long value) {
		setFromBits(bits(index), value);
	}

	public void set(int x, int y, int z, long value) {
		setFromBits(bits(x, y, z), value);
	}

	@SuppressWarnings("ShiftOutOfRange")
	private void setFromBits(int bits, long value) {
		final int storageIndex = storageIndex(bits);
		final int bitIndexInv = 64 - bitIndex(bits);
		final int bitIndexInvMinusStride = bitIndexInv - stride;

		value &= mask; // clamp value

		if (bitIndexInvMinusStride >= 0) {
			storage[storageIndex] = storage[storageIndex] & (ONES ^ mask << bitIndexInvMinusStride) | (value << bitIndexInvMinusStride);
		} else {
			storage[storageIndex] = storage[storageIndex] & ONES << bitIndexInv | value >> -bitIndexInvMinusStride;
			storage[storageIndex + 1] = storage[storageIndex + 1] & (ONES ^ mask << bitIndexInvMinusStride) | value << bitIndexInvMinusStride;
		}
	}
}
