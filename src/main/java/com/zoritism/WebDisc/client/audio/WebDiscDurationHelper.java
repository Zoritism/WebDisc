package com.zoritism.webdisc.client.audio;

import com.zoritism.webdisc.WebDiscMod;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Определение длительности .ogg через ffmpeg.
 * Работает по уже скачанному файлу в webdisc/client_downloads.
 * Возвращает длительность в тиках (20 тиков = 1 сек), с ограничениями:
 * - минимум 10 секунд (200 тиков);
 * - максимум 10 минут (600 секунд, 12000 тиков).
 */
public final class WebDiscDurationHelper {

    private WebDiscDurationHelper() {}

    private static final double MIN_SECONDS = 10.0;
    private static final double MAX_SECONDS = 600.0;
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_TICKS = (int) (MIN_SECONDS * TICKS_PER_SECOND);
    private static final int MAX_TICKS = (int) (MAX_SECONDS * TICKS_PER_SECOND);

    public static int getLengthTicksForUrl(String url) {
        try {
            if (url == null || url.isBlank()) return -1;

            // Такой же key, как в AudioHandlerClient
            String key = com.zoritism.webdisc.util.WebHashing.sha256(minecraftify(url));

            Minecraft mc = Minecraft.getInstance();
            java.io.File root = mc != null ? mc.gameDirectory : new java.io.File(".");

            // ВАЖНО: использовать тот же путь, что и AudioHandlerClient.baseDir():
            // root.toPath().resolve("webdisc").resolve("client_downloads");
            Path dir = root.toPath().resolve("webdisc").resolve("client_downloads");
            java.io.File ogg = dir.resolve(key + ".ogg").toFile();
            if (!ogg.exists()) {
                WebDiscMod.LOGGER.info("[WebDisc] Duration: OGG file not found at {}", ogg.getAbsolutePath());
                return -1;
            }

            String ffmpeg = findFfmpegExecutable();
            if (ffmpeg == null) {
                WebDiscMod.LOGGER.info("[WebDisc] Duration: ffmpeg not available");
                return -1;
            }

            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(ffmpeg);
            java.util.Collections.addAll(cmd,
                    "-i", ogg.getAbsolutePath(),
                    "-show_entries", "format=duration",
                    "-v", "quiet",
                    "-of", "csv=p=0"
            );

            Process proc;
            if (SystemUtils.IS_OS_LINUX) {
                String joined = String.join(" ", cmd);
                proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", joined});
            } else {
                proc = Runtime.getRuntime().exec(cmd.toArray(new String[0]));
            }

            StringBuilder out = new StringBuilder();
            try (BufferedReader rdr = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = rdr.readLine()) != null) {
                    out.append(line.trim());
                }
            }

            int code = proc.waitFor();
            if (code != 0) {
                return -1;
            }

            String s = out.toString().trim();
            if (s.isEmpty()) return -1;

            double seconds = Double.parseDouble(s);
            if (seconds <= 0) return -1;

            if (seconds < MIN_SECONDS) seconds = MIN_SECONDS;
            if (seconds > MAX_SECONDS) seconds = MAX_SECONDS;

            int ticks = (int) Math.round(seconds * TICKS_PER_SECOND);
            if (ticks < MIN_TICKS) ticks = MIN_TICKS;
            if (ticks > MAX_TICKS) ticks = MAX_TICKS;

            WebDiscMod.LOGGER.info("[WebDisc] Duration probe OK: {} sec ({} ticks)", seconds, ticks);
            return ticks;
        } catch (Throwable t) {
            WebDiscMod.LOGGER.info("[WebDisc] URL duration probe failed: {}", t.toString());
            return -1;
        }
    }

    private static String minecraftify(String url) {
        if (url == null) return "";
        return url.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static String findFfmpegExecutable() {
        try {
            var found = WebDiscAudioHelper.findInPath("ffmpeg");
            if (found.isPresent()) {
                return found.get();
            }

            Minecraft mc = Minecraft.getInstance();
            java.io.File root = mc != null ? mc.gameDirectory : new java.io.File(".");
            Path dir = root.toPath().resolve("WebDisc").resolve("ffmpeg");
            String fileName = SystemUtils.IS_OS_WINDOWS ? "ffmpeg.exe" : "ffmpeg";
            java.io.File localExe = dir.resolve(fileName).toFile();
            if (localExe.exists() && (SystemUtils.IS_OS_WINDOWS || localExe.canExecute())) {
                return localExe.getAbsolutePath();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}