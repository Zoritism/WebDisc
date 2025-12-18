package com.zoritism.webdisc.network.message;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.config.WebDiscConfig;
import com.zoritism.webdisc.registry.WebDiscRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.net.URL;
import java.util.function.Supplier;

public record SetUrlMessage(String url) {

    public static void encode(SetDiscUrlMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.url);
    }

    public static SetDiscUrlMessage decode(FriendlyByteBuf buf) {
        return new SetDiscUrlMessage(buf.readUtf());
    }

    public static void handle(SetDiscUrlMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (!c.getDirection().getReceptionSide().isServer()) return;
            Player p = c.getSender();
            if (p == null) return;

            ItemStack stack = p.getItemInHand(p.getUsedItemHand());
            if (!stack.is(WebDiscRegistry.CUSTOM_RECORD.get())) return;

            String u = msg.url();
            try {
                new URL(u).toURI();
            } catch (Exception e) {
                p.sendSystemMessage(Component.translatable("webdisc.url.invalid").withStyle(ChatFormatting.RED));
                return;
            }

            if (u.length() >= 400) {
                p.sendSystemMessage(Component.translatable("webdisc.url.too_long").withStyle(ChatFormatting.RED));
                return;
            }

            boolean allowed = false;
            for (String prefix : WebDiscConfig.SERVER.whitelistedUrls.get()) {
                if (u.startsWith(prefix)) {
                    allowed = true;
                    break;
                }
            }

            if (!allowed) {
                p.sendSystemMessage(
                        Component.translatable("webdisc.url.not_whitelisted")
                                .append(" ")
                                .append(WebDiscConfig.SERVER.whitelistedWebsites.get().toString())
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            p.playNotifySound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1.0F, 1.0F);
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString(WebDiscMod.URL_NBT, u);
            stack.setTag(tag);
        });
        c.setPacketHandled(true);
    }
}