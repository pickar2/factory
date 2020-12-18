package xyz.sathro.factory.collision;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.joml.Vector3d;
import org.joml.Vector3i;
import xyz.sathro.factory.physics.IPhysicsEntity;
import xyz.sathro.factory.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Stream;

public class Octree {
	private static final byte MAX_LIFE = 64;
	private static final int MIN_SIZE = 1;

	private final Node root;
	private final List<IPhysicsEntity> offWorldEntities = new ObjectArrayList<>();

	public Octree(Node root) {
		this.root = root;
	}

	public Octree() {
		this.root = new Node(new Vector3i(-256, -256, -256), new Vector3i(256, 256, 256));
	}

	public List<Pair<IPhysicsEntity, IPhysicsEntity>> getAllCollisionPairs() {
		final List<Pair<IPhysicsEntity, IPhysicsEntity>> pairs = new ArrayList<>();
		Stack<Node> openList = new Stack<>();
		openList.add(root);

		while (!openList.empty()) {
			final Node node = openList.pop();

			if (node.entries != null && !node.entries.isEmpty()) {
				if (node.parent != null) {
					node.parent.getAllEntriesTouchingThisNode().forEach(entity -> {
						for (IPhysicsEntity entry : node.entries) {
							pairs.add(Pair.of(entity, entry));
						}
					});
				}

				for (int i = 0; i < node.entries.size(); i++) {
					for (int k = i + 1; k < node.entries.size(); k++) {
						pairs.add(Pair.of(node.entries.get(i), node.entries.get(k)));
					}
				}
			}

			if (node.children != null) {
				for (Node child : node.children) {
					if (child != null) {
						openList.add(child);
					}
				}
			}
		}

		return pairs;
	}

	public List<Node> getAllNodes() {
		List<Node> nodes = new ObjectArrayList<>();
		Stack<Node> openList = new Stack<>();
		openList.add(root);

		Node node;
		while (!openList.empty()) {
			node = openList.pop();
			nodes.add(node);
			if (node.children != null) {
				for (Node child : node.children) {
					if (child != null) {
						openList.add(child);
					}
				}
			}
		}

		return nodes;
	}

	public void insertEntity(IPhysicsEntity entity) {
		final Vector3d pos = new Vector3d(entity.getPose().position);

		if (!root.canContain(entity)) {
			offWorldEntities.add(entity);
			return;
		}

		Node node = root;
		while (true) {
			int size = (node.maxPos.x - node.minPos.x) >> 1;
			if (size < MIN_SIZE) {
				node.addEntity(entity);
				break;
			}

			final int xCenter = (node.minPos.x + node.maxPos.x) >> 1;
			final int yCenter = (node.minPos.y + node.maxPos.y) >> 1;
			final int zCenter = (node.minPos.z + node.maxPos.z) >> 1;

			final Vector3i minPos = new Vector3i(node.minPos);
			byte b = 0;
			if (pos.x > xCenter) {
				b |= 0b0000001;
				minPos.x = xCenter;
			}
			if (pos.y > yCenter) {
				b |= 0b0000010;
				minPos.y = yCenter;
			}
			if (pos.z > zCenter) {
				b |= 0b0000100;
				minPos.z = zCenter;
			}

			if (CollisionUtils.isEntityFullyInside(entity, minPos, new Vector3i(minPos).add(size, size, size))) {
				if (node.children == null) {
					node.children = new Node[8];
				}
				if (node.children[b] == null) {
					node.children[b] = new Node(node, minPos, new Vector3i(minPos).add(size, size, size));
				}

				node = node.children[b];
			} else {
				node.addEntity(entity);
				break;
			}
		}
	}

	public static class Node {
		@Getter private final Node parent;
		@Getter private final Vector3i minPos, maxPos;
		private byte maxLife = 8;
		private byte curLife = maxLife;
		private List<IPhysicsEntity> entries;
		private Node[] children;

		public Node(Node parent, Vector3i minPos, Vector3i maxPos) {
			this.parent = parent;
			this.minPos = minPos;
			this.maxPos = maxPos;
		}

		public Node(Vector3i minPos, Vector3i maxPos) {
			this.parent = null;
			this.minPos = minPos;
			this.maxPos = maxPos;
		}

		public void addEntity(IPhysicsEntity entity) {
			if (entries == null) {
				entries = new ObjectArrayList<>();
			}

			entries.add(entity);
//			System.out.println("Inserted " + entity + " in " + this);
		}

		public boolean canContain(IPhysicsEntity entity) {
			return CollisionUtils.isEntityFullyInside(entity, minPos, maxPos);
		}

		private boolean allChildrenAreNull() {
			if (children != null) {
				for (int i = 0; i < 8; i++) {
					if (children[i] != null) {
						return false;
					}
				}
			}

			return true;
		}

		public void update() {
			if (children != null) {
				Node node;
				for (int i = 0; i < 8; i++) {
					node = children[i];
					if (node != null) {
						node.update();
						if (node.curLife == 0) {
							children[i] = null;
						}
					}
				}
			}

			if (entries == null || entries.isEmpty()) {
				if (allChildrenAreNull()) {
					if (curLife == -1) {
						curLife = maxLife;
					} else if (curLife > 0) {
						curLife--;
					}
				}
			} else {
				if (curLife != -1) {
					if (maxLife <= MAX_LIFE) {
						maxLife <<= 1;
					}
					curLife = -1;
				}
			}
		}

		public boolean removeEntry(IPhysicsEntity entry) {
			if (entries == null) { return false; }
			return entries.remove(entry);
		}

		public Stream<IPhysicsEntity> getEntriesStream() {
			if (entries == null) { return Stream.empty(); }
			return entries.stream();
		}

		public Stream<IPhysicsEntity> getAllEntriesTouchingThisNode() {
			if (parent == null) {
				return getEntriesStream();
			}
			return Stream.concat(getEntriesStream(), parent.getAllEntriesTouchingThisNode());
		}

		@Override
		public String toString() {
			return "Node{" +
			       "minPos=" + minPos +
			       ", maxPos=" + maxPos +
			       '}';
		}
	}
}
