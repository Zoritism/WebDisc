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
import net.minecraftforge.network.PacketDistributor;

import java.net.URL;
import java.util.function.Supplier;

/**
 * C2S: игрок нажал "Принять" в окне ввода URL.
 * Сервер:
 * - валидирует URL;
 * - проверяет белый список;
 * - пишет URL в NBT диска;
 * - сбрасывает флаги finalized/durationTicks;
 * - шлёт S2C StartRecordMessage только этому игроку.
 */
public record SetUrlMessage(String url) {

    public static void encode(SetUrlMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.url);
    }

    public static SetUrlMessage decode(FriendlyByteBuf buf) {
        return new SetUrlMessage(buf.readUtf());
    }

    public static void handle(SetUrlMessage msg, Supplier<NetworkEvent.Context> ctx) {
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

            // Записываем URL и сбрасываем статус записи
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString(WebDiscMod.URL_NBT, u);
            tag.putBoolean("webdisc:finalized", false);
            tag.remove("webdisc:durationTicks");
            stack.setTag(tag);

            p.playNotifySound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1.0F, 1.0F);

            // Шлём клиенту команду начать запись/загрузку
            com.zoritism.webdisc.network.WebDiscNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> (net.minecraft.server.level.ServerPlayer) p),
                    new StartRecordMessage(u)
            );
        });
        c.setPacketHandled(true);
    }
}