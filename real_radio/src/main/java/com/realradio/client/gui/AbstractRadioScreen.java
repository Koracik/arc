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
 * Draws a procedural wooden panel on top of the base texture.
 */
public abstract class AbstractRadioScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "textures/gui/radio.png");

    protected static final int GUI_WIDTH = 226;
    protected static final int GUI_HEIGHT = 228;

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

        // Base texture (may be simple) then procedural wood chrome
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        drawWoodenPanel(graphics, x, y, imageWidth, imageHeight);
    }

    /**
     * Layered wood frame, speaker grill hint, brass screws — pure code art.
     */
    protected void drawWoodenPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        // Outer shadow
        graphics.fill(x + 2, y + 2, x + w, y + h, 0x44000000);

        // Main wood body
        graphics.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF3D2A18);
        // Lighter wood inset
        graphics.fill(x + 7, y + 7, x + w - 7, y + h - 7, 0xFF4A3420);
        // Darker inner face
        graphics.fill(x + 10, y + 24, x + w - 10, y + h - 14, 0xFF2C1E12);

        // Brass top stripe
        graphics.fill(x + 10, y + 22, x + w - 10, y + 24, 0xAAC9A227);
        graphics.fill(x + 10, y + h - 14, x + w - 10, y + h - 12, 0x553A2A1A);

        // Corner “screws”
        drawScrew(graphics, x + 12, y + 12);
        drawScrew(graphics, x + w - 18, y + 12);
        drawScrew(graphics, x + 12, y + h - 20);
        drawScrew(graphics, x + w - 18, y + h - 20);

        // Decorative speaker grill (bottom strip)
        int gx = x + 16;
        int gy = y + h - 28;
        int gw = w - 32;
        graphics.fill(gx, gy, gx + gw, gy + 10, 0xFF1A120A);
        for (int i = 0; i < gw; i += 4) {
            graphics.fill(gx + i, gy + 1, gx + i + 2, gy + 9, 0xFF3A2A1A);
        }
    }

    private static void drawScrew(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 5, y + 5, 0xFF8B6914);
        graphics.fill(x + 1, y + 1, x + 4, y + 4, 0xFFC9A227);
        graphics.fill(x + 2, y + 1, x + 3, y + 4, 0xFF3A2A1A);
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
        int lx = leftPos + imageWidth - 20;
        int ly = topPos + 10;
        graphics.fill(lx - 1, ly - 1, lx + 9, ly + 9, 0xFF1A1208);
        graphics.fill(lx, ly, lx + 8, ly + 8, ledColor);
        if (active) {
            graphics.fill(lx + 1, ly + 1, lx + 4, ly + 4, 0x66FFFFFF);
        }
    }
}
