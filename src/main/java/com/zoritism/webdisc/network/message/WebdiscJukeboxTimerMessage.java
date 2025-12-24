package com.zoritism.webdisc.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record WebdiscJukeboxTimerMessage(java.util.UUID storageUuid, int durationTicks) {

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

            try {
            } catch (Throwable ignored) {}
        });
        ctx.setPacketHandled(true);
    }
}