package com.zoritism.webdisc.mixin.webdisc;

import com.zoritism.webdisc.server.WebDiscScFinishTimeOverride;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(value = ServerStorageSoundHandler.class, remap = false)
public abstract class MixinServerStorageSoundHandler {

    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

    // Имя поля взято из твоего decompile SophisticatedCore 1.2.123.1432 (не угадывается)
    @Shadow
    private static Map<ResourceKey<Level>, Map<UUID, Object>> worldStorageSoundInfos;

    @Inject(
            method = "startPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/UUID;Lnet/minecraft/world/item/Item;Ljava/lang/Runnable;)V",
            at = @At("HEAD")
    )
    private static void webdisc$startPlayingDiscBlockHead(ServerLevel serverLevel,
                                                          BlockPos position,
                                                          UUID storageUuid,
                                                          Item item,
                                                          Runnable onFinishedHandler,
                                                          CallbackInfo ci) {
        if (serverLevel == null || storageUuid == null) return;
        logger.info(
                "[WebDisc] SC startPlayingDisc(block) uuid={} dim={} pos={} item={} handlerNull={} now={}",
                storageUuid,
                serverLevel.dimension().location(),
                position,
                (item != null ? item.getClass().getName() : "null"),
                onFinishedHandler == null,
                serverLevel.getGameTime()
        );
    }

    @Inject(
            method = "startPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/UUID;Lnet/minecraft/world/item/Item;Ljava/lang/Runnable;)V",
            at = @At("TAIL")
    )
    private static void webdisc$startPlayingDiscBlockTail(ServerLevel serverLevel,
                                                          BlockPos position,
                                                          UUID storageUuid,
                                                          Item item,
                                                          Runnable onFinishedHandler,
                                                          CallbackInfo ci) {
        applyFinishOverride(serverLevel, storageUuid, "block");
    }

    @Inject(
            method = "startPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ljava/util/UUID;ILnet/minecraft/world/item/Item;Ljava/lang/Runnable;)V",
            at = @At("HEAD")
    )
    private static void webdisc$startPlayingDiscEntityHead(ServerLevel serverLevel,
                                                           Vec3 position,
                                                           UUID storageUuid,
                                                           int entityId,
                                                           Item item,
                                                           Runnable onFinishedHandler,
                                                           CallbackInfo ci) {
        if (serverLevel == null || storageUuid == null) return;
        logger.info(
                "[WebDisc] SC startPlayingDisc(entity) uuid={} dim={} entityId={} pos={} item={} handlerNull={} now={}",
                storageUuid,
                serverLevel.dimension().location(),
                entityId,
                position,
                (item != null ? item.getClass().getName() : "null"),
                onFinishedHandler == null,
                serverLevel.getGameTime()
        );
    }

    @Inject(
            method = "startPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ljava/util/UUID;ILnet/minecraft/world/item/Item;Ljava/lang/Runnable;)V",
            at = @At("TAIL")
    )
    private static void webdisc$startPlayingDiscEntityTail(ServerLevel serverLevel,
                                                           Vec3 position,
                                                           UUID storageUuid,
                                                           int entityId,
                                                           Item item,
                                                           Runnable onFinishedHandler,
                                                           CallbackInfo ci) {
        applyFinishOverride(serverLevel, storageUuid, "entity");
    }

    @Inject(
            method = "stopPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ljava/util/UUID;)V",
            at = @At("HEAD")
    )
    private static void webdisc$stopPlayingDiscHead(ServerLevel serverWorld, Vec3 position, UUID storageUuid, CallbackInfo ci) {
        if (serverWorld == null || storageUuid == null) return;
        logger.info(
                "[WebDisc] SC stopPlayingDisc uuid={} dim={} pos={} now={}",
                storageUuid, serverWorld.dimension().location(), position, serverWorld.getGameTime()
        );
    }

    @Inject(
            method = "removeSoundInfo(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;Z)V",
            at = @At("HEAD")
    )
    private static void webdisc$removeSoundInfoHead(ServerLevel serverWorld, UUID storageUuid, boolean finished, CallbackInfo ci) {
        if (serverWorld == null || storageUuid == null) return;
        logger.info(
                "[WebDisc] SC removeSoundInfo uuid={} finished={} dim={} now={}",
                storageUuid, finished, serverWorld.dimension().location(), serverWorld.getGameTime()
        );
    }

    private static void applyFinishOverride(ServerLevel serverLevel, UUID storageUuid, String kind) {
        if (serverLevel == null || storageUuid == null) {
            return;
        }

        Long desiredFinish;
        try {
            desiredFinish = WebDiscScFinishTimeOverride.pop(storageUuid);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC finish override pop FAILED uuid={} kind={} err={}", storageUuid, kind, t.toString());
            return;
        }

        // Если override не ставили — это normal case для ванильных дисков, не шумим.
        if (desiredFinish == null) {
            return;
        }

        ResourceKey<Level> dim = serverLevel.dimension();

        Map<UUID, Object> dimMap;
        try {
            dimMap = (worldStorageSoundInfos != null) ? worldStorageSoundInfos.get(dim) : null;
        } catch (Throwable t) {
            logger.info("[WebDisc] SC finish override: access worldStorageSoundInfos FAILED uuid={} kind={} err={}", storageUuid, kind, t.toString());
            return;
        }

        if (dimMap == null) {
            logger.info("[WebDisc] SC finish override: dim map missing uuid={} kind={} dim={} desiredFinish={}",
                    storageUuid, kind, dim.location(), desiredFinish);
            return;
        }

        Object infoObj = dimMap.get(storageUuid);
        if (infoObj == null) {
            logger.info("[WebDisc] SC finish override: SoundInfo missing uuid={} kind={} dim={} desiredFinish={}",
                    storageUuid, kind, dim.location(), desiredFinish);
            return;
        }

        try {
            // поле finishTime взято из твоего decompile SophisticatedCore 1.2.123.1432 (не угадывается)
            var f = infoObj.getClass().getDeclaredField("finishTime");
            f.setAccessible(true);
            long oldFinish = (long) f.get(infoObj);
            f.setLong(infoObj, desiredFinish);

            logger.info(
                    "[WebDisc] SC finish override APPLIED uuid={} kind={} dim={} oldFinish={} newFinish={} now={}",
                    storageUuid, kind, dim.location(), oldFinish, desiredFinish, serverLevel.getGameTime()
            );
        } catch (Throwable t) {
            logger.info("[WebDisc] SC finish override APPLY FAILED uuid={} kind={} dim={} desiredFinish={} err={}",
                    storageUuid, kind, dim.location(), desiredFinish, t.toString());
        }
    }
}