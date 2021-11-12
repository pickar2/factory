package xyz.sathro.factory.ui.font;

import xyz.sathro.vulkan.models.VulkanImage;

public record UICharacter(Character character, int width, int height, VulkanImage texture) { }
