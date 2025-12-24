package com.zoritism.webdisc.network.message;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * S2C: вспомогательное сообщение с длительностью webdisc-диска для SBP-джукбокса.
 * Сейчас используется только для логирования/отладки; дедлайны полностью убраны.
 */
public record WebdiscJukeboxTimerMessage(java.util.UUID storageUuid, int durationTicks) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void encode(WebdiscJukeboxTimerMessage msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.storageUuid());
        buf.writeVarInt(msg.durationTicks());
    }

    public static WebdiscJukeboxTimerMessage decode(FriendlyByteBuf buf) {
        java.util.UUID uuid = buf.readUUID();
        int ticks = buf.readVarInt();
        return new WebdiscJukeboxTimerMessage(uuid, ticks);
    }

    public static void handle(WebdiscJukeboxTimerMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (!ctx.getDirection().getReceptionSide().isClient()) {
                return;
            }
            // Оставляем только лог, чтобы видеть длительность трека.
            try {
                LOGGER.info("[WebDisc][JukeboxTimer] received duration: storageUuid={}, durationTicks={}",
                        msg.storageUuid(), msg.durationTicks());
            } catch (Throwable ignored) {}
        });
        ctx.setPacketHandled(true);
    }
}