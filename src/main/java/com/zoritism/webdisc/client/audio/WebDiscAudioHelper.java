package com.zoritism.webdisc.client.audio;

import com.mojang.blaze3d.audio.OggAudioStream;
import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.WebDiscMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class WebDiscAudioHelper {

    private WebDiscAudioHelper() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final long MAX_FILE_AGE_DAYS = 3L;
    private static final long MAX_CACHE_BYTES = 1L * 1024L * 1024L * 1024L;
    private static final long OFFSET_FALLBACK_TIMEOUT_MS = 20_000L;

    private static final Map<String, Long> OFFSET_REQUEST_DEADLINES = new HashMap<>();
    private static final Map<String, CompletableFuture<Void>> activeOffsetCuts = new ConcurrentHashMap<>();

    private enum OffsetState {
        PENDING,
        READY
    }

    private static final Map<String, OffsetState> OFFSET_STATES = new ConcurrentHashMap<>();

    public static Optional<String> findInPath(String filename) {
        String separator = SystemUtils.IS_OS_UNIX ? ":" : ";";
        String path = System.getenv("PATH");
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }

        String[] dirs = path.split(separator);
        for (String dir : dirs) {
            try {
                Path candidate = Paths.get(dir, filename);
                File f = candidate.toFile();
                if (f.exists()) {
                    return Optional.of(f.getAbsolutePath());
                }
            } catch (Throwable ignored) {}
        }
        return Optional.empty();
    }

    public static CompletableFuture<AudioStream> getStream(ResourceLocation location, boolean wrapLoop) {
        return getStream(location, wrapLoop, 0);
    }

    private static Path audioCacheDir() {
        Minecraft mc = Minecraft.getInstance();
        File root = mc != null ? mc.gameDirectory : new File(".");
        return root.toPath().resolve("webdisc").resolve("audio_cache");
    }

    private static String normalizeKey(String key) {
        if (key == null) return "";
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    public static void cleanupOffsetFilesForKey(String urlKey) {
        if (urlKey == null || urlKey.isEmpty()) {
            return;
        }
        try {
            Path cacheDirPath = audioCacheDir();
            File cacheDir = cacheDirPath.toFile();
            if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                return;
            }
            String prefix = urlKey + "_off";
            File[] toDelete = cacheDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(prefix) && name.endsWith(".ogg");
                }
            });
            if (toDelete == null || toDelete.length == 0) {
                return;
            }
            for (File f : toDelete) {
                try {
                    if (f.delete()) {
                        LOGGER.info("[WebDisc] cleanupOffsetFilesForKey: deleted temp offset file '{}'", f.getAbsolutePath());
                    }
                } catch (Throwable ignored) {}
            }

            try {
                synchronized (OFFSET_STATES) {
                    Iterator<String> it = OFFSET_STATES.keySet().iterator();
                    while (it.hasNext()) {
                        String offKey = it.next();
                        if (offKey != null && offKey.startsWith(urlKey + "_off")) {
                            it.remove();
                        }
                    }
                }
            } catch (Throwable ignored) {}

            try {
                synchronized (OFFSET_REQUEST_DEADLINES) {
                    Iterator<String> it = OFFSET_REQUEST_DEADLINES.keySet().iterator();
                    while (it.hasNext()) {
                        String offKey = it.next();
                        if (offKey != null && offKey.startsWith(urlKey + "_off")) {
                            it.remove();
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LOGGER.info("[WebDisc] cleanupOffsetFilesForKey: failed for urlKey='{}': {}", urlKey, t.toString());
        }
    }

    public static void cleanupAllOffsetFiles() {
        try {
            Path cacheDirPath = audioCacheDir();
            File cacheDir = cacheDirPath.toFile();
            if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                LOGGER.info("[WebDisc] cleanupAllOffsetFiles: cache dir does not exist or is not a directory: '{}'", cacheDir.getAbsolutePath());
                return;
            }
            File[] toDelete = cacheDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".ogg") && name.contains("_off");
                }
            });
            if (toDelete == null) {
                LOGGER.info("[WebDisc] cleanupAllOffsetFiles: listFiles returned null for dir '{}'", cacheDir.getAbsolutePath());
                return;
            }
            if (toDelete.length == 0) {
                LOGGER.info("[WebDisc] cleanupAllOffsetFiles: no _off files found in dir '{}'", cacheDir.getAbsolutePath());
                return;
            }
            LOGGER.info("[WebDisc] cleanupAllOffsetFiles: found {} _off files to delete", toDelete.length);
            for (File f : toDelete) {
                try {
                    boolean deleted = f.delete();
                    if (deleted) {
                        LOGGER.info("[WebDisc] cleanupAllOffsetFiles: deleted temp offset file '{}'", f.getAbsolutePath());
                    } else {
                        LOGGER.info("[WebDisc] cleanupAllOffsetFiles: FAILED to delete temp offset file '{}' (delete() returned false)", f.getAbsolutePath());
                    }
                } catch (Throwable t) {
                    LOGGER.info("[WebDisc] cleanupAllOffsetFiles: exception while deleting file '{}': {}", f.getAbsolutePath(), t.toString());
                }
            }

            resetClientStates();
        } catch (Throwable t) {
            LOGGER.info("[WebDisc] cleanupAllOffsetFiles: failed: {}", t.toString());
        }
    }

    public static void cleanupCacheOnClientInit() {
        try {
            Path cacheDirPath = audioCacheDir();
            File cacheDir = cacheDirPath.toFile();
            if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                return;
            }

            LOGGER.info("[WebDisc] cleanupCacheOnClientInit: start, dir='{}'", cacheDir.getAbsolutePath());

            cleanupAllOffsetFiles();

            File[] all = cacheDir.listFiles();
            if (all == null || all.length == 0) {
                LOGGER.info("[WebDisc] cleanupCacheOnClientInit: cache empty after off-cleanup");
                return;
            }

            long nowMillis = System.currentTimeMillis();
            Instant now = Instant.ofEpochMilli(nowMillis);
            long maxAgeMillis = ChronoUnit.MILLIS.between(now.minus(MAX_FILE_AGE_DAYS, ChronoUnit.DAYS), now);

            long totalBytes = 0L;
            List<FileInfo> candidates = new ArrayList<>();

            for (File f : all) {
                if (f == null || !f.isFile()) continue;
                String name = f.getName();
                if (!name.endsWith(".ogg")) continue;
                if (name.contains("_off")) continue;

                long size = f.length();
                totalBytes += size;

                try {
                    BasicFileAttributes attrs = java.nio.file.Files.readAttributes(f.toPath(), BasicFileAttributes.class);
                    long ctime = attrs.creationTime().toMillis();
                    long mtime = attrs.lastModifiedTime().toMillis();
                    long ts = Math.min(ctime, mtime);
                    long ageMillis = nowMillis - ts;

                    if (ageMillis > maxAgeMillis) {
                        if (f.delete()) {
                            totalBytes -= size;
                            LOGGER.info("[WebDisc] cleanupCacheOnClientInit: deleted old file '{}' ({} bytes, age={} ms)",
                                    f.getAbsolutePath(), size, ageMillis);
                        }
                    } else {
                        candidates.add(new FileInfo(f, ts, size));
                    }
                } catch (Throwable t) {
                    candidates.add(new FileInfo(f, f.lastModified(), size));
                }
            }

            long recalculated = 0L;
            for (FileInfo info : candidates) {
                if (info.file.exists() && info.file.isFile()) {
                    recalculated += info.size;
                }
            }
            totalBytes = recalculated;

            LOGGER.info("[WebDisc] cleanupCacheOnClientInit: size after age-cleanup = {} bytes", totalBytes);

            if (totalBytes > MAX_CACHE_BYTES && !candidates.isEmpty()) {
                candidates.sort(Comparator.comparingLong(fi -> fi.timestamp));
                for (FileInfo info : candidates) {
                    if (totalBytes <= MAX_CACHE_BYTES) break;
                    File f = info.file;
                    if (!f.exists() || !f.isFile()) continue;
                    long sz = info.size;
                    try {
                        if (f.delete()) {
                            totalBytes -= sz;
                            LOGGER.info("[WebDisc] cleanupCacheOnClientInit: deleted for size limit '{}' ({} bytes), total now {}",
                                    f.getAbsolutePath(), sz, totalBytes);
                        }
                    } catch (Throwable ignored) {}
                }
            }

            LOGGER.info("[WebDisc] cleanupCacheOnClientInit: finished, final size = {} bytes", totalBytes);
        } catch (Throwable t) {
            LOGGER.info("[WebDisc] cleanupCacheOnClientInit: failed: {}", t.toString());
        }
    }

    private static final class FileInfo {
        final File file;
        final long timestamp;
        final long size;

        FileInfo(File file, long timestamp, long size) {
            this.file = file;
            this.timestamp = timestamp;
            this.size = size;
        }
    }

    public static boolean isOffsetFilePresent(String urlKey, int offsetMs) {
        if (urlKey == null || urlKey.isEmpty()) return false;
        if (offsetMs <= 0) return true;

        String offKey = urlKey + "_off" + offsetMs;
        try {
            File f = audioCacheDir().resolve(offKey + ".ogg").toFile();
            return f.exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static CompletableFuture<AudioStream> getStream(ResourceLocation location, boolean wrapLoop, int offsetMs) {
        if (location == null) return null;
        if (!WebDiscMod.MODID.equals(location.getNamespace())) return null;

        String path = location.getPath();
        if (path == null || path.isEmpty()) return null;
        if (path.contains("placeholder_sound.ogg")) return null;

        String normalizedPath = path;
        if (normalizedPath.startsWith("sounds/")) {
            normalizedPath = normalizedPath.substring("sounds/".length());
        }

        if (!normalizedPath.startsWith("customsound/")) {
            return null;
        }

        String rest = normalizedPath.substring("customsound/".length());
        if (rest.endsWith(".ogg")) {
            rest = rest.substring(0, rest.length() - 4);
        }

        final String urlKey = normalizeKey(rest);

        int safeOffsetMs = Math.max(0, offsetMs);
        int maxOffsetMs = 10 * 60 * 1000;
        if (safeOffsetMs > maxOffsetMs) {
            safeOffsetMs = maxOffsetMs;
        }
        final int finalOffsetMs = safeOffsetMs;

        return CompletableFuture.supplyAsync(() -> {
            try {
                AudioHandlerClient handler = new AudioHandlerClient();

                Path cacheDir = audioCacheDir();
                File originalFile = cacheDir.resolve(urlKey + ".ogg").toFile();

                if (!originalFile.exists()) {
                    InputStream in = handler.openOggByKey(urlKey);
                    if (in == null) {
                        return null;
                    }
                    return wrapLoop ? new LoopingAudioStream(OggAudioStream::new, in) : new OggAudioStream(in);
                }

                if (finalOffsetMs <= 0) {
                    InputStream in = handler.openOggByKey(urlKey);
                    if (in == null) {
                        return null;
                    }
                    return wrapLoop ? new LoopingAudioStream(OggAudioStream::new, in) : new OggAudioStream(in);
                }

                String offKey = urlKey + "_off" + finalOffsetMs;
                File offsetFile = cacheDir.resolve(offKey + ".ogg").toFile();

                if (offsetFile.exists()) {
                    InputStream in = handler.openOggByKey(offKey);
                    if (in == null) {
                        InputStream baseIn = handler.openOggByKey(urlKey);
                        if (baseIn == null) {
                            return null;
                        }
                        return wrapLoop ? new LoopingAudioStream(OggAudioStream::new, baseIn) : new OggAudioStream(baseIn);
                    }
                    try {
                        synchronized (OFFSET_REQUEST_DEADLINES) {
                            OFFSET_REQUEST_DEADLINES.remove(offKey);
                        }
                    } catch (Throwable ignored) {}

                    try {
                        synchronized (OFFSET_STATES) {
                            OFFSET_STATES.put(offKey, OffsetState.READY);
                        }
                    } catch (Throwable ignored) {}

                    return wrapLoop ? new LoopingAudioStream(OggAudioStream::new, in) : new OggAudioStream(in);
                }

                long nowMs = System.currentTimeMillis();
                long deadline;
                try {
                    synchronized (OFFSET_REQUEST_DEADLINES) {
                        Long existing = OFFSET_REQUEST_DEADLINES.get(offKey);
                        if (existing == null) {
                            deadline = nowMs + OFFSET_FALLBACK_TIMEOUT_MS;
                            OFFSET_REQUEST_DEADLINES.put(offKey, deadline);
                        } else {
                            deadline = existing;
                        }
                    }
                } catch (Throwable t) {
                    deadline = nowMs + OFFSET_FALLBACK_TIMEOUT_MS;
                }

                try {
                    synchronized (activeOffsetCuts) {
                        CompletableFuture<Void> existingTask = activeOffsetCuts.get(offKey);
                        if (existingTask == null || existingTask.isDone()) {
                            CompletableFuture<Void> newTask = scheduleOffsetCut(originalFile, offsetFile, finalOffsetMs, urlKey, offKey);
                            activeOffsetCuts.put(offKey, newTask);

                            newTask.whenComplete((v, t) -> {
                                synchronized (activeOffsetCuts) {
                                    activeOffsetCuts.remove(offKey);
                                }
                            });
                        }
                    }
                } catch (Throwable ignored) {}

                InputStream in = handler.openOggByKey(urlKey);
                if (in == null) {
                    return null;
                }

                if (nowMs < deadline) {
                    try {
                        synchronized (OFFSET_STATES) {
                            OffsetState prev = OFFSET_STATES.get(offKey);
                            if (prev == null || prev != OffsetState.READY) {
                                OFFSET_STATES.put(offKey, OffsetState.PENDING);
                            }
                        }
                    } catch (Throwable ignored) {}
                    return wrapLoop ? new LoopingAudioStream(OggAudioStream::new, in) : new OggAudioStream(in);
                }

                return wrapLoop ? new LoopingAudioStream(OggAudioStream::new, in) : new OggAudioStream(in);
            } catch (Throwable t) {
                throw new CompletionException(t);
            }
        }, Util.backgroundExecutor());
    }

    private static CompletableFuture<Void> scheduleOffsetCut(File original, File offsetFile, int offsetMs, String urlKey, String offKey) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!original.exists()) {
                    return;
                }
                File parent = offsetFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return;
                }

                String offsetSeconds = String.format(Locale.ROOT, "%.3f", offsetMs / 1000.0);

                FFmpegHelper.run(
                        "-ss", offsetSeconds,
                        "-i", original.getAbsolutePath(),
                        "-c", "copy",
                        "-vn",
                        "-y",
                        "-nostdin",
                        "-nostats",
                        "-loglevel", "0",
                        offsetFile.getAbsolutePath()
                );

                boolean ok = offsetFile.exists();
                if (ok) {
                    try {
                        synchronized (OFFSET_STATES) {
                            OFFSET_STATES.put(offKey, OffsetState.READY);
                        }
                    } catch (Throwable ignored) {}

                    try {
                        synchronized (OFFSET_REQUEST_DEADLINES) {
                            OFFSET_REQUEST_DEADLINES.remove(offKey);
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                LOGGER.info("[WebDisc] scheduleOffsetCut: ffmpeg failed for key='{}', offKey='{}': {}", urlKey, offKey, t.toString());
            }
        }, Util.backgroundExecutor());
    }

    public static boolean isOffsetReady(String urlKey, int offsetMs) {
        if (urlKey == null || urlKey.isEmpty()) {
            return true;
        }
        if (offsetMs <= 0) {
            return true;
        }

        String offKey = urlKey + "_off" + offsetMs;

        try {
            Path dir = audioCacheDir();
            File f = dir.resolve(offKey + ".ogg").toFile();
            if (f.exists()) {
                try {
                    synchronized (OFFSET_STATES) {
                        OFFSET_STATES.put(offKey, OffsetState.READY);
                    }
                } catch (Throwable ignored) {}
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            synchronized (OFFSET_STATES) {
                OffsetState st = OFFSET_STATES.get(offKey);
                return st == OffsetState.READY;
            }
        } catch (Throwable t) {
            LOGGER.info("[WebDisc] isOffsetReady: failed for offKey='{}': {}", offKey, t.toString());
            return true;
        }
    }

    public static boolean isOffsetPending(String urlKey, int offsetMs) {
        if (urlKey == null || urlKey.isEmpty()) {
            return false;
        }
        if (offsetMs <= 0) {
            return false;
        }

        String offKey = urlKey + "_off" + offsetMs;

        try {
            Path dir = audioCacheDir();
            File f = dir.resolve(offKey + ".ogg").toFile();
            if (f.exists()) {
                try {
                    synchronized (OFFSET_STATES) {
                        OFFSET_STATES.put(offKey, OffsetState.READY);
                    }
                } catch (Throwable ignored) {}
                return false;
            }
        } catch (Throwable ignored) {}

        try {
            synchronized (OFFSET_STATES) {
                OffsetState st = OFFSET_STATES.get(offKey);
                return st == OffsetState.PENDING;
            }
        } catch (Throwable t) {
            LOGGER.info("[WebDisc] isOffsetPending: failed for offKey='{}': {}", offKey, t.toString());
            return false;
        }
    }

    public static void resetClient() {
        resetClientStates();
    }

    private static void resetClientStates() {
        try {
            synchronized (OFFSET_STATES) {
                OFFSET_STATES.clear();
            }
        } catch (Throwable ignored) {}
        try {
            synchronized (OFFSET_REQUEST_DEADLINES) {
                OFFSET_REQUEST_DEADLINES.clear();
            }
        } catch (Throwable ignored) {}
        try {
            synchronized (activeOffsetCuts) {
                activeOffsetCuts.clear();
            }
        } catch (Throwable ignored) {}
    }
}