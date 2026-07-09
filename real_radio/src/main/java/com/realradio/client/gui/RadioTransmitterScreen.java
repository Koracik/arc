package com.realradio.client.gui;

import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
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
    private int range;
    private boolean active;

    private RadioWidgets.RadioSlider frequencySlider;
    private RadioWidgets.RadioSlider rangeSlider;
    private Button amFmButton;
    private Button powerButton;

    public RadioTransmitterScreen(RadioTransmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.blockPos = menu.getBlockPos();
        this.frequency = menu.getFrequency();
        this.isAM = menu.isAM();
        this.range = menu.getRange();
        this.active = menu.isActive();
    }

    @Override
    protected void init() {
        super.init();

        int x = leftPos;
        int y = topPos;

        frequencySlider = createFrequencySlider(x + 16, y + 48);
        addRenderableWidget(frequencySlider);

        rangeSlider = new RadioWidgets.RadioSlider(
                x + 16, y + 84, 164, 16,
                rangeLabel(),
                RadioTransmitterBlockEntity.MIN_RANGE,
                RadioTransmitterBlockEntity.MAX_RANGE,
                range,
                v -> {
                    range = (int) Math.round(v);
                    rangeSlider.setMessage(rangeLabel());
                },
                v -> {
                    range = (int) Math.round(v);
                    rangeSlider.setMessage(rangeLabel());
                    sendUpdate();
                }
        );
        addRenderableWidget(rangeSlider);

        amFmButton = Button.builder(amFmLabel(), b -> {
            isAM = !isAM;
            frequency = band().defaultFrequency();
            removeWidget(frequencySlider);
            frequencySlider = createFrequencySlider(leftPos + 16, topPos + 48);
            addRenderableWidget(frequencySlider);
            b.setMessage(amFmLabel());
            sendUpdate();
        }).bounds(x + 16, y + 110, 70, 20).build();
        addRenderableWidget(amFmButton);

        powerButton = Button.builder(powerLabel(), b -> {
            active = !active;
            b.setMessage(powerLabel());
            sendUpdate();
        }).bounds(x + 110, y + 110, 70, 20).build();
        addRenderableWidget(powerButton);
    }

    private RadioWidgets.RadioSlider createFrequencySlider(int x, int y) {
        return new RadioWidgets.RadioSlider(
                x, y, 164, 16,
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

    private Component frequencyLabel() {
        return Component.literal(
                Component.translatable("gui.real_radio.frequency").getString() + ": " + band().format(frequency)
        );
    }

    private Component rangeLabel() {
        return Component.translatable("gui.real_radio.range", range);
    }

    private Component amFmLabel() {
        return Component.translatable(isAM ? "gui.real_radio.band_am" : "gui.real_radio.band_fm");
    }

    private Component powerLabel() {
        return Component.translatable(active ? "gui.real_radio.power_on" : "gui.real_radio.power_off");
    }

    private void sendUpdate() {
        PacketDistributor.sendToServer(new UpdateTransmitterPayload(blockPos, frequency, isAM, range, active));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int ledColor = active ? 0xFF33FF66 : 0xFF662222;
        graphics.fill(leftPos + imageWidth - 18, topPos + 10, leftPos + imageWidth - 10, topPos + 18, ledColor);
    }
}
