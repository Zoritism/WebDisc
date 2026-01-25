package com.zoritism.webdisc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WebDiscScLengthOverride {

    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

    private WebDiscScLengthOverride() {}

    private static final ConcurrentHashMap<UUID, Integer> OVERRIDES = new ConcurrentHashMap<>();

    public static void put(UUID storageUuid, int lengthTicks) {
        if (storageUuid == null) {
            return;
        }
        int safe = Math.max(1, lengthTicks);
        OVERRIDES.put(storageUuid, safe);
        logger.info("[WebDisc] SC length override PUT uuid={} lengthTicks={}", storageUuid, safe);
    }

    public static Integer pop(UUID storageUuid) {
        if (storageUuid == null) {
            return null;
        }
        Integer v = OVERRIDES.remove(storageUuid);
        logger.info("[WebDisc] SC length override POP uuid={} found={} lengthTicks={}", storageUuid, v != null, v);
        return v;
    }

    public static void clear(UUID storageUuid) {
        if (storageUuid == null) {
            return;
        }
        boolean removed = OVERRIDES.remove(storageUuid) != null;
        logger.info("[WebDisc] SC length override CLEAR uuid={} removed={}", storageUuid, removed);
    }
}