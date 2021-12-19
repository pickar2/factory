package xyz.sathro.factory.util;

public class Timer {
	private long lastLoopTime;

	public Timer() {
		lastLoopTime = System.nanoTime();
	}

	public static double toMs(long value) {
		return value / 1_000_000D;
	}

	public double getElapsedTimeAndReset() {
		final long time = System.nanoTime();
		final long elapsedTime = time - lastLoopTime;
		lastLoopTime = time;
		return toMs(elapsedTime);
	}

	public void reset() {
		lastLoopTime = System.nanoTime();
	}

	public double getElapsedTime() {
		return toMs(System.nanoTime() - lastLoopTime);
	}

	public double getLastLoopTime() {
		return toMs(lastLoopTime);
	}

	@Override
	public String toString() {
		return getElapsedTimeAndReset() + "";
	}
}
