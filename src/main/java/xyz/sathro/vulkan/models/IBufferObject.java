package xyz.sathro.vulkan.models;

import java.nio.ByteBuffer;

public interface IBufferObject {
	/**
	 * @return Size of the BufferObject in bytes.
	 */
	int sizeof();

	/**
	 * Store this BufferObject into the supplied {@link ByteBuffer} starting at the specified
	 * absolute buffer position/index.
	 * <p>
	 * Implementation should not increment buffer's position.
	 *
	 * @param index  the absolute position into the {@link ByteBuffer}
	 * @param buffer will receive the values of this BufferObject
	 */
	void get(int index, ByteBuffer buffer);

	/**
	 * Store this BufferObject into the supplied {@link ByteBuffer} starting at the current buffer position.
	 * <p>
	 * Increments {@link ByteBuffer} position by size of this BufferObject.
	 *
	 * @param buffer will receive the values of this BufferObject
	 */
	default void get(ByteBuffer buffer) {
		get(buffer.position(), buffer);
		buffer.position(buffer.position() + sizeof());
	}
}
