package com.zoritism.webdisc.mixin.webdisc;

import com.zoritism.webdisc.server.WebDiscScLengthOverride;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.UUID;

@Mixin(value = ServerStorageSoundHandler.class, remap = false)
public abstract class MixinServerStorageSoundHandler {

    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

    @ModifyArgs(
            method = "startPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/UUID;Lnet/minecraft/world/item/Item;Ljava/lang/Runnable;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/p3pp3rf1y/sophisticatedcore/upgrades/jukebox/ServerStorageSoundHandler;putSoundInfo(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;Ljava/lang/Runnable;Lnet/minecraft/world/phys/Vec3;J)V"
            )
    )
    private static void webdisc$overrideFinishTimeBlock(Args args, ServerLevel serverLevel, BlockPos position, UUID storageUuid, Item item, Runnable onFinishedHandler) {
        applyOverride("block", args);
    }

    @ModifyArgs(
            method = "startPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ljava/util/UUID;ILnet/minecraft/world/item/Item;Ljava/lang/Runnable;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/p3pp3rf1y/sophisticatedcore/upgrades/jukebox/ServerStorageSoundHandler;putSoundInfo(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;Ljava/lang/Runnable;Lnet/minecraft/world/phys/Vec3;J)V"
            )
    )
    private static void webdisc$overrideFinishTimeEntity(Args args, ServerLevel serverLevel, Vec3 position, UUID storageUuid, int entityId, Item item, Runnable onFinishedHandler) {
        applyOverride("entity", args);
    }

    private static void applyOverride(String kind, Args args) {
        if (args == null) {
            return;
        }

        ServerLevel lvl;
        UUID uuid;
        long originalFinish;

        try {
            lvl = (ServerLevel) args.get(0);
            uuid = (UUID) args.get(1);
            originalFinish = (long) args.get(4);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC finish override read args FAILED kind={} err={}", kind, t.toString());
            return;
        }

        if (lvl == null || uuid == null) {
            return;
        }

        Integer overrideLen = WebDiscScLengthOverride.pop(uuid);
        if (overrideLen == null) {
            return;
        }

        long now = lvl.getGameTime();
        long newFinish = now + (long) Math.max(1, overrideLen);

        try {
            args.set(4, newFinish);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC finish override set args FAILED kind={} uuid={} err={}", kind, uuid, t.toString());
            return;
        }

        logger.info(
                "[WebDisc] SC finish override APPLIED kind={} uuid={} now={} oldFinish={} newFinish={} overrideLenTicks={}",
                kind, uuid, now, originalFinish, newFinish, overrideLen
        );
    }
}