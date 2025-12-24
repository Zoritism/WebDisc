package com.zoritism.webdisc.client;

import com.zoritism.webdisc.client.audio.WebDiscAudioHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WebDiscClientLifecycleHandler {

    private WebDiscClientLifecycleHandler() {}

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
        } catch (Throwable ignored) {}
        resetAllClientState();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
        } catch (Throwable ignored) {}
        resetAllClientState();
    }

    private static void resetAllClientState() {
        try {
            WebDiscClientHandler.resetAll();
        } catch (Throwable ignored) {}
        try {
            WebDiscAudioHelper.resetClient();
        } catch (Throwable ignored) {}
    }
}