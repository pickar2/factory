package xyz.sathro.factory.window;

import org.joml.Vector2d;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFWCursorEnterCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.window.events.MouseClickEvent;
import xyz.sathro.factory.window.events.MouseDragEvent;
import xyz.sathro.factory.window.events.MouseMoveEvent;
import xyz.sathro.factory.window.events.MouseScrollEvent;

import static org.lwjgl.glfw.GLFW.*;

public class MouseInput {
	private static final Vector2d previousPos = new Vector2d(0, 0);
	private static final Vector2d currentPos = new Vector2d(0, 0);

	private static boolean leftButtonPressed = false;
	private static boolean rightButtonPressed = false;
	private static GLFWCursorPosCallback cursorPosCallback;
	private static GLFWCursorEnterCallback cursorEnterCallback;
	private static GLFWMouseButtonCallback cursorMouseButtonCallback;

	private static GLFWScrollCallback scrollCallback;

	public static void init() {
		double[] xPos = new double[1];
		double[] yPos = new double[1];

		glfwGetCursorPos(Window.handle, xPos, yPos);
		previousPos.set(xPos[0], yPos[0]);
		currentPos.set(xPos[0], yPos[0]);

		glfwSetCursorPosCallback(Window.handle, cursorPosCallback = new GLFWCursorPosCallback() {
			@Override
			public void invoke(long window, double xPos, double yPos) {
				if (leftButtonPressed) {
					EventManager.callEvent(new MouseDragEvent(new Vector2i((int) currentPos.x, (int) currentPos.y), new Vector2i((int) (xPos - currentPos.x), (int) (yPos - currentPos.y)), GLFW_MOUSE_BUTTON_1));
				}
				previousPos.set(currentPos);

				currentPos.x = xPos;
				currentPos.y = yPos;

				EventManager.callEvent(new MouseMoveEvent(new Vector2i((int) xPos, (int) yPos), new Vector2i((int) (previousPos.x - currentPos.x), (int) (previousPos.y - currentPos.y))));
			}
		});
		glfwSetCursorEnterCallback(Window.handle, cursorEnterCallback = new GLFWCursorEnterCallback() {
			@Override
			public void invoke(long window, boolean entered) {
				if (Window.isCursorGrabbed() && glfwGetInputMode(Window.handle, GLFW_CURSOR) == GLFW_CURSOR_NORMAL && glfwGetWindowAttrib(Window.handle, GLFW_HOVERED) == GLFW_TRUE) {
					glfwSetInputMode(Window.handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
				}
//				if (!Window.cursorGrabbed && !Window.isKeyPressed(GLFW_KEY_LEFT_ALT) && glfwGetWindowAttrib(Window.handle, GLFW_HOVERED) == GLFW_TRUE && glfwGetWindowAttrib(Window.handle, GLFW_FOCUSED) == GLFW_TRUE) {
//					Window.cursorGrabbed = true;
//					glfwSetInputMode(Window.handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
//				}
			}
		});
		glfwSetMouseButtonCallback(Window.handle, cursorMouseButtonCallback = new GLFWMouseButtonCallback() {
			@Override
			public void invoke(long window, int button, int action, int mode) {
//				MouseButton mouseButton = leftButtonPressed ? MouseButton.BUTTON_1 : MouseButton.BUTTON_2;
				leftButtonPressed = button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS;
				rightButtonPressed = button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS;

//				if (action == GLFW_RELEASE) {
				final MouseClickEvent.Stage stage = action == GLFW_RELEASE ? MouseClickEvent.Stage.RELEASE : MouseClickEvent.Stage.PRESS;
				final MouseClickEvent event = new MouseClickEvent(new Vector2i((int) currentPos.x, (int) currentPos.y), button, stage);
				EventManager.callEvent(event);
//				}
			}
		});
		glfwSetScrollCallback(Window.handle, scrollCallback = new GLFWScrollCallback() {
			@Override
			public void invoke(long window, double xoffset, double yoffset) {
				EventManager.callEvent(new MouseScrollEvent((int) xoffset, (int) yoffset));
			}
		});
	}

	public static void cleanup() {
		cursorPosCallback.free();
		cursorEnterCallback.free();
		cursorMouseButtonCallback.free();
		scrollCallback.free();
	}
}
