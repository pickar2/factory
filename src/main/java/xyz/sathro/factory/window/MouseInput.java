package xyz.sathro.factory.window;

import org.joml.Vector2d;
import org.joml.Vector2f;
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

	private static final Vector2f displVec = new Vector2f();
	public static boolean first = true;
	private static boolean inWindow = false;
	private static boolean leftButtonPressed = false;
	private static boolean rightButtonPressed = false;
	private static GLFWCursorPosCallback cursorPosCallback;
	private static GLFWCursorEnterCallback cursorEnterCallback;
	private static GLFWMouseButtonCallback cursorMouseButtonCallback;

	private static GLFWScrollCallback scrollCallback;

	public static void init() {
		glfwSetCursorPosCallback(Window.handle, cursorPosCallback = new GLFWCursorPosCallback() {
			@Override
			public void invoke(long window, double xPos, double yPos) {
				if (leftButtonPressed || rightButtonPressed) {
					MouseButton button = leftButtonPressed ? MouseButton.LEFT : MouseButton.RIGHT;
					EventManager.callEvent(new MouseDragEvent(new Vector2i((int) currentPos.x, (int) currentPos.y), new Vector2i((int) (xPos - currentPos.x), (int) (yPos - currentPos.y)), button));
				}
				currentPos.x = xPos;
				currentPos.y = yPos;
				EventManager.callEvent(new MouseMoveEvent(new Vector2i((int) xPos, (int) yPos)));
			}
		});
		glfwSetCursorEnterCallback(Window.handle, cursorEnterCallback = new GLFWCursorEnterCallback() {
			@Override
			public void invoke(long window, boolean entered) {
				inWindow = entered;
				if (Window.cursorGrabbed && glfwGetInputMode(Window.handle, GLFW_CURSOR) == GLFW_CURSOR_NORMAL && glfwGetWindowAttrib(Window.handle, GLFW_HOVERED) == GLFW_TRUE) {
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
				MouseButton mouseButton = leftButtonPressed ? MouseButton.LEFT : MouseButton.RIGHT;
				leftButtonPressed = button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS;
				rightButtonPressed = button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS;

//				if (action == GLFW_RELEASE) {
				MouseClickEvent event = new MouseClickEvent(new Vector2i((int) currentPos.x, (int) currentPos.y), mouseButton);
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

	public static Vector2f getDisplVec() {
		return displVec;
	}

	public static void input() {
		displVec.set(0);
		if (inWindow) {
			double deltax = currentPos.x - previousPos.x;
			double deltay = currentPos.y - previousPos.y;
			if (deltax != 0 && !first) {
				displVec.y = (float) deltax;
			}
			if (deltay != 0 && !first) {
				displVec.x = (float) deltay;
			}
			if ((deltax != 0 || deltay != 0) && first) {
				first = false;
			}
		}
		previousPos.x = currentPos.x;
		previousPos.y = currentPos.y;
	}

	public static void cleanup() {
		cursorPosCallback.free();
		cursorEnterCallback.free();
		cursorMouseButtonCallback.free();
		scrollCallback.free();
	}

	public static boolean isLeftButtonPressed() {
		return leftButtonPressed;
	}

	public static boolean isRightButtonPressed() {
		return rightButtonPressed;
	}
}
