package xyz.sathro.factory.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Objects;

import static java.lang.ClassLoader.getSystemClassLoader;

public class PathUtils {
	public static Path fromString(String path) {
		try {
			final URI uri = Objects.requireNonNull(getSystemClassLoader().getResource(path)).toURI();

			if ("jar".equals(uri.getScheme())) {
				for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
					if (provider.getScheme().equalsIgnoreCase("jar")) {
						try {
							provider.getFileSystem(uri);
						} catch (FileSystemNotFoundException e) {
							provider.newFileSystem(uri, Collections.emptyMap());
						}
					}
				}
			}

			return Paths.get(uri);
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
}
