package xyz.sathro.factory.ui.components;

import xyz.sathro.vulkan.models.CombinedBuffer;
import xyz.sathro.vulkan.models.IBufferObject;

public interface IUIComponent extends IBufferObject {
	CombinedBuffer.Builder appendToBufferBuilder(CombinedBuffer.Builder builder);

	boolean isSimpleQuad();
}
