package com.realradio.client.gui;

import com.realradio.common.menu.RadioReceiverMenu;
import com.realradio.common.util.RadioBand;
import com.realradio.network.UpdateReceiverPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadioReceiverScreen extends AbstractRadioScreen<RadioReceiverMenu> {
    private final BlockPos blockPos;

    private float frequency;
    private boolean isAM;
    private float volume;
    private boolean active;

    private RadioWidgets.RadioSlider frequencySlider;
    private RadioWidgets.RadioSlider volumeSlider;
    private Button amFmButton;
    private Button powerButton;

    public RadioReceiverScreen(RadioReceiverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.blockPos = menu.getBlockPos();
        this.frequency = menu.getFrequency();
        this.isAM = menu.isAM();
        this.volume = menu.getVolume();
        this.active = menu.isActive();
    }

    @Override
    protected void init() {
        super.init();

        int x = leftPos;
        int y = topPos;

        frequencySlider = createFrequencySlider(x + 16, y + 48);
        addRenderableWidget(frequencySlider);

        volumeSlider = new RadioWidgets.RadioSlider(
                x + 16, y + 84, 164, 16,
                volumeLabel(),
                0.0, 1.0, volume,
                v -> {
                    volume = v.floatValue();
                    volumeSlider.setMessage(volumeLabel());
                },
                v -> {
                    volume = v.floatValue();
                    volumeSlider.setMessage(volumeLabel());
                    sendUpdate();
                }
        );
        addRenderableWidget(volumeSlider);

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

    private Component volumeLabel() {
        return Component.translatable("gui.real_radio.volume", Math.round(volume * 100));
    }

    private Component amFmLabel() {
        return Component.translatable(isAM ? "gui.real_radio.band_am" : "gui.real_radio.band_fm");
    }

    private Component powerLabel() {
        return Component.translatable(active ? "gui.real_radio.power_on" : "gui.real_radio.power_off");
    }

    private void sendUpdate() {
        PacketDistributor.sendToServer(new UpdateReceiverPayload(blockPos, frequency, isAM, volume, active));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int ledColor = active ? 0xFF33FF66 : 0xFF662222;
        graphics.fill(leftPos + imageWidth - 18, topPos + 10, leftPos + imageWidth - 10, topPos + 18, ledColor);

        // Signal strength meter
        float quality = menu.getSignalQuality();
        int meterX = leftPos + 16;
        int meterY = topPos + 138;
        int meterW = 164;
        graphics.fill(meterX, meterY, meterX + meterW, meterY + 8, 0xFF2A1F12);
        int fill = Math.round(meterW * quality);
        int color = quality > 0.66f ? 0xFF33CC55 : quality > 0.33f ? 0xFFCCAA33 : 0xFFCC4433;
        if (fill > 0) {
            graphics.fill(meterX, meterY, meterX + fill, meterY + 8, color);
        }
        graphics.drawString(font,
                Component.translatable("gui.real_radio.signal", Math.round(quality * 100)).getString(),
                meterX, meterY - 10, 0xFFE8D5A3, false);
    }
}
