package com.zoritism.webdisc.mixin.webdisc;

import com.zoritism.webdisc.WebDiscPlaybackRegistry;
import com.zoritism.webdisc.client.WebDiscClientHandler;
import com.zoritism.webdisc.client.audio.sound.WebFileSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraftforge.event.TickEvent;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.StorageSoundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mixin(value = StorageSoundHandler.class, remap = false)
public abstract class MixinStorageSoundHandlerPlay {

    @Unique
    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

    @Shadow
    @Final
    private static Map<UUID, SoundInstance> storageSounds;

    @Overwrite
    public static void playStorageSound(UUID storageUuid, SoundInstance sound) {
        if (storageUuid == null || sound == null) {
            return;
        }

        boolean isWebDisc = safeIsWebDisc(storageUuid);
        boolean isWebFileSound = (sound instanceof WebFileSound);

        if (isWebDisc && !isWebFileSound) {
            logger.info(
                    "[WebDisc] BLOCKING non-WebDisc sound for WebDisc uuid={} soundClass={}",
                    storageUuid, sound.getClass().getName()
            );
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        stopExisting(storageUuid, mc, "playStorageSound_replace");
        try {
            storageSounds.put(storageUuid, sound);
        } catch (Throwable ignored) {}

        if (mc != null) {
            try {
                mc.getSoundManager().play(sound);
            } catch (Throwable t) {
                logger.info("[WebDisc] playStorageSound SoundManager.play FAILED uuid={} err={}", storageUuid, t.toString());
            }
        }
    }

    @Inject(method = "stopStorageSound(Ljava/util/UUID;)V", at = @At("TAIL"))
    private static void webdisc$onStopStorageSound(UUID storageUuid, CallbackInfo ci) {
        cleanupAfterStop(storageUuid, "stopStorageSound_tail");
    }

    @Inject(method = "tick(Lnet/minecraftforge/event/TickEvent$LevelTickEvent;)V", at = @At("TAIL"))
    private static void webdisc$afterTick(TickEvent.LevelTickEvent event, CallbackInfo ci) {
        if (event == null || event.level == null) return;
        if (!event.level.isClientSide) return;
        if (storageSounds == null || storageSounds.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Set<UUID> toCleanup = null;

        try {
            for (Map.Entry<UUID, SoundInstance> e : storageSounds.entrySet()) {
                if (e == null) continue;

                UUID uuid = e.getKey();
                SoundInstance sound = e.getValue();
                if (uuid == null || sound == null) continue;

                boolean active;
                try {
                    active = mc.getSoundManager().isActive(sound);
                } catch (Throwable t) {
                    continue;
                }

                if (!active) {
                    if (toCleanup == null) toCleanup = new HashSet<>();
                    toCleanup.add(uuid);
                }
            }
        } catch (Throwable t) {
            logger.info("[WebDisc] afterTick iteration failed err={}", t.toString());
            return;
        }

        if (toCleanup == null || toCleanup.isEmpty()) {
            return;
        }

        for (UUID uuid : toCleanup) {
            SoundInstance removed = null;
            try {
                removed = storageSounds.remove(uuid);
            } catch (Throwable ignored) {}

            if (removed instanceof WebFileSound) {
                cleanupAfterStop(uuid, "tick_inactive");
            }
        }
    }

    @Unique
    private static boolean safeIsWebDisc(UUID uuid) {
        try {
            return WebDiscPlaybackRegistry.isWebDisc(uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private static void cleanupAfterStop(UUID storageUuid, String reason) {
        if (storageUuid == null) {
            return;
        }

        // cleanup делаем только если это WebDisc uuid, иначе ломаем vanilla/SC поведение
        if (!safeIsWebDisc(storageUuid)) {
            return;
        }

        try {
            WebDiscClientHandler.clearByUuid(storageUuid, reason);
        } catch (Throwable t) {
            logger.info("[WebDisc] cleanupAfterStop WebDiscClientHandler.clearByUuid FAILED uuid={} err={}", storageUuid, t.toString());
        }

        try {
            WebDiscPlaybackRegistry.clear(storageUuid);
        } catch (Throwable t) {
            logger.info("[WebDisc] cleanupAfterStop WebDiscPlaybackRegistry.clear FAILED uuid={} err={}", storageUuid, t.toString());
        }
    }

    @Unique
    private static void stopExisting(UUID storageUuid, Minecraft mc, String reason) {
        try {
            SoundInstance existing = storageSounds.remove(storageUuid);
            if (existing == null) {
                return;
            }
            if (mc != null) {
                try {
                    mc.getSoundManager().stop(existing);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}