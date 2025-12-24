package com.zoritism.webdisc.client.audio;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.config.ModConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FFmpegHelper {
    private static String ffmpegPath;
    private static final Logger LOGGER = LogUtils.getLogger();

    private FFmpegHelper() {}

    private static Path baseDir() {
        Minecraft mc = Minecraft.getInstance();
        File root = mc != null ? mc.gameDirectory : new File(".");
        return root.toPath().resolve("webdisc").resolve("ffmpeg");
    }

    private static void ensureExecutable() throws Exception {
        if (ffmpegPath != null && new File(ffmpegPath).canExecute()) {
            return;
        }

        var found = WebDiscAudioHelper.findInPath("ffmpeg");
        if (found.isPresent()) {
            ffmpegPath = found.get();
            return;
        }

        Path dir = baseDir();
        dir.toFile().mkdirs();
        String fileName = SystemUtils.IS_OS_WINDOWS ? "ffmpeg.exe" : "ffmpeg";
        File localExe = dir.resolve(fileName).toFile();
        Minecraft mc = Minecraft.getInstance();

        if (!localExe.exists()) {
            if (!ModConfigHandler.COMMON.webdiscDownloadFFmpeg.get()) {
                if (mc != null && mc.player != null) {
                    mc.player.sendSystemMessage(Component.translatable("webdisc.ffmpeg.missing"));
                }
                logMissingInstructions();
                return;
            }

            URL src = null;
            if (SystemUtils.IS_OS_MAC) {
                src = new URL("https://evermeet.cx/ffmpeg/ffmpeg-6.1.zip");
            } else if (SystemUtils.IS_OS_WINDOWS) {
                src = new URL("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip");
            }

            if (src == null) {
                return;
            }

            Path zipPath = dir.resolve("ffmpeg.zip");
            try (var in = src.openStream()) {
                Files.copy(in, zipPath, StandardCopyOption.REPLACE_EXISTING);
            }

            try (var zip = new java.util.zip.ZipInputStream(new FileInputStream(zipPath.toFile()))) {
                java.util.zip.ZipEntry e = zip.getNextEntry();
                while (e != null) {
                    if (e.getName().endsWith("ffmpeg.exe") || e.getName().endsWith("ffmpeg")) {
                        Path out = dir.resolve(fileName);
                        Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                        ffmpegPath = out.toString();
                        out.toFile().setExecutable(true);
                    }
                    e = zip.getNextEntry();
                }
            }

            Files.deleteIfExists(zipPath);
        }

        if (ffmpegPath == null) {
            if (localExe.exists() && (SystemUtils.IS_OS_WINDOWS || localExe.canExecute())) {
                ffmpegPath = localExe.getAbsolutePath();
                return;
            }
            if (mc != null && mc.player != null) {
                mc.player.sendSystemMessage(Component.translatable("webdisc.executable.permission", localExe.getName()));
            }
        }
    }

    private static void logMissingInstructions() {
        String[] lines;
        if (SystemUtils.IS_OS_WINDOWS) {
            lines = new String[]{
                    "FFmpeg not found for WebDisc.",
                    "Download from e.g. https://www.gyan.dev/ffmpeg/builds/ and place ffmpeg.exe into WebDisc/ffmpeg"
            };
        } else if (SystemUtils.IS_OS_MAC) {
            lines = new String[]{
                    "FFmpeg not found for WebDisc.",
                    "Download a macOS ffmpeg build and place the 'ffmpeg' binary into WebDisc/ffmpeg"
            };
        } else if (SystemUtils.IS_OS_LINUX) {
            lines = new String[]{
                    "FFmpeg not found for WebDisc.",
                    "Install ffmpeg via your package manager or put executable into WebDisc/ffmpeg"
            };
        } else {
            lines = new String[]{"FFmpeg not found for WebDisc."};
        }
        for (String s : lines) {
            LOGGER.info("[WebDisc] {}", s);
        }
    }

    // --- new API for cancellable downloads ---

    public static void ensureExecutablePublic() throws Exception {
        ensureExecutable();
    }

    public static Process startProcess(String exePath, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(exePath);
        Collections.addAll(cmd, args);

        if (SystemUtils.IS_OS_LINUX) {
            String joined = String.join(" ", cmd);
            return Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", joined});
        }
        return Runtime.getRuntime().exec(cmd.toArray(new String[0]));
    }

    public static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader rdr = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = rdr.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    public static void run(String... args) throws Exception {
        ensureExecutable();
        if (ffmpegPath == null || !new File(ffmpegPath).canExecute()) {
            throw new IllegalStateException("ffmpeg executable not available");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        Collections.addAll(cmd, args);

        Process proc;
        if (SystemUtils.IS_OS_LINUX) {
            String joined = String.join(" ", cmd);
            proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", joined});
        } else {
            proc = Runtime.getRuntime().exec(cmd.toArray(new String[0]));
        }

        int code = proc.waitFor();
        if (code != 0) {
            try (BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = err.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                LOGGER.info("[WebDisc] ffmpeg stderr: {}", sb.toString());
            }
            throw new RuntimeException("ffmpeg exited with code " + code);
        }
    }

    public static String getFfmpegPath() {
        try {
            ensureExecutable();
            if (ffmpegPath != null && new File(ffmpegPath).canExecute()) {
                return ffmpegPath;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}