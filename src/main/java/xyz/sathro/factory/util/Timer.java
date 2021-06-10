package xyz.sathro.factory.util;

public class Timer {
	private long lastLoopTime;

	public Timer() {
		lastLoopTime = System.nanoTime();
	}

	public static double toMs(long value) {
		return value / 1_000_000D;
	}

	public double getElapsedTime() {
		final long time = System.nanoTime();
		final long elapsedTime = time - lastLoopTime;
		lastLoopTime = time;
		return toMs(elapsedTime);
	}

	public double getLastLoopTime() {
		return toMs(lastLoopTime);
	}

	@Override
	public String toString() {
		return getElapsedTime() + "";
	}
}
