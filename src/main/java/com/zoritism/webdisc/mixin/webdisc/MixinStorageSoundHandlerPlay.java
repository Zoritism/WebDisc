package com.zoritism.webdisc.mixin.webdisc;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.WebDiscPlaybackRegistry;
import com.zoritism.webdisc.client.WebDiscClientHandler;
import com.zoritism.webdisc.client.audio.sound.WebFileSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.StorageSoundHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(value = StorageSoundHandler.class, remap = false)
public abstract class MixinStorageSoundHandlerPlay {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    private static Map<UUID, SoundInstance> storageSounds;

    @Overwrite
    public static void playStorageSound(UUID storageUuid, SoundInstance sound) {
        if (storageUuid == null || sound == null) {
            return;
        }

        try {
            if (WebDiscPlaybackRegistry.isWebDisc(storageUuid) && !(sound instanceof WebFileSound)) {
                LOGGER.info("[WebDisc][StorageSoundHandlerPlay] ignoring placeholder sound for WebDisc uuid={}, class={}",
                        storageUuid, sound.getClass().getName());
                return;
            }
        } catch (Throwable ignored) {}

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            stopExisting(storageUuid, null);
            storageSounds.put(storageUuid, sound);
            return;
        }

        stopExisting(storageUuid, mc);
        storageSounds.put(storageUuid, sound);

        try {
            mc.getSoundManager().play(sound);
            LOGGER.info("[WebDisc][StorageSoundHandlerPlay] started sound for uuid={} (class={})",
                    storageUuid, sound.getClass().getName());
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][StorageSoundHandlerPlay] failed to play sound for uuid={}: {}",
                    storageUuid, t.toString());
        }
    }

    @Inject(
            method = "stopStorageSound(Ljava/util/UUID;)V",
            at = @At("TAIL")
    )
    private static void webdisc$onStopStorageSound(UUID storageUuid, CallbackInfo ci) {
        if (storageUuid == null) {
            return;
        }

        try {
            com.zoritism.webdisc.server.WebDiscJukeboxSyncRegistry.remove(storageUuid);
        } catch (Throwable ignored) {}

        try {
            WebDiscClientHandler.clearByUuid(storageUuid);
        } catch (Throwable ignored) {}

        try {
            WebDiscPlaybackRegistry.clear(storageUuid);
        } catch (Throwable ignored) {}
    }

    private static void stopExisting(UUID storageUuid, Minecraft mc) {
        try {
            SoundInstance existing = storageSounds.remove(storageUuid);
            if (existing != null && mc != null) {
                mc.getSoundManager().stop(existing);
                LOGGER.info("[WebDisc][StorageSoundHandlerPlay] stopExisting: uuid={}, class={}",
                        storageUuid, existing.getClass().getName());
            }
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][StorageSoundHandlerPlay] stopExisting failed for uuid={}: {}",
                    storageUuid, t.toString());
        }
    }
}