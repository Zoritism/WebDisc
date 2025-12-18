package com.zoritism.webdisc.client.audio;

import com.mojang.blaze3d.audio.OggAudioStream;
import com.zoritism.webdisc.WebDiscMod;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class WebDiscAudioHelper {

    private WebDiscAudioHelper() {}

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
        if (location == null) return null;
        if (!WebDiscMod.MOD_ID.equals(location.getNamespace())) return null;

        String path = location.getPath();
        if (path == null || path.isEmpty()) return null;
        if (path.contains("placeholder_sound.ogg")) return null;

        String[] parts = path.split("/");
        if (parts.length < 2) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) sb.append('/');
            sb.append(parts[i]);
        }
        String keyWithExt = sb.toString();
        if (!keyWithExt.endsWith(".ogg")) {
            return null;
        }
        String urlKey = keyWithExt.substring(0, keyWithExt.length() - 4);

        return CompletableFuture.supplyAsync(() -> {
            try {
                AudioHandlerClient handler = new AudioHandlerClient();
                InputStream in = handler.openOgg(urlKey);
                if (in == null) {
                    return null;
                }
                if (wrapLoop) {
                    return new LoopingAudioStream(OggAudioStream::new, in);
                } else {
                    return new OggAudioStream(in);
                }
            } catch (Throwable t) {
                throw new CompletionException(t);
            }
        }, Util.backgroundExecutor());
    }
}