package xyz.sathro.factory.event;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import xyz.sathro.factory.util.Timer;
import xyz.sathro.factory.window.Window;
import xyz.sathro.vulkan.renderer.MainRenderer;

public class TickScheduler {
	private static final Object sync = new Object();
	@SuppressWarnings("unchecked")
	private static final ObjectArrayList<Runnable>[] tasks = new ObjectArrayList[MainRenderer.FRAMES_PER_SECOND];
	private static int counter = 0;

	static {
		for (int i = 0; i < MainRenderer.FRAMES_PER_SECOND; i++) {
			tasks[i] = new ObjectArrayList<>();
		}
	}

	private TickScheduler() { }

	public static void main(String[] args) {
		scheduleTask(5, ()-> System.out.println("Every 5 ticks"));

		scheduleOneTimeTask(14, ()-> System.out.println("On first tick 15"));

		loop();
	}

	public static void scheduleTask(int ticks, Runnable runnable) {
		if (ticks <= 0 || ticks > MainRenderer.FRAMES_PER_SECOND) {
			throw new IllegalArgumentException();
		}
		synchronized (sync) {
			final int count = ticks;
			while (ticks <= MainRenderer.FRAMES_PER_SECOND) {
				tasks[ticks - 1].add(runnable);
				ticks += count;
			}
		}
	}

	public static void scheduleOneTimeTask(int waitTicks, Runnable runnable) {
		synchronized (sync) {
			final int tick = (counter + waitTicks) % MainRenderer.FRAMES_PER_SECOND;
			tasks[tick].add(runnable);
			tasks[tick].add(() -> tasks[tick].remove(runnable));
		}
	}

	private static void loop() {
		double lag = 0.0;
		Timer timer = new Timer();
		while (!Window.shouldClose) {
			double elapsed = timer.getElapsedTimeAndReset();
			lag += elapsed;

			if (lag >= MainRenderer.MS_PER_UPDATE) {
				tick();
				lag = 0;
			}
		}
	}

	private static void tick() {
		synchronized (sync) {
			System.out.printf("Executing tick %d%n", counter + 1);
			for (Runnable runnable : tasks[counter]) {
				runnable.run();
			}
		}

		if (++counter == MainRenderer.FRAMES_PER_SECOND) {
			counter = 0;
		}
	}
}
