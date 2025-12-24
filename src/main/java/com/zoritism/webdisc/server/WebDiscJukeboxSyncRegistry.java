package com.zoritism.webdisc.server;


import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WebDiscJukeboxSyncRegistry {



    private WebDiscJukeboxSyncRegistry() {}

    public static final long MAX_LIFETIME_TICKS = 15L * 60L * 20L;

    public static final class Entry {
        public final UUID storageUuid;
        public final ResourceKey<Level> dimension;
        public final BlockPos pos;
        public final int entityId;
        public final String url;
        public final int discLengthTicks;
        public final long startGameTimeTicks;
        public final long discFinishGameTimeTicks;

        public Entry(UUID storageUuid,
                     ResourceKey<Level> dimension,
                     BlockPos pos,
                     int entityId,
                     String url,
                     int discLengthTicks,
                     long startGameTimeTicks,
                     long discFinishGameTimeTicks) {
            this.storageUuid = storageUuid;
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.entityId = entityId;
            this.url = url;
            this.discLengthTicks = discLengthTicks;
            this.startGameTimeTicks = startGameTimeTicks;
            this.discFinishGameTimeTicks = discFinishGameTimeTicks;
        }
    }

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    public static void put(UUID storageUuid,
                           ResourceKey<Level> dimension,
                           BlockPos pos,
                           int entityId,
                           String url,
                           int discLengthTicks,
                           long startGameTimeTicks,
                           long discFinishGameTimeTicks) {
        if (storageUuid == null || dimension == null || pos == null || url == null || url.isEmpty()) {
            return;
        }
        int safeLen = Math.max(1, discLengthTicks);
        long safeStart = Math.max(0L, startGameTimeTicks);
        long safeFinish = Math.max(safeStart, discFinishGameTimeTicks);
        Entry e = new Entry(storageUuid, dimension, pos, entityId, url, safeLen, safeStart, safeFinish);
        ENTRIES.put(storageUuid, e);
        try {
        } catch (Throwable ignored) {
        }
    }

    public static void remove(UUID storageUuid) {
        if (storageUuid == null) {
            return;
        }
        Entry removed = ENTRIES.remove(storageUuid);
        if (removed != null) {
            try {
            } catch (Throwable ignored) {
            }
        }
    }

    public static Map<UUID, Entry> snapshot() {
        return Map.copyOf(ENTRIES);
    }

    public static void clearAll() {
        ENTRIES.clear();
    }
}