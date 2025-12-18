package com.zoritism.webdisc.client.audio;

import com.zoritism.webdisc.WebDiscMod;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Определение длительности .ogg.
 * 1) Пытаемся использовать ffprobe (если есть рядом с ffmpeg или в PATH).
 * 2) Если ffprobe нет — парсим stderr от ffmpeg по строке "Duration: hh:mm:ss.xx".
 *
 * Возвращает длительность в тиках (20 тиков = 1 сек) с ограничениями:
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

            String key = com.zoritism.webdisc.util.WebHashing.sha256(minecraftify(url));

            Minecraft mc = Minecraft.getInstance();
            File root = mc != null ? mc.gameDirectory : new File(".");
            Path dir = root.toPath().resolve("webdisc").resolve("client_downloads");
            File ogg = dir.resolve(key + ".ogg").toFile();
            if (!ogg.exists()) {
                WebDiscMod.LOGGER.info("[WebDisc] Duration: OGG file not found at {}", ogg.getAbsolutePath());
                return -1;
            }

            double seconds = probeWithFfprobe(ogg);
            if (seconds <= 0) {
                seconds = probeWithFfmpeg(ogg);
            }
            if (seconds <= 0) {
                return -1;
            }

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

    private static double probeWithFfprobe(File ogg) {
        try {
            String ffmpegPath = FFmpegHelper.getFfmpegPath();
            String ffprobe = findFfprobeExecutable(ffmpegPath);
            if (ffprobe == null) {
                return -1;
            }

            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(ffprobe);
            java.util.Collections.addAll(cmd,
                    "-v", "error",
                    "-select_streams", "a:0",
                    "-show_entries", "stream=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    ogg.getAbsolutePath()
            );

            WebDiscMod.LOGGER.info("[WebDisc] Duration: running ffprobe cmd: {}", String.join(" ", cmd));

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
            String raw = out.toString().trim();
            WebDiscMod.LOGGER.info("[WebDisc] Duration: ffprobe exitCode={}, stdout='{}'", code, raw);

            if (code != 0 || raw.isEmpty()) {
                return -1;
            }
            return Double.parseDouble(raw);
        } catch (Throwable t) {
            WebDiscMod.LOGGER.info("[WebDisc] Duration: ffprobe failed: {}", t.toString());
            return -1;
        }
    }

    private static double probeWithFfmpeg(File ogg) {
        try {
            String ffmpegPath = FFmpegHelper.getFfmpegPath();
            if (ffmpegPath == null) {
                WebDiscMod.LOGGER.info("[WebDisc] Duration: ffmpeg not available");
                return -1;
            }

            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(ffmpegPath);
            java.util.Collections.addAll(cmd,
                    "-i", ogg.getAbsolutePath(),
                    "-f", "null",
                    "-" // вывод в /dev/null / NUL
            );

            WebDiscMod.LOGGER.info("[WebDisc] Duration: running ffmpeg cmd: {}", String.join(" ", cmd));

            Process proc;
            if (SystemUtils.IS_OS_LINUX) {
                String joined = String.join(" ", cmd);
                proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", joined});
            } else {
                proc = Runtime.getRuntime().exec(cmd.toArray(new String[0]));
            }

            double seconds = -1.0;
            try (BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                String line;
                while ((line = err.readLine()) != null) {
                    line = line.trim();
                    if (line.contains("Duration:")) {
                        // пример: "Duration: 00:03:25.28, start: 0.000000, bitrate: 192 kb/s"
                        int idx = line.indexOf("Duration:");
                        if (idx >= 0) {
                            String after = line.substring(idx + "Duration:".length()).trim();
                            int comma = after.indexOf(',');
                            String time = (comma >= 0) ? after.substring(0, comma).trim() : after;
                            seconds = parseHmsTimeToSeconds(time);
                            break;
                        }
                    }
                }
            }

            int code = proc.waitFor();
            WebDiscMod.LOGGER.info("[WebDisc] Duration: ffmpeg exitCode={}, parsedSeconds={}", code, seconds);
            if (seconds <= 0) {
                return -1;
            }
            return seconds;
        } catch (Throwable t) {
            WebDiscMod.LOGGER.info("[WebDisc] Duration: ffmpeg stderr parse failed: {}", t.toString());
            return -1;
        }
    }

    private static double parseHmsTimeToSeconds(String t) {
        try {
            String v = t.trim();
            if (v.isEmpty()) return -1;
            String[] parts = v.split(":", 3);
            if (parts.length != 3) return -1;
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            double s = Double.parseDouble(parts[2]);
            return h * 3600.0 + m * 60.0 + s;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String minecraftify(String url) {
        if (url == null) return "";
        return url.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static String findFfprobeExecutable(String ffmpegPath) {
        try {
            // 1) Если ffmpegPath известен — пробуем ffprobe рядом
            if (ffmpegPath != null) {
                File ff = new File(ffmpegPath);
                File dir = ff.getParentFile();
                if (dir != null) {
                    String probeName = SystemUtils.IS_OS_WINDOWS ? "ffprobe.exe" : "ffprobe";
                    File probe = new File(dir, probeName);
                    if (probe.exists() && (SystemUtils.IS_OS_WINDOWS || probe.canExecute())) {
                        return probe.getAbsolutePath();
                    }
                }
            }

            // 2) Иначе поиск в PATH
            var fromPath = WebDiscAudioHelper.findInPath(SystemUtils.IS_OS_WINDOWS ? "ffprobe.exe" : "ffprobe");
            return fromPath.orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}