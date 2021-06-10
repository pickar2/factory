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

	@SuppressWarnings("ShiftOutOfRange")
	public long get(int index) {
		final int bits = bits(index);
		final int storageIndex = storageIndex(bits);
		final int bitIndexInv = 64 - bitIndex(bits);
		final int bitIndexInvMinusStride = bitIndexInv - stride;

		if (bitIndexInvMinusStride >= 0) {
			return (this.storage[storageIndex] & mask << bitIndexInvMinusStride) >> bitIndexInvMinusStride & mask;
		} else {
			return (this.storage[storageIndex] & (ONES ^ ONES << bitIndexInv)) << stride - bitIndexInv & (mask ^ mask >> bitIndexInv) |
			       (this.storage[storageIndex + 1] & mask << bitIndexInvMinusStride) >> bitIndexInvMinusStride & mask >> bitIndexInv;
		}
	}

	@SuppressWarnings("ShiftOutOfRange")
	public long get(int x, int y, int z) {
		final int bits = bits(x, y, z);
		final int storageIndex = storageIndex(bits);
		final int bitIndexInv = 64 - bitIndex(bits);
		final int bitIndexInvMinusStride = bitIndexInv - stride;

		if (bitIndexInvMinusStride >= 0) {
			return (this.storage[storageIndex] & mask << bitIndexInvMinusStride) >> bitIndexInvMinusStride & mask;
		} else {
			return (this.storage[storageIndex] & (ONES ^ ONES << bitIndexInv)) << stride - bitIndexInv & (mask ^ mask >> bitIndexInv) |
			       (this.storage[storageIndex + 1] & mask << bitIndexInvMinusStride) >> bitIndexInvMinusStride & mask >> bitIndexInv;
		}
	}

	@SuppressWarnings("ShiftOutOfRange")
	public void set(int x, int y, int z, long value) {
		final int bits = bits(x, y, z);
		final int storageIndex = storageIndex(bits);
		final int bitIndexInv = 64 - bitIndex(bits);
		final int bitIndexInvMinusStride = bitIndexInv - stride;

		value &= mask; // clamp value

		if (bitIndexInvMinusStride >= 0) {
			this.storage[storageIndex] = this.storage[storageIndex] & (ONES ^ mask << bitIndexInvMinusStride) | value << bitIndexInvMinusStride;
		} else {
			this.storage[storageIndex] = this.storage[storageIndex] & ONES << bitIndexInv | value >> -bitIndexInvMinusStride;
			this.storage[storageIndex + 1] = this.storage[storageIndex + 1] & (ONES ^ mask << bitIndexInvMinusStride) | value << bitIndexInvMinusStride;
		}
	}
}
