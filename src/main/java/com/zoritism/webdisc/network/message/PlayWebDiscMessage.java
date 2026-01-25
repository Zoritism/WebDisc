package com.zoritism.webdisc.network.message;

import com.zoritism.webdisc.client.WebDiscClientHandler;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Supplier;

public record PlayWebDiscMessage(
        BlockPos pos,
        String url,
        UUID uuid,
        int entityId,
        int elapsedTicks,
        int discLengthTicks
) {

    public PlayWebDiscMessage(BlockPos pos, String url) {
        this(pos, url, Util.NIL_UUID, -1, 0, 0);
    }

    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

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

            logger.info(
                    "[WebDisc] PlayWebDiscMessage recv uuid={} pos={} entityId={} elapsed={} len={} urlPresent={}",
                    msg.uuid(), msg.pos(), msg.entityId(), msg.elapsedTicks(), msg.discLengthTicks(),
                    msg.url() != null && !msg.url().isEmpty()
            );

            Vec3 center = msg.pos().getCenter();
            int elapsed = Math.max(0, msg.elapsedTicks());
            int length = Math.max(0, msg.discLengthTicks());
            WebDiscClientHandler.play(center, msg.url(), msg.uuid(), msg.entityId(), elapsed, length);
        });
        c.setPacketHandled(true);
    }
}