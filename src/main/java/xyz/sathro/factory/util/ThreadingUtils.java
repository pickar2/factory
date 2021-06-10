package xyz.sathro.factory.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadingUtils {
	private static final ExecutorService generalThreadPool = Executors.newCachedThreadPool();

	private ThreadingUtils() { }

	public static void addTask(Runnable task) {
		generalThreadPool.execute(task);
	}
}
