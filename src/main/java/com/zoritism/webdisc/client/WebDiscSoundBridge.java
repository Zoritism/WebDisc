package com.zoritism.webdisc.client;

import com.zoritism.webdisc.client.audio.sound.WebFileSound;

import java.util.Map;
import java.util.UUID;

public final class WebDiscSoundBridge {


    private WebDiscSoundBridge() {}

    public static WebFileSound getSoundForStorageUuid(UUID storageUuid) {
        if (storageUuid == null) {
            return null;
        }
        try {
            Map<UUID, WebFileSound> map = WebDiscClientHandler.getSoundsByUuidView();
            WebFileSound sound = map.get(storageUuid);
            return sound;
        } catch (Throwable t) {
            return null;
        }
    }
}