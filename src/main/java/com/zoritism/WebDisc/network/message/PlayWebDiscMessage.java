package com.zoritism.webdisc.network.message;

import com.zoritism.webdisc.client.WebDiscClientHandler;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record PlayWebDiscMessage(BlockPos pos, String url, UUID uuid, int entityId) {

    public PlayWebDiscMessage(BlockPos pos, String url) {
        this(pos, url, Util.NIL_UUID, -1);
    }

    public static void encode(PlayWebDiscMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.url);
        buf.writeUUID(msg.uuid);

        buf.writeInt(msg.entityId);
    }

    public static PlayWebDiscMessage decode(FriendlyByteBuf buf) {
        return new PlayWebDiscMessage(
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readUUID(),
                buf.readInt()
        );
    }

    public static void handle(PlayWebDiscMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (!c.getDirection().getReceptionSide().isClient()) return;
            Vec3 center = msg.pos().getCenter();
            WebDiscClientHandler.play(center, msg.url(), msg.uuid(), msg.entityId());
        });
        c.setPacketHandled(true);
    }
}