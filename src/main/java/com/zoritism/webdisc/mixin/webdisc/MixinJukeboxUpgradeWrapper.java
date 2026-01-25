package com.zoritism.webdisc.mixin.webdisc;

import com.zoritism.webdisc.WebDiscPlaybackRegistry;
import com.zoritism.webdisc.item.WebDiscItem;
import com.zoritism.webdisc.network.NetworkHandler;
import com.zoritism.webdisc.network.message.PlayWebDiscMessage;
import com.zoritism.webdisc.network.message.WebdiscJukeboxTimerMessage;
import com.zoritism.webdisc.server.WebDiscJukeboxSyncRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.JukeboxUpgradeWrapper;
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

    protected MixinJukeboxUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
    }

    @Inject(method = "playDisc", at = @At("TAIL"))
    private void webdisc$afterPlayDisc(CallbackInfo ci) {
        JukeboxUpgradeWrapper wrapper;
        try {
            wrapper = (JukeboxUpgradeWrapper) (Object) this;
        } catch (Throwable t) {
            return;
        }

        ItemStack disc;
        try {
            disc = wrapper.getDisc();
        } catch (Throwable t) {
            return;
        }

        UUID storageUuid = null;
        try {
            storageUuid = storageWrapper.getContentsUuid().orElse(null);
        } catch (Throwable ignored) {}

        if (disc == null || disc.isEmpty()) {
            logger.info("[WebDisc] SC playDisc: empty disc storageUuid={}", storageUuid);
            markNonWebDiscSafe(null);
            return;
        }

        if (!(disc.getItem() instanceof WebDiscItem)) {
            logger.info("[WebDisc] SC playDisc: non-webdisc disc item={} storageUuid={}", disc.getItem().getClass().getName(), storageUuid);
            markNonWebDiscSafe(storageUuid);

            if (storageUuid != null) {
                try {
                    WebDiscJukeboxSyncRegistry.remove(storageUuid);
                } catch (Throwable ignored) {}
            }
            return;
        }

        int webTicks = WebDiscItem.getDurationTicks(disc);
        boolean recorded = WebDiscItem.isRecorded(disc);
        String url = WebDiscItem.getUrl(disc);

        logger.info("[WebDisc] SC playDisc: webdisc detected storageUuid={} recorded={} webTicks={} urlPresent={}", storageUuid, recorded, webTicks, (url != null && !url.isEmpty()));

        if (!recorded || webTicks <= 0 || url == null || url.isEmpty()) {
            logger.info("[WebDisc] SC playDisc: invalid webdisc meta -> treat as non-webdisc storageUuid={}", storageUuid);
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
            logger.info("[WebDisc] SC playDisc: not serverlevel yet storageUuid={}", storageUuid);
            markWebDiscSafe(storageUuid);
            return;
        }

        if (storageUuid == null) {
            logger.info("[WebDisc] SC playDisc: storageUuid is null, cannot sync");
            return;
        }

        long now = serverLevel.getGameTime();
        long discFinishTime = now + webTicks;

        try {
            NBTHelper.setLong(upgrade, "discFinishTime", discFinishTime);
            NBTHelper.setLong(upgrade, "discLength", webTicks);
            save();
            logger.info("[WebDisc] SC playDisc: wrote NBT discFinishTime={} discLength={} storageUuid={}", discFinishTime, webTicks, storageUuid);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: failed to write NBT storageUuid={} err={}", storageUuid, t.toString());
        }

        markWebDiscSafe(storageUuid);

        BlockPos rawPos = posPlaying;
        if (rawPos == null && entityPlaying != null) {
            try {
                rawPos = entityPlaying.blockPosition();
            } catch (Throwable ignored) {}
        }
        if (rawPos == null) {
            logger.info("[WebDisc] SC playDisc: rawPos null storageUuid={}", storageUuid);
            return;
        }
        final BlockPos sendPos = rawPos;

        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(sendPos)),
                    new WebdiscJukeboxTimerMessage(storageUuid, webTicks)
            );
            logger.info("[WebDisc] SC playDisc: sent WebdiscJukeboxTimerMessage storageUuid={} ticks={}", storageUuid, webTicks);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: failed send timer msg storageUuid={} err={}", storageUuid, t.toString());
        }

        int elapsedTicks;
        try {
            long discFinish = NBTHelper.getLong(upgrade, "discFinishTime").orElse(discFinishTime);
            long discLengthLong = NBTHelper.getLong(upgrade, "discLength").orElse((long) webTicks);
            if (discLengthLong <= 0L) discLengthLong = webTicks;

            long discStart = discFinish - discLengthLong;
            long rawElapsed = now - discStart;
            if (rawElapsed < 0L) rawElapsed = 0L;
            if (rawElapsed > discLengthLong) rawElapsed = discLengthLong;

            elapsedTicks = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, rawElapsed));
        } catch (Throwable t) {
            elapsedTicks = 0;
        }

        try {
            int entityId = (entityPlaying != null) ? entityPlaying.getId() : -1;
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(sendPos)),
                    new PlayWebDiscMessage(sendPos, url, storageUuid, entityId, elapsedTicks, webTicks)
            );
            logger.info("[WebDisc] SC playDisc: sent PlayWebDiscMessage storageUuid={} elapsed={} len={} entityId={}", storageUuid, elapsedTicks, webTicks, (entityPlaying != null ? entityPlaying.getId() : -1));
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: failed send PlayWebDiscMessage storageUuid={} err={}", storageUuid, t.toString());
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
            logger.info("[WebDisc] SC playDisc: registry.put storageUuid={} start={} finish={}", storageUuid, now, discFinishTime);
        } catch (Throwable t) {
            logger.info("[WebDisc] SC playDisc: registry.put failed storageUuid={} err={}", storageUuid, t.toString());
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