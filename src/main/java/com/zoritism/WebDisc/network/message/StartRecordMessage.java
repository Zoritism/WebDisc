package com.zoritism.webdisc.network.message;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.client.audio.AudioHandlerClient;
import com.zoritism.webdisc.client.audio.WebDiscDurationHelper;
import com.zoritism.webdisc.network.WebDiscNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * S2C: сервер сообщил клиенту начать запись диска для указанного URL.
 * Клиент:
 * - показывает сообщение "идёт загрузка музыки";
 * - скачивает .ogg;
 * - вычисляет длительность через ffmpeg;
 * - шлёт C2S FinalizeRecordMessage с длиной в тиках.
 */
public record StartRecordMessage(String url) {

    public static void encode(StartRecordMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.url);
    }

    public static StartRecordMessage decode(FriendlyByteBuf buf) {
        return new StartRecordMessage(buf.readUtf());
    }

    public static void handle(StartRecordMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (!c.getDirection().getReceptionSide().isClient()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;

            String url = msg.url();
            if (url == null || url.isBlank()) return;

            mc.player.sendSystemMessage(Component.translatable("webdisc.song.downloading").withStyle(ChatFormatting.GRAY));

            AudioHandlerClient handler = new AudioHandlerClient();
            CompletableFuture<Boolean> future = handler.downloadAsOgg(url);

            future.thenAccept(success -> {
                Minecraft mc2 = Minecraft.getInstance();
                if (mc2 == null || mc2.player == null) return;

                if (!success) {
                    mc2.player.sendSystemMessage(Component.translatable("webdisc.song.failed").withStyle(ChatFormatting.RED));
                    return;
                }

                mc2.player.sendSystemMessage(Component.translatable("webdisc.song.ready").withStyle(ChatFormatting.GREEN));

                int ticks = WebDiscDurationHelper.getLengthTicksForUrl(url);
                if (ticks <= 0) {
                    mc2.player.sendSystemMessage(Component.translatable("webdisc.song.duration_failed").withStyle(ChatFormatting.RED));
                    return;
                }

                WebDiscNetwork.CHANNEL.sendToServer(new FinalizeRecordMessage(url, ticks));
            });
        });
        c.setPacketHandled(true);
    }
}