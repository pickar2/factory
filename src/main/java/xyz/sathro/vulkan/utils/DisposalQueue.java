package xyz.sathro.vulkan.utils;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import xyz.sathro.vulkan.models.IDisposable;
import xyz.sathro.vulkan.renderer.MainRenderer;

import java.util.LinkedList;
import java.util.List;

import static xyz.sathro.vulkan.renderer.MainRenderer.MAX_FRAMES_IN_FLIGHT;

public class DisposalQueue {
	private static final Int2ObjectArrayMap<List<List<IDisposable>>> map = new Int2ObjectArrayMap<>();
	private static final List<IDisposable> lateDisposables = new LinkedList<>();

	static {
		for (int k = 0; k < 2; k++) {
			map.put(k, new ObjectArrayList<>());
			for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
				map.get(k).add(new LinkedList<>());
			}
		}
	}

	private DisposalQueue() { }

	public static int getCurrentFrameIndex() {
		return MainRenderer.getCurrentFrameIndex();
	}

	public static int getNextFrameIndex() {
		return (MainRenderer.getCurrentFrameIndex() + 1) % MAX_FRAMES_IN_FLIGHT;
	}

	public static int getPreviousFrameIndex() {
		return (MAX_FRAMES_IN_FLIGHT + MainRenderer.getCurrentFrameIndex() - 1) % MAX_FRAMES_IN_FLIGHT;
	}

	/**
	 * Adds object to list to be disposed at the end of every frame
	 */
	public static void registerToDisposal(IDisposable disposable) {
		synchronized (map) {
			map.get(1).get(getCurrentFrameIndex()).add(disposable);
		}
	}

	/**
	 * Adds object to list to be disposed at the end of the engine's lifecycle
	 */
	public static void registerToLateDisposal(IDisposable disposable) {
		synchronized (lateDisposables) {
			lateDisposables.add(disposable);
		}
	}

	public static void dispose() {
		final int index = getCurrentFrameIndex();
		synchronized (map) {
			final List<IDisposable> temp = map.get(0).get(index);
			temp.forEach(IDisposable::dispose);
			temp.clear();

			map.get(0).set(index, map.get(1).get(index));
			map.get(1).set(index, temp);
		}
	}

	public static void lateDispose() {
		synchronized (lateDisposables) {
			lateDisposables.forEach(IDisposable::dispose);
			lateDisposables.clear();
		}

		synchronized (map) {
			for (List<List<IDisposable>> lists : map.values()) {
				for (List<IDisposable> disposables : lists) {
					disposables.forEach(IDisposable::dispose);
				}
			}
		}
	}
}
