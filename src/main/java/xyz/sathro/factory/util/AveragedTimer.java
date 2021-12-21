package xyz.sathro.factory.util;

import lombok.RequiredArgsConstructor;

import java.util.LinkedList;

@RequiredArgsConstructor
public class AveragedTimer {
	private final LinkedList<Long> timeList = new LinkedList<>();
	private final int size;

	private long startTime = 0;

	public void addTime(long timeDelta) {
		synchronized (timeList) {
			timeList.add(timeDelta);
			if (timeList.size() > size) {
				timeList.pop();
			}
		}
	}

	public void startRecording() {
		startTime = System.nanoTime();
	}

	public void endRecording() {
		addTime(System.nanoTime() - startTime);
	}

	public double getAverageTime() {
		synchronized (timeList) {
			return timeList.stream().reduce(0L, Long::sum) / 1_000_000D / timeList.size();
		}
	}
}
