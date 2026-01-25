package com.zoritism.webdisc.mixin.webdisc;

import com.zoritism.webdisc.WebDiscPlaybackRegistry;
import com.zoritism.webdisc.item.WebDiscItem;
import com.zoritism.webdisc.network.NetworkHandler;
import com.zoritism.webdisc.network.message.PlayWebDiscMessage;
import com.zoritism.webdisc.server.WebDiscJukeboxSyncRegistry;
import com.zoritism.webdisc.server.WebDiscScLengthOverride;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.JukeboxUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

@Mixin(value = JukeboxUpgradeWrapper.class, remap = false)
public abstract class MixinJukeboxUpgradeWrapper extends UpgradeWrapperBase {

    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

    @Shadow
    public abstract ItemStack getDisc();

    @Shadow
    @Nullable
    private Entity entityPlaying;

    @Shadow
    @Nullable
    private Level levelPlaying;

    @Shadow
    @Nullable
    private BlockPos posPlaying;

    @Shadow
    private Runnable onFinishedCallback;

    protected MixinJukeboxUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
    }

    @Inject(method = "playDisc", at = @At("TAIL"))
    private void webdisc$afterPlayDisc(CallbackInfo ci) {
        JukeboxUpgradeWrapper wrapper;
        try {
            wrapper = (JukeboxUpgradeWrapper) (Object) this;
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: cast failed err={}", t.toString());
            return;
        }

        ItemStack disc;
        try {
            disc = wrapper.getDisc();
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: getDisc failed err={}", t.toString());
            return;
        }

        UUID storageUuid;
        try {
            storageUuid = storageWrapper.getContentsUuid().orElse(null);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: getContentsUuid failed err={}", t.toString());
            return;
        }

        if (disc == null || disc.isEmpty()) {
            markNonWebDiscSafe(storageUuid);
            return;
        }

        if (!(disc.getItem() instanceof WebDiscItem)) {
            markNonWebDiscSafe(storageUuid);
            if (storageUuid != null) {
                try {
                    WebDiscJukeboxSyncRegistry.remove(storageUuid);
                } catch (Throwable ignored) {}
            }
            return;
        }

        int webTicks;
        boolean recorded;
        String url;
        try {
            webTicks = WebDiscItem.getDurationTicks(disc);
            recorded = WebDiscItem.isRecorded(disc);
            url = WebDiscItem.getUrl(disc);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: webdisc meta read failed uuid={} err={}", storageUuid, t.toString());
            markNonWebDiscSafe(storageUuid);
            return;
        }

        logger.info(
                "[WebDisc] SC playDisc webdisc uuid={} recorded={} lenTicks={} urlPresent={}",
                storageUuid, recorded, webTicks, url != null && !url.isEmpty()
        );

        if (storageUuid == null || !recorded || webTicks <= 0 || url == null || url.isEmpty()) {
            markNonWebDiscSafe(storageUuid);
            if (storageUuid != null) {
                try {
                    WebDiscJukeboxSyncRegistry.remove(storageUuid);
                } catch (Throwable ignored) {}
            }
            return;
        }

        Level playLevel = (entityPlaying != null) ? entityPlaying.level() : levelPlaying;
        if (!(playLevel instanceof ServerLevel serverLevel)) {
            markWebDiscSafe(storageUuid);
            return;
        }

        BlockPos rawPos = posPlaying;
        if (rawPos == null && entityPlaying != null) {
            try {
                rawPos = entityPlaying.blockPosition();
            } catch (Throwable ignored) {}
        }
        if (rawPos == null) {
            logger.info("[WebDisc] SC playDisc: rawPos null uuid={}", storageUuid);
            return;
        }
        BlockPos sendPos = rawPos;

        long now = serverLevel.getGameTime();
        long discFinishTime = now + (long) webTicks;

        // ВАЖНО: это чинит превью/оверлей SC (он читает эти поля с апгрейда)
        try {
            NBTHelper.setLong(upgrade, "discFinishTime", discFinishTime);
            NBTHelper.setLong(upgrade, "discLength", (long) webTicks);
            save();
            logger.info("[WebDisc] SC playDisc: override upgrade NBT discFinishTime={} discLength={} uuid={}", discFinishTime, webTicks, storageUuid);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: failed override upgrade NBT uuid={} err={}", storageUuid, t.toString());
        }

        markWebDiscSafe(storageUuid);

        try {
            WebDiscScLengthOverride.put(storageUuid, webTicks);

            if (entityPlaying != null) {
                ServerStorageSoundHandler.startPlayingDisc(
                        serverLevel,
                        entityPlaying.position(),
                        storageUuid,
                        entityPlaying.getId(),
                        disc.getItem(),
                        onFinishedCallback
                );
            } else {
                ServerStorageSoundHandler.startPlayingDisc(
                        serverLevel,
                        sendPos,
                        storageUuid,
                        disc.getItem(),
                        onFinishedCallback
                );
            }
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: startPlayingDisc failed uuid={} err={}", storageUuid, t.toString());
            try {
                WebDiscScLengthOverride.clear(storageUuid);
            } catch (Throwable ignored) {}
            return;
        }

        try {
            int entityId = (entityPlaying != null) ? entityPlaying.getId() : -1;
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(sendPos)),
                    new PlayWebDiscMessage(sendPos, url, storageUuid, entityId, 0, webTicks)
            );
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: send PlayWebDiscMessage failed uuid={} err={}", storageUuid, t.toString());
        }

        try {
            int entityId = (entityPlaying != null) ? entityPlaying.getId() : -1;
            WebDiscJukeboxSyncRegistry.put(
                    storageUuid,
                    serverLevel.dimension(),
                    sendPos,
                    entityId,
                    url,
                    webTicks,
                    now,
                    discFinishTime
            );
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: registry.put failed uuid={} err={}", storageUuid, t.toString());
        }
    }

    private void markWebDiscSafe(UUID storageUuid) {
        try {
            WebDiscPlaybackRegistry.markWebDisc(storageUuid);
        } catch (Throwable ignored) {}
    }

    private void markNonWebDiscSafe(UUID storageUuid) {
        try {
            WebDiscPlaybackRegistry.markNonWebDisc(storageUuid);
        } catch (Throwable ignored) {}
    }
}