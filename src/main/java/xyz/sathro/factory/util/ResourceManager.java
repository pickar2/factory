package xyz.sathro.factory.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBIIOCallbacks;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.jni.JNINativeInterface;
import xyz.sathro.factory.logger.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;

import static java.lang.ClassLoader.getSystemClassLoader;
import static java.util.Objects.requireNonNull;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;

public class ResourceManager {
	private static final String jar = "jar";
	public static final STBIIOCallbacks stbiIOCallbacks;

	static {
		stbiIOCallbacks = STBIIOCallbacks.calloc();
		stbiIOCallbacks.read((user, data, size) -> {
//			System.out.println("read: size = " + size + ", data = " + data + ", user = " + user);
			int bytes;
			try {
				bytes = MemoryUtil.<ReadableByteChannel>memGlobalRefToObject(user).read(memByteBuffer(data, size));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
//			System.out.println("read: bytes = " + bytes);
			if (bytes == -1) {
				JNINativeInterface.DeleteGlobalRef(user);
				bytes = 0;
			}
			return bytes;
		});
		stbiIOCallbacks.skip((user, size) -> {
//			System.out.println("skip: size = " + size + ", user = " + user);
		});
		stbiIOCallbacks.eof((user) -> {
//			System.out.println("eof : user = " + user);
			return 0;
		});
	}

	private ResourceManager() { }

	public static InputStream getStreamFromString(@NotNull String path) {
		final InputStream stream = getSystemClassLoader().getResourceAsStream(path);

		if (stream == null) {
			Logger.instance.error("Can't find file " + path, new FileNotFoundException());
		}

		return stream;
	}

	public static Path getPathFromString(String path) {
		try {
			final URI uri = requireNonNull(getSystemClassLoader().getResource(path)).toURI();

			if (jar.equals(uri.getScheme())) {
				for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
					if (provider.getScheme().equalsIgnoreCase(jar)) {
						try {
							provider.getFileSystem(uri);
						} catch (FileSystemNotFoundException e) {
							provider.newFileSystem(uri, Collections.emptyMap());
						}

						final Path path1 = provider.getFileSystem(uri).getPath(path);

						return path1.toAbsolutePath();
					}
				}
			}

			return Paths.get(uri);
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public static @Nullable ByteBuffer stringToByteBuffer(String path) {
		try (InputStream stream = ResourceManager.getStreamFromString(path)) {
			final ReadableByteChannel rbc = Channels.newChannel(stream);

			final ByteBuffer memoryBuffer = MemoryUtil.memAlloc(stream.available());
			rbc.read(memoryBuffer);
			memoryBuffer.flip();

			return memoryBuffer;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public static void dispose() {
		stbiIOCallbacks.skip().free();
		stbiIOCallbacks.eof().free();
		stbiIOCallbacks.read().free();
		stbiIOCallbacks.free();
	}
}
