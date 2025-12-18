package com.zoritism.webdisc.client.audio;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.util.WebHashing;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class AudioHandlerClient {

    private static Path baseDir() {
        Minecraft mc = Minecraft.getInstance();
        File root = mc != null ? mc.gameDirectory : new File(".");
        return root.toPath().resolve("webdisc").resolve("client_downloads");
    }

    private static String minecraftify(String url) {
        if (url == null) return "";
        return url.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    public boolean hasOgg(String url) {
        String key = WebHashing.sha256(minecraftify(url));
        File ogg = baseDir().resolve(key + ".ogg").toFile();
        return ogg.exists();
    }

    public CompletableFuture<Boolean> downloadAsOgg(String url) {
        return CompletableFuture.supplyAsync(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            try {
                Path dir = baseDir();
                dir.toFile().mkdirs();
                String key = WebHashing.sha256(minecraftify(url));
                String tempBase = dir.resolve(key).toString();
                File oggOut = dir.resolve(key + ".ogg").toFile();

                String inPath = YoutubeDLHelper.run(
                        "-S", "res:144",
                        "--no-playlist",
                        "-o", tempBase,
                        url,
                        "--print", "after_move:filepath"
                );

                FFmpegHelper.run(
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
                return true;
            } catch (Exception e) {
                WebDiscMod.LOGGER.info("[WebDisc] Audio download/transcode failed: {}", e.toString());
                return false;
            }
        });
    }

    public InputStream openOgg(String url) {
        String key = WebHashing.sha256(minecraftify(url));
        File ogg = baseDir().resolve(key + ".ogg").toFile();
        try {
            return new FileInputStream(ogg);
        } catch (FileNotFoundException e) {
            WebDiscMod.LOGGER.info("[WebDisc] OGG file missing for url key {}", key);
            return null;
        }
    }
}