package com.zoritism.webdisc.client;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.client.audio.sound.WebFileSound;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

/**
 * Простая прослойка для доступа к WebDisc-звукам по storageUuid
 * из миксинов (StorageSoundHandler).
 *
 * Источник правды — WebDiscClientHandler.getSoundsByUuidView().
 */
public final class WebDiscSoundBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private WebDiscSoundBridge() {}

    public static WebFileSound getSoundForStorageUuid(UUID storageUuid) {
        if (storageUuid == null) {
            return null;
        }
        try {
            Map<UUID, WebFileSound> map = WebDiscClientHandler.getSoundsByUuidView();
            WebFileSound sound = map.get(storageUuid);
            LOGGER.info("[WebDisc][SoundBridge] getSoundForStorageUuid: uuid={}, found={}", storageUuid, sound != null);
            return sound;
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][SoundBridge] failed to get sound for uuid={}: {}", storageUuid, t.toString());
            return null;
        }
    }
}