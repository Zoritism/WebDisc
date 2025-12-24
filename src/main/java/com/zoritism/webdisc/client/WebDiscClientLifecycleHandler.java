package com.zoritism.webdisc.client;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.client.audio.WebDiscAudioHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Клиентский lifecycle-хендлер для WebDisc:
 * - при логине/логауте очищает все локальные состояния и offset-данные,
 *   чтобы повторные подключения в тот же мир не использовали старые _off-файлы/offsetMs.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WebDiscClientLifecycleHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private WebDiscClientLifecycleHandler() {}

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            LOGGER.info("[WebDisc][Lifecycle] onClientLogin: resetting client WebDisc state");
        } catch (Throwable ignored) {}
        resetAllClientState();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            LOGGER.info("[WebDisc][Lifecycle] onClientLogout: resetting client WebDisc state");
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