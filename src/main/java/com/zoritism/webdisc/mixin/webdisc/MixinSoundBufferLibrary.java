package com.zoritism.webdisc.mixin.webdisc;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.client.WebDiscClientHandler;
import com.zoritism.webdisc.client.audio.WebDiscAudioHelper;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundBufferLibrary.class)
public abstract class MixinSoundBufferLibrary {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
            method = "getStream(Lnet/minecraft/resources/ResourceLocation;Z)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void webdisc$getStream(ResourceLocation id,
                                   boolean repeatInstantly,
                                   CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (id == null || !"webdisc".equals(id.getNamespace())) {
            return;
        }

        String path = id.getPath();
        if (path == null || path.isEmpty()) return;
        if (path.contains("placeholder_sound.ogg")) return;

        String normalizedPath = path;
        if (normalizedPath.startsWith("sounds/")) {
            normalizedPath = normalizedPath.substring("sounds/".length());
        }
        if (!normalizedPath.startsWith("customsound/")) {
            return;
        }

        String rest = normalizedPath.substring("customsound/".length());
        if (rest.endsWith(".ogg")) {
            rest = rest.substring(0, rest.length() - 4);
        }
        String urlKey = rest;

        int offsetMs;
        try {
            offsetMs = WebDiscClientHandler.getOffsetForUrlKey(urlKey);
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][SoundBufferLibrary] getStream: failed to get offset for urlKey='{}': {}", urlKey, t.toString());
            offsetMs = 0;
        }

        CompletableFuture<AudioStream> custom = WebDiscAudioHelper.getStream(id, repeatInstantly, offsetMs);
        if (custom != null) {
            cir.setReturnValue(custom);
            cir.cancel();
        }
    }
}