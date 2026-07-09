package com.realradio.client.gui;

import com.realradio.common.menu.RadioTransmitterMenu;
import com.realradio.common.util.RadioBand;
import com.realradio.network.UpdateTransmitterPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadioTransmitterScreen extends AbstractRadioScreen<RadioTransmitterMenu> {
    private final BlockPos blockPos;

    private float frequency;
    private boolean isAM;
    private boolean active;

    private RadioWidgets.RadioSlider frequencySlider;
    private Button amFmButton;
    private Button powerButton;

    public RadioTransmitterScreen(RadioTransmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.blockPos = menu.getBlockPos();
        this.frequency = menu.getFrequency();
        this.isAM = menu.isAM();
        this.active = menu.isActive();
    }

    @Override
    protected void init() {
        super.init();

        int x = leftPos;
        int y = topPos;
        int sliderW = imageWidth - 32;

        frequencySlider = createFrequencySlider(x + 16, y + 44);
        addRenderableWidget(frequencySlider);

        // No range slider — range is derived from frequency (shown as read-only info)

        int btnW = (sliderW - 8) / 2;
        amFmButton = Button.builder(amFmLabel(), b -> {
            isAM = !isAM;
            frequency = band().defaultFrequency();
            removeWidget(frequencySlider);
            frequencySlider = createFrequencySlider(leftPos + 16, topPos + 44);
            addRenderableWidget(frequencySlider);
            b.setMessage(amFmLabel());
            sendUpdate();
        }).bounds(x + 16, y + 100, btnW, 20).build();
        addRenderableWidget(amFmButton);

        powerButton = Button.builder(powerLabel(), b -> {
            active = !active;
            b.setMessage(powerLabel());
            sendUpdate();
        }).bounds(x + 16 + btnW + 8, y + 100, btnW, 20).build();
        addRenderableWidget(powerButton);
    }

    private RadioWidgets.RadioSlider createFrequencySlider(int x, int y) {
        int sliderW = imageWidth - 32;
        return new RadioWidgets.RadioSlider(
                x, y, sliderW, 16,
                frequencyLabel(),
                band().minFrequency(), band().maxFrequency(), frequency,
                v -> {
                    frequency = v.floatValue();
                    frequencySlider.setMessage(frequencyLabel());
                },
                v -> {
                    frequency = band().snap(v.floatValue());
                    frequencySlider.setValue(frequency);
                    frequencySlider.setMessage(frequencyLabel());
                    sendUpdate();
                }
        );
    }

    private RadioBand band() {
        return RadioBand.fromAm(isAM);
    }

    private int autoRange() {
        return band().rangeBlocks(frequency);
    }

    private Component frequencyLabel() {
        return Component.literal(
                Component.translatable("gui.real_radio.frequency").getString() + ": " + band().format(frequency)
        );
    }

    private Component amFmLabel() {
        return Component.translatable(isAM ? "gui.real_radio.band_am" : "gui.real_radio.band_fm");
    }

    private Component powerLabel() {
        return Component.translatable(active ? "gui.real_radio.power_on" : "gui.real_radio.power_off");
    }

    private void sendUpdate() {
        PacketDistributor.sendToServer(new UpdateTransmitterPayload(blockPos, frequency, isAM, active));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int ledColor = active ? 0xFF33FF66 : 0xFF662222;
        graphics.fill(leftPos + imageWidth - 18, topPos + 10, leftPos + imageWidth - 10, topPos + 18, ledColor);

        // Auto range info (read-only) — sits between frequency slider and buttons
        String rangeText = Component.translatable("gui.real_radio.range_auto", autoRange()).getString();
        graphics.drawString(font, rangeText, leftPos + 16, topPos + 74, 0xFFE8D5A3, false);
        String hint = Component.translatable("gui.real_radio.range_hint").getString();
        graphics.drawString(font, font.plainSubstrByWidth(hint, imageWidth - 32),
                leftPos + 16, topPos + 86, 0xFFA89870, false);
    }
}
