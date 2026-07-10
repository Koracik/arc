package com.realradio.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Creative/debug tool: use toggles the client coverage overlay (handled client-side).
 */
public class CoverageMapperItem extends Item {
    public CoverageMapperItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Client-side overlay toggle is done via keybind / client event when holding this item
        if (level.isClientSide) {
            com.realradio.client.coverage.CoverageOverlay.toggle();
            player.displayClientMessage(
                    Component.translatable("gui.real_radio.coverage_toggle",
                            com.realradio.client.coverage.CoverageOverlay.isEnabled() ? "ON" : "OFF"),
                    true
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gui.real_radio.coverage_toggle", "…"));
    }
}
