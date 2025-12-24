package com.zoritism.webdisc.network.message;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.client.WebDiscClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C: периодический sync от WebDiscJukeboxSyncTicker.
 *
 * Сервер шлёт remainingTicks (сколько осталось до конца композиции).
 * Клиент сравнивает remaining до конца текущего реально играющего файла (оригинал/_off).
 */
public record WebdiscJukeboxSyncMessage(
        UUID storageUuid,
        BlockPos pos,
        String url,
        int entityId,
        int remainingTicks,
        int discLengthTicks,
        long finishGameTimeTicks
) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void encode(WebdiscJukeboxSyncMessage msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.storageUuid);
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.url);
        buf.writeInt(msg.entityId);
        buf.writeVarInt(msg.remainingTicks);
        buf.writeVarInt(msg.discLengthTicks);
        buf.writeVarLong(msg.finishGameTimeTicks);
    }

    public static WebdiscJukeboxSyncMessage decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        BlockPos pos = buf.readBlockPos();
        String url = buf.readUtf();
        int entityId = buf.readInt();
        int remaining = buf.readVarInt();
        int length = buf.readVarInt();
        long finish = buf.readVarLong();
        return new WebdiscJukeboxSyncMessage(uuid, pos, url, entityId, remaining, length, finish);
    }

    public static void handle(WebdiscJukeboxSyncMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context c = ctxSupplier.get();
        c.enqueueWork(() -> {
            if (!c.getDirection().getReceptionSide().isClient()) {
                return;
            }

            UUID uuid = msg.storageUuid();
            int serverRemaining = Math.max(0, msg.remainingTicks());
            int discLength = Math.max(1, msg.discLengthTicks());
            long serverFinish = msg.finishGameTimeTicks();
            Vec3 center = msg.pos().getCenter();

            try {
                Minecraft mc = Minecraft.getInstance();
                String playerName = (mc != null && mc.player != null) ? mc.player.getGameProfile().getName() : "UNKNOWN";
                LOGGER.info(
                        "[WebDisc][JukeboxSync] CLIENT '{}' received sync: storageUuid={}, pos={}, remainingTicks={}, lengthTicks={}, finishGameTimeTicks={}, url='{}'",
                        playerName, uuid, msg.pos(), serverRemaining, discLength, serverFinish, msg.url()
                );
            } catch (Throwable ignored) {}

            WebDiscClientHandler.onSync(
                    uuid,
                    center,
                    msg.url(),
                    msg.entityId(),
                    serverRemaining,
                    discLength,
                    serverFinish
            );
        });
        c.setPacketHandled(true);
    }
}