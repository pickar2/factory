package xyz.sathro.factory.test.xpbd;

import org.lwjgl.glfw.GLFW;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.test.xpbd.events.PhysicsUpdateEvent;
import xyz.sathro.factory.util.AveragedTimer;
import xyz.sathro.factory.util.Timer;
import xyz.sathro.factory.window.Window;
import xyz.sathro.factory.window.events.KeyDownEvent;

public class PhysicsController {
	private static final AveragedTimer timer = new AveragedTimer(60);

	public static final int UPS = 60;
	public static final double UPS_INV = 1.0 / UPS;
	public static final double MS_PER_UPDATE = 1000.0 / UPS;
	public static final double MS_PER_UPDATE_INV = 1 / MS_PER_UPDATE;

	private static boolean isSimulating = true;
	private static boolean simulateOneFrame = false;

	public static void physicsUpdateLoop() {
		EventManager.registerListeners(PhysicsController.class);
		double lag = 0.0;
		final Timer timer = new Timer();

		while (!Window.shouldClose) {
			lag += timer.getElapsedTimeAndReset();
			if (lag >= MS_PER_UPDATE) {
				PhysicsController.timer.startRecording();

				if (isSimulating || simulateOneFrame) {
					simulateOneFrame = false;
					EventManager.callEvent(new PhysicsUpdateEvent());
				}

				PhysicsController.timer.endRecording();

				lag -= MS_PER_UPDATE;
			}
		}
	}

	@SubscribeEvent
	public static void onPhysicsUpdate(PhysicsUpdateEvent event) {
		PhysicsCompute.simulate();
	}

	@SubscribeEvent
	public static void onKeyPressed(KeyDownEvent event) {
		if (event.action == GLFW.GLFW_PRESS && event.key == GLFW.GLFW_KEY_P) {
			isSimulating = !isSimulating;
		}
		if (event.key == GLFW.GLFW_KEY_F) {
			simulateOneFrame = true;
		}
		if (event.action == GLFW.GLFW_PRESS && event.key == GLFW.GLFW_KEY_R) {
			PhysicsCompute.updateParticles();
		}
	}

	public static double getPhysicsTime() {
		return timer.getAverageTime();
	}
}
