package xyz.sathro.vulkan.renderer;

import lombok.Getter;
import xyz.sathro.factory.util.Timer;

public class Time {
	private static final Timer deltaTimer = new Timer();

	@Getter static double lastDeltaTime = 0;
	@Getter static double lastUnscaledDeltaTime = 0;

	public static double getDeltaTime() {
		return deltaTimer.getElapsedTime() * MainRenderer.MS_PER_UPDATE_INV;
	}

	public static double getUnscaledDeltaTime() {
		return deltaTimer.getElapsedTime();
	}

	static void updateTimer() {
		deltaTimer.reset();
	}
}
