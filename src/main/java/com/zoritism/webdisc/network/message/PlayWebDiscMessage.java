package com.zoritism.webdisc.network.message;

import com.zoritism.webdisc.client.WebDiscClientHandler;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C: команда клиенту начать/остановить проигрывание webdisc-а в точке или от entity.
 * Пустой url означает "остановить".
 *
 * elapsedTicks / discLengthTicks:
 * - используются для первого старта трека (точный seek);
 * - для последующей периодической ресинхронизации используется WebdiscJukeboxSyncMessage.
 */
public record PlayWebDiscMessage(
        BlockPos pos,
        String url,
        UUID uuid,
        int entityId,
        int elapsedTicks,
        int discLengthTicks
) {

    /**
     * Упрощённый конструктор для ванильного джукбокса / старых вызовов:
     * без uuid и без таймингов.
     */
    public PlayWebDiscMessage(BlockPos pos, String url) {
        this(pos, url, Util.NIL_UUID, -1, 0, 0);
    }

    public static void encode(PlayWebDiscMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.url);
        buf.writeUUID(msg.uuid);
        buf.writeInt(msg.entityId);
        buf.writeVarInt(msg.elapsedTicks);
        buf.writeVarInt(msg.discLengthTicks);
    }

    public static PlayWebDiscMessage decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String url = buf.readUtf();
        UUID uuid = buf.readUUID();
        int entityId = buf.readInt();
        int elapsed = buf.readVarInt();
        int length = buf.readVarInt();
        return new PlayWebDiscMessage(pos, url, uuid, entityId, elapsed, length);
    }

    public static void handle(PlayWebDiscMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context c = ctxSupplier.get();
        c.enqueueWork(() -> {
            if (!c.getDirection().getReceptionSide().isClient()) return;
            Vec3 center = msg.pos().getCenter();
            int elapsed = Math.max(0, msg.elapsedTicks());
            int length = Math.max(0, msg.discLengthTicks());
            WebDiscClientHandler.play(center, msg.url(), msg.uuid(), msg.entityId(), elapsed, length);
        });
        c.setPacketHandled(true);
    }
}