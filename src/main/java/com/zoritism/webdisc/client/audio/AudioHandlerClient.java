package com.zoritism.webdisc.client.audio;

import com.zoritism.webdisc.util.WebHashing;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AudioHandlerClient {

    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

    private static final Map<String, CompletableFuture<Boolean>> DOWNLOAD_TASKS = new ConcurrentHashMap<>();
    private static final Map<String, DownloadContext> ACTIVE_CONTEXTS = new ConcurrentHashMap<>();

    private static final class DownloadContext {
        volatile Process ytDlpProc;
        volatile Process ffmpegProc;
        volatile boolean cancelled;
    }

    private static Path baseDir() {
        Minecraft mc = Minecraft.getInstance();
        File root = mc != null ? mc.gameDirectory : new File(".");
        return root.toPath().resolve("webdisc").resolve("audio_cache");
    }

    private static String minecraftify(String url) {
        if (url == null) return "";
        return url.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static String keyForUrl(String url) {
        return WebHashing.sha256(minecraftify(url));
    }

    public boolean hasOgg(String url) {
        String key = keyForUrl(url);
        File ogg = baseDir().resolve(key + ".ogg").toFile();
        return ogg.exists();
    }

    public void cancelDownload(String url) {
        String key = keyForUrl(url);
        DownloadContext ctx = ACTIVE_CONTEXTS.get(key);
        if (ctx != null) {
            ctx.cancelled = true;
            tryKill(ctx.ytDlpProc);
            tryKill(ctx.ffmpegProc);
        }
        CompletableFuture<Boolean> f = DOWNLOAD_TASKS.get(key);
        if (f != null) {
            f.cancel(true);
        }
    }

    public void deleteAllFilesForUrl(String url) {
        String key = keyForUrl(url);
        deleteAllByPrefix(key, null);
    }

    public void cleanupTempFilesAfterFinalize(String url) {
        String key = keyForUrl(url);
        deleteAllByPrefix(key, key + ".ogg");
    }

    private void deleteAllByPrefix(String keyPrefix, String keepExactName) {
        if (keyPrefix == null || keyPrefix.isEmpty()) return;

        File dir = baseDir().toFile();
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] entries = dir.listFiles();
        if (entries == null) return;

        for (File f : entries) {
            if (f == null) continue;
            String name = f.getName();
            if (!name.startsWith(keyPrefix)) continue;
            if (keepExactName != null && keepExactName.equals(name)) continue;

            deleteRecursively(f);
        }
    }

    private void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        try {
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    for (File c : children) {
                        deleteRecursively(c);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    private static void tryKill(Process p) {
        if (p == null) return;
        try {
            p.destroyForcibly();
        } catch (Throwable ignored) {}
    }

    public CompletableFuture<Boolean> downloadAsOgg(String url) {
        String key = keyForUrl(url);

        CompletableFuture<Boolean> existing = DOWNLOAD_TASKS.get(key);
        if (existing != null) {
            return existing;
        }

        logger.info("[WebDisc] downloadAsOgg start url='{}' key='{}'", url, key);

        CompletableFuture<Boolean> task = CompletableFuture.supplyAsync(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            DownloadContext ctx = new DownloadContext();
            ACTIVE_CONTEXTS.put(key, ctx);

            try {
                Path dir = baseDir();
                File dirFile = dir.toFile();
                if (!dirFile.exists() && !dirFile.mkdirs()) {
                    logger.info("[WebDisc] downloadAsOgg failed to create cache dir path='{}'", dir.toAbsolutePath());
                    return false;
                }

                File oggOut = dir.resolve(key + ".ogg").toFile();
                if (oggOut.exists()) {
                    return true;
                }

                String tempBase = dir.resolve(key).toString();

                String ffmpegPath = null;
                try {
                    ffmpegPath = FFmpegHelper.getFfmpegPath();
                } catch (Throwable ignored) {}

                if (ffmpegPath != null && !ffmpegPath.isEmpty()) {
                    logger.info("[WebDisc] yt-dlp will use ffmpeg at '{}'", ffmpegPath);
                } else {
                    logger.info("[WebDisc] yt-dlp ffmpeg path is null (FFmpegHelper.getFfmpegPath returned null)");
                }

                String inPath;
                try {
                    inPath = runYtDlp(ctx, ffmpegPath,
                            "-S", "res:144",
                            "--no-playlist",
                            "-o", tempBase,
                            url,
                            "--print", "after_move:filepath"
                    );
                } catch (Throwable t) {
                    if (ctx.cancelled) return false;
                    logger.info("[WebDisc] yt-dlp failed url='{}' key='{}' err='{}'", url, key, t.toString());
                    return false;
                }

                if (ctx.cancelled) return false;
                if (inPath == null || inPath.isEmpty()) {
                    logger.info("[WebDisc] yt-dlp returned empty filepath url='{}' key='{}'", url, key);
                    return false;
                }

                try {
                    runFfmpeg(ctx,
                            "-i", inPath,
                            "-c:a", "libvorbis",
                            "-ac", "1",
                            "-b:a", "64k",
                            "-vn",
                            "-y",
                            "-nostdin",
                            "-nostats",
                            "-loglevel", "0",
                            oggOut.getAbsolutePath()
                    );
                } catch (Throwable t) {
                    if (ctx.cancelled) return false;
                    logger.info("[WebDisc] ffmpeg failed url='{}' key='{}' err='{}'", url, key, t.toString());
                    return false;
                }

                if (ctx.cancelled) return false;
                return oggOut.exists();
            } catch (Throwable t) {
                if (ctx.cancelled) return false;
                logger.info("[WebDisc] downloadAsOgg unexpected error url='{}' key='{}' err='{}'", url, key, t.toString());
                return false;
            } finally {
                ACTIVE_CONTEXTS.remove(key);
            }
        });

        task.whenComplete((res, err) -> {
            try {
                DOWNLOAD_TASKS.remove(key);
            } catch (Throwable ignored) {}
            if (err != null) {
                logger.info("[WebDisc] downloadAsOgg completed exceptionally url='{}' key='{}' err='{}'", url, key, err.toString());
            } else {
                logger.info("[WebDisc] downloadAsOgg completed url='{}' key='{}' success={}", url, key, Boolean.TRUE.equals(res));
            }
        });

        CompletableFuture<Boolean> prev = DOWNLOAD_TASKS.putIfAbsent(key, task);
        if (prev != null) {
            return prev;
        }
        return task;
    }

    private String runYtDlp(DownloadContext ctx, String ffmpegPath, String... args) throws Exception {
        YoutubeDLHelper.ensureExecutablePublic();
        String exe = YoutubeDLHelper.getYtDlpPath();
        if (exe == null || exe.isEmpty() || !(new File(exe).canExecute())) {
            throw new IllegalStateException("yt-dlp executable not available");
        }

        // Если ffmpeg есть — явно подсказываем yt-dlp, где он лежит.
        // Это убирает WARNING: ffmpeg not found даже когда ffmpeg не в PATH и не рядом с yt-dlp.exe.
        String[] finalArgs;
        if (ffmpegPath != null && !ffmpegPath.isEmpty()) {
            finalArgs = new String[args.length + 2];
            finalArgs[0] = "--ffmpeg-location";
            finalArgs[1] = ffmpegPath;
            System.arraycopy(args, 0, finalArgs, 2, args.length);
        } else {
            finalArgs = args;
        }

        Process proc = YoutubeDLHelper.startProcess(exe, finalArgs);
        ctx.ytDlpProc = proc;

        String out = YoutubeDLHelper.readAll(proc.getInputStream());
        int code = proc.waitFor();

        if (ctx.cancelled) return "";
        if (code != 0) {
            String err = YoutubeDLHelper.readAll(proc.getErrorStream());
            logger.info("[WebDisc] yt-dlp exit code={} stderr='{}' stdout='{}'", code, err, out);
            throw new RuntimeException("yt-dlp exit code " + code);
        }
        return out.trim();
    }

    private void runFfmpeg(DownloadContext ctx, String... args) throws Exception {
        FFmpegHelper.ensureExecutablePublic();
        String exe = FFmpegHelper.getFfmpegPath();
        if (exe == null || exe.isEmpty() || !(new File(exe).canExecute())) {
            throw new IllegalStateException("ffmpeg executable not available");
        }

        Process proc = FFmpegHelper.startProcess(exe, args);
        ctx.ffmpegProc = proc;

        int code = proc.waitFor();
        if (ctx.cancelled) return;
        if (code != 0) {
            String err = FFmpegHelper.readAll(proc.getErrorStream());
            logger.info("[WebDisc] ffmpeg exit code={} stderr='{}'", code, err);
            throw new RuntimeException("ffmpeg exited with code " + code);
        }
    }

    public InputStream openOgg(String url) {
        String key = keyForUrl(url);
        return openOggByKey(key);
    }

    public InputStream openOggByKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        File ogg = baseDir().resolve(key + ".ogg").toFile();
        try {
            return new FileInputStream(ogg);
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}