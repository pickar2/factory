package xyz.sathro.factory.vulkan.utils;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Collection;
import java.util.List;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.system.MemoryStack.stackGet;

public class BufferUtils {
	private BufferUtils() { }

	public static PointerBuffer asPointerBuffer(Collection<String> collection) {
		MemoryStack stack = stackGet();
		PointerBuffer buffer = stack.mallocPointer(collection.size());
		collection.stream().map(stack::UTF8).forEach(buffer::put);

		return buffer.rewind();
	}

	public static PointerBuffer asPointerBuffer(List<? extends Pointer> list) {
		MemoryStack stack = stackGet();
		PointerBuffer buffer = stack.mallocPointer(list.size());
		list.forEach(buffer::put);

		return buffer.rewind();
	}

	public static PointerBuffer asPointerBuffer(Pointer[] pointers) {
		MemoryStack stack = stackGet();
		PointerBuffer buffer = stack.mallocPointer(pointers.length);
		for (Pointer pointer : pointers) {
			buffer.put(pointer);
		}

		return buffer.rewind();
	}

	public static long[] toArray(LongBuffer buffer) {
		long[] ret = new long[buffer.remaining()];
		buffer.mark();
		buffer.get(ret);
		buffer.reset();

		return ret;
	}

	public static int[] toArray(IntBuffer buffer) {
		int[] ret = new int[buffer.remaining()];
		buffer.mark();
		buffer.get(ret);
		buffer.reset();

		return ret;
	}

	public static ByteBuffer resize(ByteBuffer buffer, int newCapacity) {
		ByteBuffer newBuffer = createByteBuffer(newCapacity);
		buffer.flip();
		newBuffer.put(buffer);

		return newBuffer;
	}
}
