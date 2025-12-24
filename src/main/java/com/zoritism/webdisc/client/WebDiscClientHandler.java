package com.zoritism.webdisc.client;

import com.zoritism.webdisc.client.audio.AudioHandlerClient;
import com.zoritism.webdisc.client.audio.WebDiscAudioHelper;
import com.zoritism.webdisc.client.audio.sound.WebEntityBoundSound;
import com.zoritism.webdisc.client.audio.sound.WebFileSound;
import com.zoritism.webdisc.util.WebHashing;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.StorageSoundHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WebDiscClientHandler {

    private WebDiscClientHandler() {}

    private static final Map<Vec3, WebFileSound> soundsByPos = new HashMap<>();
    private static final Map<UUID, WebFileSound> soundsByUuid = new HashMap<>();

    private static final Map<UUID, Long> startGameTimeTicksByUuid = new HashMap<>();
    private static final Map<UUID, Integer> startElapsedTicksByUuid = new HashMap<>();
    private static final Map<UUID, Integer> discLengthTicksByUuid = new HashMap<>();

    private static final Map<String, Integer> offsetsByUrlKey = new HashMap<>();

    private static final Set<UUID> validListeners = new HashSet<>();

    private static final int TICKS_PER_SECOND = 20;
    private static final int TOLERANCE_TICKS = 3 * TICKS_PER_SECOND;
    private static final int MIN_OFF_LENGTH_TICKS = 20 * TICKS_PER_SECOND;
    private static final int MIN_OFF_START_TICKS = 1 * TICKS_PER_SECOND;
    private static final long OFF_WAIT_TIMEOUT_MS = 20_000L;
    private static final int CLIENT_SYNC_GRACE_TICKS = 40;
    private static final int LENGTH_OVERFLOW_MARGIN_TICKS = 5 * TICKS_PER_SECOND;

    private static final long INACTIVE_TTL_TICKS = 10L * TICKS_PER_SECOND;
    private static final Map<UUID, Long> lastSeenClientTicksByUuid = new HashMap<>();

    private static boolean clientReadyForSync = false;
    private static long clientReadyFromGameTime = 0L;

    private static final int START_PLAY_GRACE_TICKS = 10;
    private static final Map<UUID, Long> lastStartClientGameTimeByUuid = new HashMap<>();

    private enum OffState {
        NONE,
        WAITING_FOR_OFF,
        USING_OFF,
        GAVE_UP
    }

    private static final class SessionState {
        final UUID uuid;

        String urlKey = "";
        int discLengthTicks = 0;
        int lastServerRemainingTicks = 0;
        long lastSyncClientGameTime = 0L;

        OffState offState = OffState.NONE;
        int offOffsetMs = 0;
        long offRequestTimeMs = 0L;
        int offAttempts = 0;

        boolean pendingInitialSeek = false;

        SessionState(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private static final Map<UUID, SessionState> sessions = new HashMap<>();

    public static Map<UUID, WebFileSound> getSoundsByUuidView() {
        return Collections.unmodifiableMap(soundsByUuid);
    }

    public static int getOffsetForUrlKey(String urlKey) {
        if (urlKey == null || urlKey.isEmpty()) {
            return 0;
        }
        Integer v = offsetsByUrlKey.get(urlKey);
        return v != null ? v : 0;
    }

    public static boolean isValidListener(UUID storageUuid) {
        return storageUuid != null && validListeners.contains(storageUuid);
    }

    private static void markValidListener(UUID storageUuid) {
        if (storageUuid == null) return;
        validListeners.add(storageUuid);
    }

    private static void clearValidListener(UUID storageUuid) {
        if (storageUuid == null) return;
        validListeners.remove(storageUuid);
    }

    static boolean isClientReadyForSync(ClientLevel level) {
        if (level == null) {
            clientReadyForSync = false;
            clientReadyFromGameTime = 0L;
            return false;
        }
        long now = level.getGameTime();
        if (!clientReadyForSync) {
            if (clientReadyFromGameTime == 0L) {
                clientReadyFromGameTime = now + CLIENT_SYNC_GRACE_TICKS;
                return false;
            }
            if (now >= clientReadyFromGameTime) {
                clientReadyForSync = true;
            }
        }
        return clientReadyForSync;
    }

    public static void resetAll() {
        try {
        } catch (Throwable ignored) {}

        Minecraft mc = Minecraft.getInstance();

        try {
            for (WebFileSound s : soundsByUuid.values()) {
                if (s != null && mc != null) {
                    try {
                        mc.getSoundManager().stop(s);
                    } catch (Throwable ignored) {}
                    try {
                        WebDiscAudioHelper.cleanupOffsetFilesForKey(s.getUrlKey());
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (WebFileSound s : soundsByPos.values()) {
                if (s != null && mc != null) {
                    try {
                        mc.getSoundManager().stop(s);
                    } catch (Throwable ignored) {}
                    try {
                        WebDiscAudioHelper.cleanupOffsetFilesForKey(s.getUrlKey());
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        soundsByUuid.clear();
        soundsByPos.clear();
        startGameTimeTicksByUuid.clear();
        startElapsedTicksByUuid.clear();
        discLengthTicksByUuid.clear();
        offsetsByUrlKey.clear();
        sessions.clear();
        validListeners.clear();
        lastSeenClientTicksByUuid.clear();
        lastStartClientGameTimeByUuid.clear();

        clientReadyForSync = false;
        clientReadyFromGameTime = 0L;
    }

    public static boolean isSoundActuallyPlaying(UUID uuid) {
        if (uuid == null) return false;
        WebFileSound sound = soundsByUuid.get(uuid);
        if (sound == null) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        try {
            return mc.getSoundManager().isActive(sound);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isWithinStartGrace(UUID uuid, long nowClientTicks) {
        if (uuid == null) return false;
        Long t0 = lastStartClientGameTimeByUuid.get(uuid);
        if (t0 == null) return false;
        return nowClientTicks - t0 <= START_PLAY_GRACE_TICKS;
    }

    private static void noteStarted(UUID uuid, ClientLevel level) {
        if (uuid == null || uuid.equals(Util.NIL_UUID) || level == null) return;
        lastStartClientGameTimeByUuid.put(uuid, level.getGameTime());
    }

    public static void clearByUuid(UUID uuid) {
        if (uuid == null) {
            return;
        }
        clearValidListener(uuid);
        lastSeenClientTicksByUuid.remove(uuid);
        lastStartClientGameTimeByUuid.remove(uuid);

        Minecraft mc = Minecraft.getInstance();
        WebFileSound existing = soundsByUuid.remove(uuid);
        if (existing != null && mc != null) {
            try {
                mc.getSoundManager().stop(existing);
            } catch (Throwable t) {
            }
            try {
                WebDiscAudioHelper.cleanupOffsetFilesForKey(existing.getUrlKey());
            } catch (Throwable ignored) {}
        }

        startGameTimeTicksByUuid.remove(uuid);
        startElapsedTicksByUuid.remove(uuid);
        discLengthTicksByUuid.remove(uuid);

        SessionState ss = sessions.remove(uuid);
        if (ss != null && ss.urlKey != null && !ss.urlKey.isEmpty()) {
            offsetsByUrlKey.remove(ss.urlKey);
        }
    }

    public static void onStorageSoundFinished(UUID storageUuid) {
        if (storageUuid == null) return;
        try {
            clearByUuid(storageUuid);
        } catch (Throwable t) {
        }
    }

    private static boolean hasActiveUuid(UUID uuid) {
        return uuid != null && soundsByUuid.containsKey(uuid);
    }

    private static void registerTiming(UUID uuid, int startElapsedTicksIgnored, int discLengthTicks) {
        if (uuid == null || uuid.equals(Util.NIL_UUID)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = (mc != null) ? mc.level : null;
        long now = (level != null) ? level.getGameTime() : 0L;

        int safeLen = Math.max(1, discLengthTicks);
        int safeStartElapsed = Math.max(0, Math.min(startElapsedTicksIgnored, safeLen));

        startGameTimeTicksByUuid.put(uuid, now);
        startElapsedTicksByUuid.put(uuid, safeStartElapsed);
        discLengthTicksByUuid.put(uuid, safeLen);
    }

    public static int estimateClientElapsedTicks(UUID uuid) {
        if (uuid == null || uuid.equals(Util.NIL_UUID)) {
            return -1;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = (mc != null) ? mc.level : null;
        if (level == null) return -1;

        Long startTime = startGameTimeTicksByUuid.get(uuid);
        Integer startElapsed = startElapsedTicksByUuid.get(uuid);
        Integer length = discLengthTicksByUuid.get(uuid);
        if (startTime == null || startElapsed == null || length == null || length <= 0) {
            return -1;
        }

        long now = level.getGameTime();
        long dt = now - startTime;
        if (dt < 0L) dt = 0L;
        long raw = (long) startElapsed + dt;
        if (raw < 0L) raw = 0L;
        if (raw > length) raw = length;
        return (int) raw;
    }

    public static void play(Vec3 center, String url, UUID uuid, int entityId) {
        play(center, url, uuid, entityId, 0, 0);
    }

    public static void play(Vec3 center, String url, UUID uuid, int entityId, int elapsedTicks, int discLengthTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        boolean hasUuid = uuid != null && !uuid.equals(Util.NIL_UUID);


        boolean forceVanillaPlayback = entityId < 0;

        Entity entity = (entityId >= 0) ? level.getEntity(entityId) : null;
        boolean entityBound = entity != null;

        int safeElapsed = Math.max(0, elapsedTicks);
        int requestedOffsetMs = safeElapsed * 50;
        int safeLen = Math.max(1, discLengthTicks);

        if (url == null || url.isEmpty()) {
            if (hasUuid) {
                clearValidListener(uuid);
                lastSeenClientTicksByUuid.remove(uuid);
                lastStartClientGameTimeByUuid.remove(uuid);

                WebFileSound existing = soundsByUuid.remove(uuid);
                if (existing != null) {
                    try {
                        mc.getSoundManager().stop(existing);
                    } catch (Throwable t) {
                    }
                    try {
                        WebDiscAudioHelper.cleanupOffsetFilesForKey(existing.getUrlKey());
                    } catch (Throwable ignored) {}
                }
            } else if (center != null) {
                WebFileSound existing = soundsByPos.remove(center);
                if (existing != null) {
                    try {
                        mc.getSoundManager().stop(existing);
                    } catch (Throwable t) {
                    }
                    try {
                        WebDiscAudioHelper.cleanupOffsetFilesForKey(existing.getUrlKey());
                    } catch (Throwable ignored) {}
                }
            }
            if (hasUuid) {
                startGameTimeTicksByUuid.remove(uuid);
                startElapsedTicksByUuid.remove(uuid);
                discLengthTicksByUuid.remove(uuid);
                SessionState ss = sessions.remove(uuid);
                if (ss != null && ss.urlKey != null && !ss.urlKey.isEmpty()) {
                    offsetsByUrlKey.remove(ss.urlKey);
                }
            }
            return;
        }

        String newKey = WebHashing.sha256(minecraftify(url));

        if (hasUuid) {
            clearValidListener(uuid);

            WebFileSound existing = soundsByUuid.remove(uuid);
            if (existing != null) {
                try {
                    mc.getSoundManager().stop(existing);
                } catch (Throwable t) {
                }
                String oldKey = existing.getUrlKey();
                if (!oldKey.equals(newKey)) {
                    try {
                        WebDiscAudioHelper.cleanupOffsetFilesForKey(oldKey);
                    } catch (Throwable ignored) {}
                }
            }
        } else if (center != null) {
            WebFileSound existing = soundsByPos.remove(center);
            if (existing != null) {
                try {
                    mc.getSoundManager().stop(existing);
                } catch (Throwable t) {
                }
                String oldKey = existing.getUrlKey();
                if (!oldKey.equals(newKey)) {
                    try {
                        WebDiscAudioHelper.cleanupOffsetFilesForKey(oldKey);
                    } catch (Throwable ignored) {}
                }
            }
        }

        SessionState ss = null;
        if (hasUuid) {
            ss = sessions.get(uuid);
            if (ss == null) {
                ss = new SessionState(uuid);
                sessions.put(uuid, ss);
            }
            ss.urlKey = newKey;
            ss.discLengthTicks = safeLen;
            ss.lastSyncClientGameTime = level.getGameTime();

            lastSeenClientTicksByUuid.put(uuid, level.getGameTime());
        }

        AudioHandlerClient handler = new AudioHandlerClient();
        boolean has = handler.hasOgg(url);

        if (!has) {
            SessionState finalSs = ss;
            int finalSafeLen = safeLen;
            int finalSafeElapsed = safeElapsed;
            boolean finalForceVanillaPlayback = forceVanillaPlayback;

            handler.downloadAsOgg(url).thenAccept(success -> {
                Minecraft innerMc = Minecraft.getInstance();
                if (innerMc == null) {
                    return;
                }
                if (!success) {
                    return;
                }

                ClientLevel lvl2 = innerMc.level;
                Entity ent2 = null;
                if (lvl2 != null && entityId >= 0) {
                    ent2 = lvl2.getEntity(entityId);
                }
                boolean entityBound2 = ent2 != null;

                WebFileSound fs2;
                if (entityBound2) {
                    fs2 = new WebEntityBoundSound(url, ent2);
                } else if (finalForceVanillaPlayback) {
                    fs2 = new com.zoritism.webdisc.client.audio.sound.WebShipBoundSound(
                            url,
                            net.minecraft.core.BlockPos.containing(center)
                    );
                } else {
                    fs2 = new WebFileSound(url, center);
                }

                String urlKey2 = fs2.getUrlKey();

                int offsetMs2 = requestedOffsetMs;
                if (hasUuid && finalSs != null) {
                    if (finalSs.offState == OffState.WAITING_FOR_OFF || finalSs.offState == OffState.USING_OFF) {
                        offsetMs2 = finalSs.offOffsetMs;
                    } else {
                        finalSs.offOffsetMs = requestedOffsetMs;
                        offsetMs2 = requestedOffsetMs;
                    }
                }

                offsetsByUrlKey.put(urlKey2, offsetMs2);

                if (hasUuid) {
                    soundsByUuid.put(uuid, fs2);

                    try {
                        if (finalForceVanillaPlayback) {
                            innerMc.getSoundManager().play(fs2);
                        } else {
                            StorageSoundHandler.playStorageSound(uuid, fs2);
                        }
                    } catch (Throwable t) {
                    }

                    registerTiming(uuid, finalSafeElapsed, finalSafeLen);
                    noteStarted(uuid, innerMc.level);

                    if (finalSs != null) {
                        finalSs.urlKey = urlKey2;
                        finalSs.discLengthTicks = finalSafeLen;

                        if (offsetMs2 > 0 && !WebDiscAudioHelper.isOffsetReady(urlKey2, offsetMs2)) {
                            finalSs.pendingInitialSeek = true;
                            finalSs.offState = OffState.WAITING_FOR_OFF;
                            finalSs.offOffsetMs = offsetMs2;
                            finalSs.offRequestTimeMs = System.currentTimeMillis();
                        } else {
                            finalSs.pendingInitialSeek = false;
                            finalSs.offState = offsetMs2 > 0 ? OffState.USING_OFF : OffState.NONE;
                        }
                    }
                } else if (center != null) {
                    soundsByPos.put(center, fs2);
                    try {
                        innerMc.getSoundManager().play(fs2);
                    } catch (Throwable t) {
                    }
                }
            });
            return;
        }

        WebFileSound fs;
        if (entityBound) {
            fs = new WebEntityBoundSound(url, entity);
        } else if (forceVanillaPlayback) {
            fs = new com.zoritism.webdisc.client.audio.sound.WebShipBoundSound(
                    url,
                    net.minecraft.core.BlockPos.containing(center)
            );
        } else {
            fs = new WebFileSound(url, center);
        }

        String urlKey = fs.getUrlKey();
        int offsetMs = requestedOffsetMs;

        if (hasUuid && ss != null) {
            if (ss.offState == OffState.WAITING_FOR_OFF || ss.offState == OffState.USING_OFF) {
                offsetMs = ss.offOffsetMs;
            } else {
                ss.offOffsetMs = requestedOffsetMs;
                offsetMs = requestedOffsetMs;
            }
        }

        offsetsByUrlKey.put(urlKey, offsetMs);

        if (hasUuid) {
            soundsByUuid.put(uuid, fs);

            try {
                if (forceVanillaPlayback) {
                    mc.getSoundManager().play(fs);
                } else {
                    StorageSoundHandler.playStorageSound(uuid, fs);
                }
            } catch (Throwable t) {
            }

            registerTiming(uuid, safeElapsed, safeLen);
            noteStarted(uuid, level);

            if (ss != null) {
                ss.urlKey = urlKey;
                ss.discLengthTicks = safeLen;

                if (offsetMs > 0 && !WebDiscAudioHelper.isOffsetReady(urlKey, offsetMs)) {
                    ss.pendingInitialSeek = true;
                    ss.offState = OffState.WAITING_FOR_OFF;
                    ss.offOffsetMs = offsetMs;
                    ss.offRequestTimeMs = System.currentTimeMillis();
                } else {
                    ss.pendingInitialSeek = false;
                    ss.offState = offsetMs > 0 ? OffState.USING_OFF : OffState.NONE;
                }
            }
        } else if (center != null) {
            soundsByPos.put(center, fs);
            try {
                mc.getSoundManager().play(fs);
            } catch (Throwable t) {
            }
        }
    }

    private static int estimateClientRemainingTicks(UUID uuid, SessionState ss, long nowClientTicks, int discLengthTicks) {
        if (uuid == null || uuid.equals(Util.NIL_UUID) || ss == null) return -1;

        Long startTime = startGameTimeTicksByUuid.get(uuid);
        if (startTime == null) return -1;

        long elapsedInCurrentFile = nowClientTicks - startTime;
        if (elapsedInCurrentFile < 0L) elapsedInCurrentFile = 0L;

        int offsetTicks = 0;
        if (ss.offState == OffState.USING_OFF || ss.offState == OffState.WAITING_FOR_OFF) {
            offsetTicks = Math.max(0, ss.offOffsetMs / 50);
        }

        int safeLen = Math.max(1, discLengthTicks);
        int currentFileLen = safeLen - offsetTicks;
        if (currentFileLen < 1) currentFileLen = 1;

        long remaining = (long) currentFileLen - elapsedInCurrentFile;
        if (remaining < 0L) remaining = 0L;
        if (remaining > currentFileLen) remaining = currentFileLen;

        return (int) remaining;
    }

    private static void logResyncDecision(String reason,
                                          UUID uuid,
                                          int serverRemainingTicks,
                                          int clientRemainingTicks,
                                          int deltaTicks,
                                          SessionState ss,
                                          int discLengthTicks) {
        try {
        } catch (Throwable ignored) {}
    }

    public static void onSync(
            UUID storageUuid,
            Vec3 center,
            String url,
            int entityId,
            int serverRemainingTicks,
            int discLengthTicks,
            long serverFinishGameTimeTicks
    ) {
        if (storageUuid == null || storageUuid.equals(Util.NIL_UUID)) {
            return;
        }
        if (url == null || url.isEmpty()) {
            return;
        }

        int safeLen = Math.max(1, discLengthTicks);
        int safeServerRemaining = Math.max(0, Math.min(serverRemainingTicks, safeLen));

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = (mc != null) ? mc.level : null;
        if (level == null) {
            return;
        }
        long nowClientTicks = level.getGameTime();

        lastSeenClientTicksByUuid.put(storageUuid, nowClientTicks);
        cleanupInactiveSoundsByTtl(nowClientTicks);

        SessionState ss = sessions.get(storageUuid);
        if (ss == null) {
            ss = new SessionState(storageUuid);
            sessions.put(storageUuid, ss);
        }
        String newKey = WebHashing.sha256(minecraftify(url));
        ss.urlKey = newKey;
        ss.discLengthTicks = safeLen;
        ss.lastServerRemainingTicks = safeServerRemaining;
        ss.lastSyncClientGameTime = nowClientTicks;

        tickCleanupOnSync(storageUuid, ss);

        if (!isClientReadyForSync(level)) {
            try {
                AudioHandlerClient handler = new AudioHandlerClient();
                if (!handler.hasOgg(url)) {
                    handler.downloadAsOgg(url);
                }
            } catch (Throwable t) {
            }
            return;
        }

        try {
            if (hasActiveUuid(storageUuid) && isSoundActuallyPlaying(storageUuid)) {
                markValidListener(storageUuid);
            }
        } catch (Throwable ignored) {}

        boolean hasLocal = hasActiveUuid(storageUuid) && startGameTimeTicksByUuid.containsKey(storageUuid);

        if (!hasLocal) {
            int serverElapsed = safeLen - safeServerRemaining;
            if (serverElapsed < 0) serverElapsed = 0;
            if (serverElapsed > safeLen) serverElapsed = safeLen;

            ss.offOffsetMs = serverElapsed * 50;
            ss.offState = OffState.NONE;
            ss.pendingInitialSeek = false;

            logResyncDecision("noLocalSoundOrTiming_restart", storageUuid, safeServerRemaining, -1, Integer.MIN_VALUE, ss, safeLen);
            play(center, url, storageUuid, entityId, serverElapsed, safeLen);
            return;
        }

        if (!isSoundActuallyPlaying(storageUuid)) {
            if (isWithinStartGrace(storageUuid, nowClientTicks)) {
                return;
            }

            int serverElapsed = safeLen - safeServerRemaining;
            if (serverElapsed < 0) serverElapsed = 0;
            if (serverElapsed > safeLen) serverElapsed = safeLen;

            ss.offOffsetMs = serverElapsed * 50;
            ss.offState = OffState.NONE;
            ss.pendingInitialSeek = false;

            logResyncDecision("notPlaying_restart", storageUuid, safeServerRemaining, -1, Integer.MIN_VALUE, ss, safeLen);
            play(center, url, storageUuid, entityId, serverElapsed, safeLen);
            return;
        }

        if (ss.pendingInitialSeek && ss.offOffsetMs > 0 && WebDiscAudioHelper.isOffsetReady(ss.urlKey, ss.offOffsetMs)) {
            int serverElapsed = safeLen - safeServerRemaining;
            if (serverElapsed < 0) serverElapsed = 0;
            if (serverElapsed > safeLen) serverElapsed = safeLen;

            ss.pendingInitialSeek = false;
            ss.offState = OffState.USING_OFF;

            logResyncDecision("pendingInitialSeek_ready_restart", storageUuid, safeServerRemaining, -1, Integer.MIN_VALUE, ss, safeLen);
            play(center, url, storageUuid, entityId, serverElapsed, safeLen);
            return;
        }

        boolean longEnough = safeLen >= MIN_OFF_LENGTH_TICKS;
        boolean pastOffStart = (safeLen - safeServerRemaining) >= MIN_OFF_START_TICKS;

        int clientRemaining = estimateClientRemainingTicks(storageUuid, ss, nowClientTicks, safeLen);
        int delta = (clientRemaining >= 0) ? (clientRemaining - safeServerRemaining) : Integer.MIN_VALUE;

        boolean desyncTooLarge = (clientRemaining >= 0) && Math.abs(delta) > TOLERANCE_TICKS;

        if (!longEnough || !pastOffStart) {
            if (!desyncTooLarge) {
                return;
            }

            int serverElapsed = safeLen - safeServerRemaining;
            if (serverElapsed < 0) serverElapsed = 0;
            if (serverElapsed > safeLen) serverElapsed = safeLen;

            ss.offOffsetMs = serverElapsed * 50;
            ss.offState = OffState.NONE;
            ss.pendingInitialSeek = false;

            logResyncDecision("shortTrackOrEarly_desync_restart", storageUuid, safeServerRemaining, clientRemaining, delta, ss, safeLen);
            play(center, url, storageUuid, entityId, serverElapsed, safeLen);
            return;
        }

        if (ss.offState == OffState.WAITING_FOR_OFF) {
            boolean offsetReady = WebDiscAudioHelper.isOffsetReady(ss.urlKey, ss.offOffsetMs);
            long nowMs = System.currentTimeMillis();

            if (offsetReady) {
                int serverElapsed = safeLen - safeServerRemaining;
                if (serverElapsed < 0) serverElapsed = 0;
                if (serverElapsed > safeLen) serverElapsed = safeLen;

                ss.offState = OffState.USING_OFF;
                ss.pendingInitialSeek = false;

                logResyncDecision("waitingForOff_ready_restart", storageUuid, safeServerRemaining, clientRemaining, delta, ss, safeLen);
                play(center, url, storageUuid, entityId, serverElapsed, safeLen);
                return;
            }

            if (nowMs - ss.offRequestTimeMs > OFF_WAIT_TIMEOUT_MS) {
                ss.offState = OffState.GAVE_UP;
                ss.pendingInitialSeek = false;
                ss.offAttempts++;
                return;
            }

            if (desyncTooLarge) {
                int serverElapsed = safeLen - safeServerRemaining;
                if (serverElapsed < 0) serverElapsed = 0;
                if (serverElapsed > safeLen) serverElapsed = safeLen;

                int desiredOffsetMs = serverElapsed * 50;
                int driftMs = Math.abs(desiredOffsetMs - ss.offOffsetMs);
                int driftThresholdMs = TOLERANCE_TICKS * 50;

                if (driftMs >= driftThresholdMs) {
                    ss.offOffsetMs = desiredOffsetMs;
                    ss.offRequestTimeMs = nowMs;
                    ss.pendingInitialSeek = true;

                    logResyncDecision("waitingForOff_driftTooLarge_restart", storageUuid, safeServerRemaining, clientRemaining, delta, ss, safeLen);
                    play(center, url, storageUuid, entityId, serverElapsed, safeLen);
                }
            }

            return;
        }

        if (!desyncTooLarge) {
            return;
        }

        int serverElapsed = safeLen - safeServerRemaining;
        if (serverElapsed < 0) serverElapsed = 0;
        if (serverElapsed > safeLen) serverElapsed = safeLen;

        ss.offOffsetMs = serverElapsed * 50;
        ss.offRequestTimeMs = System.currentTimeMillis();
        ss.offState = OffState.WAITING_FOR_OFF;
        ss.pendingInitialSeek = true;
        ss.offAttempts++;

        logResyncDecision("desyncTooLarge_restartWithOff", storageUuid, safeServerRemaining, clientRemaining, delta, ss, safeLen);
        play(center, url, storageUuid, entityId, serverElapsed, safeLen);
    }

    private static void cleanupInactiveSoundsByTtl(long nowClientTicks) {
        if (soundsByUuid.isEmpty()) {
            return;
        }

        Set<UUID> all = new HashSet<>(soundsByUuid.keySet());
        for (UUID uuid : all) {
            if (uuid == null) continue;

            Long lastSeen = lastSeenClientTicksByUuid.get(uuid);
            if (lastSeen == null) {
                lastSeenClientTicksByUuid.put(uuid, nowClientTicks);
                continue;
            }

            long age = nowClientTicks - lastSeen;
            if (age > INACTIVE_TTL_TICKS) {
                clearByUuid(uuid);
            }
        }
    }

    private static void tickCleanupOnSync(UUID uuid, SessionState ss) {
        if (uuid == null || ss == null) return;
        if (!hasActiveUuid(uuid)) return;

        int clientElapsed = estimateClientElapsedTicks(uuid);
        if (clientElapsed >= 0 && clientElapsed > ss.discLengthTicks + LENGTH_OVERFLOW_MARGIN_TICKS) {
            clearByUuid(uuid);
        }
    }

    public static void updateTimingWithoutRestart(UUID uuid, int serverElapsedTicks, int discLengthTicks) {
        if (uuid == null || uuid.equals(Util.NIL_UUID)) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = (mc != null) ? mc.level : null;
        if (level == null) return;

        long now = level.getGameTime();
        int safeLen = Math.max(1, discLengthTicks);
        int safeElapsed = Math.max(0, Math.min(serverElapsedTicks, safeLen));

        startGameTimeTicksByUuid.put(uuid, now);
        startElapsedTicksByUuid.put(uuid, safeElapsed);
        discLengthTicksByUuid.put(uuid, safeLen);
    }

    private static String minecraftify(String url) {
        if (url == null) return "";
        return url.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }
}