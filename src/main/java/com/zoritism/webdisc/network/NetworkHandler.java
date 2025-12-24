package com.zoritism.webdisc.network;

import com.zoritism.webdisc.network.message.FinalizeRecordMessage;
import com.zoritism.webdisc.network.message.OpenUrlMenuMessage;
import com.zoritism.webdisc.network.message.PlayWebDiscMessage;
import com.zoritism.webdisc.network.message.SetUrlMessage;
import com.zoritism.webdisc.network.message.WebdiscJukeboxTimerMessage;
import com.zoritism.webdisc.WebDiscMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {

    public static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WebDiscMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                OpenUrlMenuMessage.class,
                OpenUrlMenuMessage::encode,
                OpenUrlMenuMessage::decode,
                OpenUrlMenuMessage::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                PlayWebDiscMessage.class,
                PlayWebDiscMessage::encode,
                PlayWebDiscMessage::decode,
                PlayWebDiscMessage::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                SetUrlMessage.class,
                SetUrlMessage::encode,
                SetUrlMessage::decode,
                SetUrlMessage::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                FinalizeRecordMessage.class,
                FinalizeRecordMessage::encode,
                FinalizeRecordMessage::decode,
                FinalizeRecordMessage::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                WebdiscJukeboxTimerMessage.class,
                WebdiscJukeboxTimerMessage::encode,
                WebdiscJukeboxTimerMessage::decode,
                WebdiscJukeboxTimerMessage::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                com.zoritism.webdisc.network.message.WebdiscJukeboxSyncMessage.class,
                com.zoritism.webdisc.network.message.WebdiscJukeboxSyncMessage::encode,
                com.zoritism.webdisc.network.message.WebdiscJukeboxSyncMessage::decode,
                com.zoritism.webdisc.network.message.WebdiscJukeboxSyncMessage::handle
        );
    }
}