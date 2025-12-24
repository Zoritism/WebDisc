package com.zoritism.webdisc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Краткоживущий реестр статуса текущего трека по storageUuid:
 * - true  = в этом слоте сейчас играет WebDisc (WebDiscItem с finalized=true);
 * - false = в этом слоте сейчас играет НЕ-webdisc (любой RecordItem/плейсхолдер).
 *
 * Заполняется на сервере в MixinJukeboxUpgradeWrapper.playDisc(),
 * читается на клиенте в MixinStorageSoundHandlerPlay.playStorageSound().
 *
 * Обязателен сброс при stopStorageSound() и при SoundFinishedNotificationMessage.
 */
public final class WebDiscPlaybackRegistry {

    private WebDiscPlaybackRegistry() {}

    private static final Map<UUID, Boolean> FLAGS = new ConcurrentHashMap<>();

    public static void markWebDisc(UUID storageUuid) {
        if (storageUuid != null) {
            FLAGS.put(storageUuid, Boolean.TRUE);
        }
    }

    public static void markNonWebDisc(UUID storageUuid) {
        if (storageUuid != null) {
            FLAGS.put(storageUuid, Boolean.FALSE);
        }
    }

    public static boolean isWebDisc(UUID storageUuid) {
        if (storageUuid == null) {
            return false;
        }
        Boolean v = FLAGS.get(storageUuid);
        return Boolean.TRUE.equals(v);
    }

    public static void clear(UUID storageUuid) {
        if (storageUuid != null) {
            FLAGS.remove(storageUuid);
        }
    }

    public static void clearAll() {
        FLAGS.clear();
    }
}