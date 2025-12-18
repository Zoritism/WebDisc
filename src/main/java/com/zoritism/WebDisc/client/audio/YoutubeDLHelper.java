package com.zoritism.webdisc.client.audio;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.config.WebDiscConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class YoutubeDLHelper {
    private static String ytdlpPath;

    private YoutubeDLHelper() {}

    private static Path baseDir() {
        Minecraft mc = Minecraft.getInstance();
        File root = mc != null ? mc.gameDirectory : new File(".");
        return root.toPath().resolve("webdisc").resolve("youtubedl");
    }

    private static String resolveExecutableFileName() {
        if (SystemUtils.IS_OS_LINUX) {
            return "yt-dlp_linux";
        } else if (SystemUtils.IS_OS_MAC) {
            return "yt-dlp_macos";
        } else {
            return "yt-dlp.exe";
        }
    }

    private static void ensureExecutable() throws Exception {
        if (ytdlpPath != null && new File(ytdlpPath).canExecute()) {
            return;
        }

        var fromPath = WebDiscAudioHelper.findInPath("yt-dlp");
        if (fromPath.isPresent()) {
            ytdlpPath = fromPath.get();
            return;
        }

        Path dir = baseDir();
        dir.toFile().mkdirs();

        String fileName = resolveExecutableFileName();
        File localExe = dir.resolve(fileName).toFile();
        Minecraft mc = Minecraft.getInstance();

        if (!localExe.exists()) {
            if (!WebDiscConfig.CLIENT.downloadYoutubeDL.get()) {
                if (mc != null && mc.player != null) {
                    mc.player.sendSystemMessage(Component.translatable("webdisc.ytdlp.missing"));
                }
                logMissingYtDlp();
                return;
            }

            URL src;
            if (SystemUtils.IS_OS_LINUX) {
                src = new URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux");
            } else if (SystemUtils.IS_OS_MAC) {
                src = new URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos");
            } else {
                src = new URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe");
            }

            try (var in = src.openStream()) {
                Files.copy(in, localExe.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            localExe.setExecutable(true);
        }

        if (SystemUtils.IS_OS_WINDOWS || localExe.canExecute()) {
            ytdlpPath = localExe.getAbsolutePath();
        } else if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("webdisc.executable.permission", localExe.getName()));
        }
    }

    private static void logMissingYtDlp() {
        WebDiscMod.LOGGER.info("[WebDisc] yt-dlp not found. Put yt-dlp executable into WebDisc/youtubedl or enable downloadYoutubeDL in config.");
    }

    public static String run(String... args) throws Exception {
        ensureExecutable();
        if (ytdlpPath == null || !new File(ytdlpPath).canExecute()) {
            throw new IllegalStateException("yt-dlp executable not available");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(ytdlpPath);
        Collections.addAll(cmd, args);

        WebDiscMod.LOGGER.info("[WebDisc] yt-dlp: {}", String.join(" ", cmd));

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
                out.append(line);
            }
        }

        int code = proc.waitFor();
        if (code != 0) {
            StringBuilder errBuf = new StringBuilder();
            try (BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                String line;
                while ((line = err.readLine()) != null) {
                    errBuf.append(line).append('\n');
                }
            }
            WebDiscMod.LOGGER.info("[WebDisc] yt-dlp stderr: {}", errBuf.toString());
            throw new RuntimeException("yt-dlp exit code " + code);
        }
        return out.toString();
    }
}