package xyz.sathro.factory.vulkan.utils;

import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import static java.util.Objects.requireNonNull;
import static org.lwjgl.assimp.Assimp.aiImportFileEx;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memUTF8;

public class AIFileLoader {
	private static final AIFileIO fileIo;
	private static final AIFileOpenProcI fileOpenProc;
	private static final AIFileCloseProcI fileCloseProc;

	static {
		fileIo = AIFileIO.create();
		fileOpenProc = new AIFileOpenProc() {
			public long invoke(long pFileIO, long fileName, long openMode) {
				final AIFile aiFile = AIFile.create();
				final InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(memUTF8(fileName));
				final ReadableByteChannel rbc = Channels.newChannel(requireNonNull(inputStream));

				AIFileReadProcI fileReadProc = new AIFileReadProc() {
					public long invoke(long pFile, long pBuffer, long size, long count) {
//						System.out.println("size = " + size + ", count = " + count);
						long bytes = 0;
						try {
							bytes = rbc.read(memByteBuffer(pBuffer, (int) (size * count)));
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
//						System.out.println("bytes = " + bytes);
						if (bytes == -1) {
							bytes = 0;
						}
						return bytes;
					}
				};
				AIFileSeekI fileSeekProc = new AIFileSeek() {
					public int invoke(long pFile, long offset, int origin) {
//						System.out.println("offset = " + offset + ", origin = " + origin);
						return 0;
					}
				};
				AIFileTellProcI fileTellProc = new AIFileTellProc() {
					public long invoke(long pFile) {
						return -1;
					}
				};
				aiFile.ReadProc(fileReadProc);
				aiFile.SeekProc(fileSeekProc);
				aiFile.FileSizeProc(fileTellProc);

				return aiFile.address();
			}
		};
		fileCloseProc = new AIFileCloseProc() {
			public void invoke(long pFileIO, long pFile) {
				/* Nothing to do */
			}
		};
		fileIo.set(fileOpenProc, fileCloseProc, MemoryUtil.NULL);
	}

	public static AIScene readFile(String resource, int flags) {
		return aiImportFileEx(resource, flags, fileIo);
	}
}
