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

    protected static final int GUI_WIDTH = 220;
    protected static final int GUI_HEIGHT = 200;

    protected AbstractRadioScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 8;
        this.titleLabelX = 12;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // Wooden bezel accents
        graphics.fill(x + 8, y + 22, x + imageWidth - 8, y + 24, 0x55C9A227);
        graphics.fill(x + 8, y + imageHeight - 12, x + imageWidth - 8, y + imageHeight - 10, 0x553A2A1A);

        // Inner panel shadow for depth
        graphics.fill(x + 10, y + 26, x + imageWidth - 10, y + 27, 0x33000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int maxTitleW = imageWidth - 36;
        String titleText = font.plainSubstrByWidth(title.getString(), maxTitleW);
        graphics.drawString(font, titleText, titleLabelX, titleLabelY, RadioWidgets.COL_AMBER_LIT, false);
    }

    protected void drawPowerLed(GuiGraphics graphics, boolean active) {
        int ledColor = active ? RadioWidgets.COL_LED_ON : RadioWidgets.COL_LED_OFF;
        int lx = leftPos + imageWidth - 18;
        int ly = topPos + 10;
        graphics.fill(lx - 1, ly - 1, lx + 9, ly + 9, 0xFF1A1208);
        graphics.fill(lx, ly, lx + 8, ly + 8, ledColor);
        if (active) {
            // soft glow
            graphics.fill(lx + 1, ly + 1, lx + 4, ly + 4, 0x66FFFFFF);
        }
    }
}
