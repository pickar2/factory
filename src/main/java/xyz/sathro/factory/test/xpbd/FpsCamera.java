package xyz.sathro.factory.test.xpbd;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.joml.Vector3f;
import org.joml.Vector3i;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.util.Maths;
import xyz.sathro.factory.window.Window;
import xyz.sathro.factory.window.events.GameUpdateEvent;
import xyz.sathro.factory.window.events.KeyDownEvent;
import xyz.sathro.factory.window.events.KeyUpEvent;
import xyz.sathro.factory.window.events.MouseMoveEvent;

import static org.lwjgl.glfw.GLFW.*;

@Log4j2
public class FpsCamera {
	private static final float horizontalSpeed = 1;
	private static final float verticalSpeed = 1;
	private static final float mouseSensitivity = 0.25f;
	private final Vector3i moveDirection = new Vector3i();
	@Getter private final Vector3f position = new Vector3f(10, 7, 20);
	private float speedMultiplier = 1;

	@Getter @Setter private double FOV = 90;

	@Getter private float yaw;
	@Getter private float pitch;
	@Getter private float roll = 0;

	public FpsCamera() {
		EventManager.registerListeners(this);
	}

	public void setPosition(float x, float y, float z) {
		position.set(x, y, z);
	}

	public void movePosition(float offsetX, float offsetY, float offsetZ) {
		position.add(offsetX, offsetY, offsetZ);
	}

	public void setRotation(float pitch, float yaw, float roll) {
		this.yaw = yaw;
		this.pitch = pitch;
		this.roll = roll;
	}

	public void moveRotation(float pitch, float yaw, float roll) {
		this.yaw = (this.yaw + yaw) % 360;
		this.pitch = Maths.clamp(this.pitch + pitch, -89, 89);
		this.roll = (this.roll + roll) % 360;
	}

	@SubscribeEvent
	public void onMouseMove(MouseMoveEvent event) {
		if (Window.isCursorGrabbed()) {
			moveRotation(-event.delta.y * mouseSensitivity, -event.delta.x * mouseSensitivity, 0);
		}
	}

	@SubscribeEvent
	public void onGameUpdate(GameUpdateEvent event) {
		moveDirection.set(0, 0, 0);

		if (Window.isKeyPressed(GLFW_KEY_W)) {
			moveDirection.z = -1;
		} else if (Window.isKeyPressed(GLFW_KEY_S)) {
			moveDirection.z = 1;
		}
		if (Window.isKeyPressed(GLFW_KEY_A)) {
			moveDirection.x = -1;
		} else if (Window.isKeyPressed(GLFW_KEY_D)) {
			moveDirection.x = 1;
		}
		if (Window.isKeyPressed(GLFW_KEY_Z)) {
			moveDirection.y = -1;
		} else if (Window.isKeyPressed(GLFW_KEY_SPACE)) {
			moveDirection.y = 1;
		}

		if (moveDirection.z != 0) {
			position.x -= Maths.sinDeg(yaw) * moveDirection.z * speedMultiplier * horizontalSpeed;
			position.z += Maths.cosDeg(yaw) * moveDirection.z * speedMultiplier * horizontalSpeed;
		}
		if (moveDirection.x != 0) {
			position.x += Maths.cosDeg(yaw) * moveDirection.x * speedMultiplier * horizontalSpeed;
			position.z += Maths.sinDeg(yaw) * moveDirection.x * speedMultiplier * horizontalSpeed;
		}
		position.y += moveDirection.y * speedMultiplier * verticalSpeed;
	}

	@SubscribeEvent
	public void onKeyDown(KeyDownEvent event) {
		if (event.action != GLFW_PRESS) { return; }

		if (event.key == GLFW_KEY_LEFT_CONTROL) {
			speedMultiplier = 2.6f;
		} else if (event.key == GLFW_KEY_LEFT_SHIFT) {
			speedMultiplier = 0.4f;
		}
	}

	@SubscribeEvent
	public void onKeyUp(KeyUpEvent event) {
		if (event.key == GLFW_KEY_LEFT_CONTROL || event.key == GLFW_KEY_LEFT_SHIFT) {
			speedMultiplier = 1;
		}
	}
}
