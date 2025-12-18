package com.zoritism.webdisc.network;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.network.message.FinalizeRecordMessage;
import com.zoritism.webdisc.network.message.OpenUrlMenuMessage;
import com.zoritism.webdisc.network.message.PlayWebDiscMessage;
import com.zoritism.webdisc.network.message.SetUrlMessage;
import com.zoritism.webdisc.network.message.StartRecordMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WebDiscNetwork {

    private WebDiscNetwork() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WebDiscMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int id = 0;

    public static void init() {
        CHANNEL.registerMessage(
                id++,
                OpenUrlMenuMessage.class,
                OpenUrlMenuMessage::encode,
                OpenUrlMenuMessage::decode,
                OpenUrlMenuMessage::handle
        );
        CHANNEL.registerMessage(
                id++,
                PlayWebDiscMessage.class,
                PlayWebDiscMessage::encode,
                PlayWebDiscMessage::decode,
                PlayWebDiscMessage::handle
        );
        CHANNEL.registerMessage(
                id++,
                SetUrlMessage.class,
                SetUrlMessage::encode,
                SetUrlMessage::decode,
                SetUrlMessage::handle
        );
        CHANNEL.registerMessage(
                id++,
                StartRecordMessage.class,
                StartRecordMessage::encode,
                StartRecordMessage::decode,
                StartRecordMessage::handle
        );
        CHANNEL.registerMessage(
                id++,
                FinalizeRecordMessage.class,
                FinalizeRecordMessage::encode,
                FinalizeRecordMessage::decode,
                FinalizeRecordMessage::handle
        );
    }

    public static <M> void sendToAllNear(Level level, Vec3 pos, double range, M msg) {
        if (level == null) return;
        CHANNEL.send(
                PacketDistributor.NEAR.with(() ->
                        new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, range, level.dimension())),
                msg
        );
    }
}