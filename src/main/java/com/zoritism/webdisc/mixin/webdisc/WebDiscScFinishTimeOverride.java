package com.zoritism.webdisc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WebDiscScFinishTimeOverride {

    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

    private WebDiscScFinishTimeOverride() {}

    private static final ConcurrentHashMap<UUID, Long> OVERRIDES = new ConcurrentHashMap<>();

    public static void put(UUID storageUuid, long finishTime) {
        if (storageUuid == null) return;
        OVERRIDES.put(storageUuid, finishTime);
        logger.info("[WebDisc] SC finishTime override PUT uuid={} finishTime={}", storageUuid, finishTime);
    }

    public static Long pop(UUID storageUuid) {
        if (storageUuid == null) return null;
        Long v = OVERRIDES.remove(storageUuid);
        logger.info("[WebDisc] SC finishTime override POP uuid={} found={} finishTime={}", storageUuid, v != null, v);
        return v;
    }

    public static void clear(UUID storageUuid) {
        if (storageUuid == null) return;
        boolean removed = OVERRIDES.remove(storageUuid) != null;
        logger.info("[WebDisc] SC finishTime override CLEAR uuid={} removed={}", storageUuid, removed);
    }
}