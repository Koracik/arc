package com.realradio.client.gui;

import com.realradio.RealRadio;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Shared vintage-radio look for transmitter / receiver GUIs.
 */
public abstract class AbstractRadioScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "textures/gui/radio.png");

    protected static final int GUI_WIDTH = 196;
    protected static final int GUI_HEIGHT = 166;

    protected AbstractRadioScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = 10000; // hide player inv label (no slots)
        this.titleLabelY = 8;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Fallback panel if texture is missing / simple style
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // Wooden bezel overlay accents
        graphics.fill(x + 8, y + 22, x + imageWidth - 8, y + 24, 0x55C9A227);
        graphics.fill(x + 8, y + imageHeight - 12, x + imageWidth - 8, y + imageHeight - 10, 0x553A2A1A);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE8D5A3, false);
    }
}
