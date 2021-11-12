package xyz.sathro.factory.window;

import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.util.SettingsManager;
import xyz.sathro.factory.window.events.KeyDownEvent;
import xyz.sathro.factory.window.events.KeyUpEvent;
import xyz.sathro.vulkan.renderer.MainRenderer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
	public static String title = "Engine test";

	private static int width = 1280;
	private static int height = 720;

	public static long handle;

	public static boolean shouldClose = false;
	public static boolean cursorGrabbed = false;

	private static GLFWKeyCallback keyCallback;
	private static GLFWWindowSizeCallback windowSizeCallback;
	private static GLFWFramebufferSizeCallback framebufferSizeCallback;

	public static void update() {
		shouldClose = glfwWindowShouldClose(handle);
	}

	public static void init() {
		if (!glfwInit()) {
			throw new RuntimeException("Cannot initialize GLFW");
		}

		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
//		glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
//		glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);

		long monitor = glfwGetPrimaryMonitor();
		GLFWVidMode mode = glfwGetVideoMode(monitor);

		if (mode == null) {
			throw new RuntimeException("Cannot get video mode");
		}

		if (SettingsManager.fullScreen.get()) {
			handle = glfwCreateWindow(mode.width(), mode.height(), title, monitor, 0);
		} else {
			handle = glfwCreateWindow(width, height, title, 0, 0);
		}
//		glfwSetWindowOpacity(handle, 0);
//		glfwSetWindowMonitor(handle, monitor, 0, 0, mode.width(), mode.height(), 0);
		glfwSetWindowPos(handle, (mode.width() - width) / 2, (mode.height() - height) / 2);

		if (handle == NULL) {
			throw new RuntimeException("Cannot create window");
		}

		glfwSetKeyCallback(handle, keyCallback = new GLFWKeyCallback() {
			public void invoke(long window, int key, int scancode, int action, int mods) { //TODO: user keyboard input here
				if (action == GLFW_PRESS || action == GLFW_REPEAT) {
					EventManager.callEvent(new KeyDownEvent(key, action));
				} else if (action == GLFW_RELEASE) {
					EventManager.callEvent(new KeyUpEvent(key));
				}

				if (key == GLFW_KEY_LEFT_ALT) {
					if (action == GLFW_PRESS) {
						glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
						cursorGrabbed = false;
					} else {
						MouseInput.first = true;
						glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
						cursorGrabbed = true;
					}
				}
				if (action != GLFW_RELEASE) {
					return;
				}
				if (key == GLFW_KEY_ESCAPE) {
					glfwSetWindowShouldClose(window, true);
				}
			}
		});

		glfwSetWindowSizeCallback(handle, windowSizeCallback = new GLFWWindowSizeCallback() {
			public void invoke(long window, int width, int height) {
				if (width <= 0 || height <= 0) {
					return;
				}
				Window.width = width;
				Window.height = height;
			}
		});

		glfwShowWindow(handle);

		glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback = new GLFWFramebufferSizeCallback() {
			public void invoke(long window, int width, int height) {
//				System.out.println("RESIZE");
				MainRenderer.framebufferResize = true;
			}
		});

//		if (glfwGetWindowAttrib(handle, GLFW_HOVERED) == GLFW_TRUE) {
//			cursorGrabbed = true;
//			glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
//		} else {
		cursorGrabbed = false;
		glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
//		}
	}

	public static boolean isKeyPressed(int key) {
		return glfwGetKey(handle, key) == GLFW_PRESS;
	}

	public static void cleanup() {
		keyCallback.free();
		windowSizeCallback.free();
		framebufferSizeCallback.free();

		glfwDestroyWindow(handle);
		glfwTerminate();
	}
}
