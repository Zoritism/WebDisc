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
            logger.info("[WebDisc] StorageSoundHandler.playStorageSound called with nulls uuid={} sound={}", storageUuid, sound);
            return;
        }

        boolean isWebDisc = safeIsWebDisc(storageUuid);
        boolean isWebFileSound = (sound instanceof WebFileSound);

        logger.info(
                "[WebDisc] SC->playStorageSound uuid={} soundClass={} isWebDisc={} isWebFileSound={}",
                storageUuid, sound.getClass().getName(), isWebDisc, isWebFileSound
        );

        if (isWebDisc && !isWebFileSound) {
            logger.info(
                    "[WebDisc] BLOCKING non-WebDisc sound for WebDisc uuid={} soundClass={}",
                    storageUuid, sound.getClass().getName()
            );
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            logger.info("[WebDisc] playStorageSound Minecraft.getInstance()==null uuid={}", storageUuid);
            stopExisting(storageUuid, null, "playStorageSound_noMinecraft");
            try {
                storageSounds.put(storageUuid, sound);
                logger.info("[WebDisc] playStorageSound stored sound in map (noMinecraft) uuid={} mapSize={}", storageUuid, storageSounds.size());
            } catch (Throwable t) {
                logger.info("[WebDisc] playStorageSound failed to put into storageSounds uuid={} err={}", storageUuid, t.toString());
            }
            return;
        }

        stopExisting(storageUuid, mc, "playStorageSound_replace");
        try {
            storageSounds.put(storageUuid, sound);
            logger.info("[WebDisc] playStorageSound stored sound in map uuid={} mapSize={}", storageUuid, storageSounds.size());
        } catch (Throwable t) {
            logger.info("[WebDisc] playStorageSound failed to put into storageSounds uuid={} err={}", storageUuid, t.toString());
        }

        try {
            mc.getSoundManager().play(sound);
            logger.info("[WebDisc] playStorageSound SoundManager.play done uuid={} soundClass={}", storageUuid, sound.getClass().getName());
        } catch (Throwable t) {
            logger.info("[WebDisc] playStorageSound SoundManager.play FAILED uuid={} err={}", storageUuid, t.toString());
        }
    }

    @Inject(method = "stopStorageSound(Ljava/util/UUID;)V", at = @At("HEAD"))
    private static void webdisc$beforeStopStorageSound(UUID storageUuid, CallbackInfo ci) {
        logger.info("[WebDisc] SC->stopStorageSound HEAD uuid={}", storageUuid);
    }

    @Inject(method = "stopStorageSound(Ljava/util/UUID;)V", at = @At("TAIL"))
    private static void webdisc$onStopStorageSound(UUID storageUuid, CallbackInfo ci) {
        logger.info("[WebDisc] SC->stopStorageSound TAIL uuid={} (running cleanup)", storageUuid);
        cleanupAfterStop(storageUuid, "stopStorageSound_tail");
    }

    @Inject(method = "tick(Lnet/minecraftforge/event/TickEvent$LevelTickEvent;)V", at = @At("TAIL"))
    private static void webdisc$afterTick(TickEvent.LevelTickEvent event, CallbackInfo ci) {
        if (event == null || event.level == null) {
            return;
        }

        // Только клиент: StorageSoundHandler использует Minecraft.getInstance() и SoundManager
        if (!event.level.isClientSide) {
            return;
        }

        if (storageSounds == null || storageSounds.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        long gameTime;
        try {
            gameTime = event.level.getGameTime();
        } catch (Throwable t) {
            gameTime = -1L;
        }

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
                    logger.info("[WebDisc] afterTick isActive threw uuid={} soundClass={} err={}", uuid, sound.getClass().getName(), t.toString());
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

        logger.info(
                "[WebDisc] afterTick found inactive sounds gameTime={} inactiveCount={} mapSize={}",
                gameTime, toCleanup.size(), storageSounds.size()
        );

        for (UUID uuid : toCleanup) {
            SoundInstance removed = null;
            try {
                removed = storageSounds.remove(uuid);
            } catch (Throwable t) {
                logger.info("[WebDisc] afterTick failed to remove uuid={} err={}", uuid, t.toString());
            }

            boolean removedIsWebFileSound = removed instanceof WebFileSound;
            boolean isWebDisc = safeIsWebDisc(uuid);

            logger.info(
                    "[WebDisc] afterTick inactive uuid={} removedFromMap={} removedClass={} removedIsWebFileSound={} isWebDisc={}",
                    uuid,
                    removed != null,
                    removed != null ? removed.getClass().getName() : "null",
                    removedIsWebFileSound,
                    isWebDisc
            );

            // Критично: cleanup делаем только когда реально завершился наш WebDisc-звук.
            // Иначе при смене vanilla->webdisc под тем же uuid можно случайно зачистить state в момент старта.
            if (removedIsWebFileSound) {
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
            logger.info("[WebDisc] cleanupAfterStop called with null uuid reason={}", reason);
            return;
        }

        boolean isWebDisc = safeIsWebDisc(storageUuid);
        logger.info("[WebDisc] cleanupAfterStop uuid={} reason={} isWebDisc={}", storageUuid, reason, isWebDisc);

        // Дополнительная страховка: не трогаем состояния, если uuid не WebDisc
        if (!isWebDisc) {
            return;
        }

        try {
            com.zoritism.webdisc.server.WebDiscJukeboxSyncRegistry.remove(storageUuid);
            logger.info("[WebDisc] cleanupAfterStop removed from WebDiscJukeboxSyncRegistry uuid={}", storageUuid);
        } catch (Throwable t) {
            logger.info("[WebDisc] cleanupAfterStop failed WebDiscJukeboxSyncRegistry.remove uuid={} err={}", storageUuid, t.toString());
        }

        try {
            WebDiscClientHandler.clearByUuid(storageUuid, reason);
            logger.info("[WebDisc] cleanupAfterStop WebDiscClientHandler.clearByUuid done uuid={}", storageUuid);
        } catch (Throwable t) {
            logger.info("[WebDisc] cleanupAfterStop WebDiscClientHandler.clearByUuid FAILED uuid={} err={}", storageUuid, t.toString());
        }

        try {
            WebDiscPlaybackRegistry.clear(storageUuid);
            logger.info("[WebDisc] cleanupAfterStop WebDiscPlaybackRegistry.clear done uuid={}", storageUuid);
        } catch (Throwable t) {
            logger.info("[WebDisc] cleanupAfterStop WebDiscPlaybackRegistry.clear FAILED uuid={} err={}", storageUuid, t.toString());
        }
    }

    @Unique
    private static void stopExisting(UUID storageUuid, Minecraft mc, String reason) {
        try {
            SoundInstance existing = storageSounds.remove(storageUuid);
            if (existing == null) {
                logger.info("[WebDisc] stopExisting none uuid={} reason={} mapSize={}", storageUuid, reason, storageSounds.size());
                return;
            }

            logger.info(
                    "[WebDisc] stopExisting removed uuid={} existingClass={} reason={} (will stop via SoundManager={})",
                    storageUuid, existing.getClass().getName(), reason, (mc != null)
            );

            if (mc != null) {
                try {
                    mc.getSoundManager().stop(existing);
                    logger.info("[WebDisc] stopExisting SoundManager.stop done uuid={}", storageUuid);
                } catch (Throwable t) {
                    logger.info("[WebDisc] stopExisting SoundManager.stop FAILED uuid={} err={}", storageUuid, t.toString());
                }
            }
        } catch (Throwable t) {
            logger.info("[WebDisc] stopExisting FAILED uuid={} reason={} err={}", storageUuid, reason, t.toString());
        }
    }
}