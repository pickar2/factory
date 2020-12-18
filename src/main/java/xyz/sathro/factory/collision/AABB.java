package xyz.sathro.factory.collision;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.joml.Vector3d;

@AllArgsConstructor
@ToString
public class AABB {
	@Getter final Vector3d minPos, maxPos;
}
