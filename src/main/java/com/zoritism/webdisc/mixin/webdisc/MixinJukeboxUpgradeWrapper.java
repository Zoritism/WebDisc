package com.zoritism.webdisc.mixin.webdisc;

import com.mojang.logging.LogUtils;
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
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.JukeboxUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.JukeboxUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

@Mixin(value = JukeboxUpgradeWrapper.class, remap = false)
public abstract class MixinJukeboxUpgradeWrapper extends UpgradeWrapperBase<JukeboxUpgradeWrapper, JukeboxUpgradeItem> {

    private static final Logger LOGGER = LogUtils.getLogger();

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

    protected MixinJukeboxUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
    }

    @Inject(method = "playDisc", at = @At("TAIL"))
    private void webdisc$afterPlayDisc(CallbackInfo ci) {
        JukeboxUpgradeWrapper wrapper;
        try {
            wrapper = (JukeboxUpgradeWrapper) (Object) this;
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: failed to cast self: {}", t.toString());
            return;
        }

        ItemStack disc;
        try {
            disc = wrapper.getDisc();
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: getDisc() threw: {}", t.toString());
            return;
        }

        UUID storageUuid = null;
        try {
            storageUuid = storageWrapper.getContentsUuid().orElse(null);
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: getContentsUuid() threw: {}", t.toString());
        }

        if (disc == null || disc.isEmpty()) {
            markNonWebDiscSafe(null);
            return;
        }

        // --- НЕ-webdisc ---
        if (!(disc.getItem() instanceof WebDiscItem)) {
            markNonWebDiscSafe(storageUuid);

            if (storageUuid != null) {
                try {
                    WebDiscJukeboxSyncRegistry.remove(storageUuid);
                } catch (Throwable ignored) {}

                Level playLevel = (entityPlaying != null) ? entityPlaying.level() : levelPlaying;
                if (playLevel instanceof ServerLevel serverLevel) {
                    BlockPos rawPos = posPlaying;
                    if (rawPos == null && entityPlaying != null) {
                        try {
                            rawPos = entityPlaying.blockPosition();
                        } catch (Throwable ignored) {}
                    }
                    if (rawPos != null) {
                        final BlockPos sendPos = rawPos;
                        int entityId = (entityPlaying != null) ? entityPlaying.getId() : -1;
                        try {
                            NetworkHandler.CHANNEL.send(
                                    PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(sendPos)),
                                    new PlayWebDiscMessage(sendPos, "", storageUuid, entityId, 0, 0)
                            );
                        } catch (Throwable t) {
                            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: failed to send STOP PlayWebDiscMessage for non-webdisc slot: {}",
                                    t.toString());
                        }
                    }
                }
            }
            return;
        }

        int webTicks = WebDiscItem.getDurationTicks(disc);
        boolean recorded = WebDiscItem.isRecorded(disc);
        String url = WebDiscItem.getUrl(disc);

        if (!recorded || webTicks <= 0 || url == null || url.isEmpty()) {
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

        if (storageUuid == null) {
            return;
        }

        long now = serverLevel.getGameTime();
        long discFinishTime = now + webTicks;

        try {
            NBTHelper.setLong(upgrade, "discFinishTime", discFinishTime);
            NBTHelper.setLong(upgrade, "discLength", webTicks);
            save();
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: failed to update discLength/discFinishTime: {}", t.toString());
        }

        markWebDiscSafe(storageUuid);

        BlockPos rawPos = posPlaying;
        if (rawPos == null && entityPlaying != null) {
            try {
                rawPos = entityPlaying.blockPosition();
            } catch (Throwable t) {
                LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: entityPlaying.blockPosition() threw: {}", t.toString());
            }
        }
        if (rawPos == null) {
            return;
        }
        final BlockPos sendPos = rawPos;

        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(sendPos)),
                    new WebdiscJukeboxTimerMessage(storageUuid, webTicks)
            );
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: failed to send WebdiscJukeboxTimerMessage: {}", t.toString());
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
            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: failed to compute elapsedTicks: {}", t.toString());
            elapsedTicks = 0;
        }

        try {
            int entityId = (entityPlaying != null) ? entityPlaying.getId() : -1;
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(sendPos)),
                    new PlayWebDiscMessage(sendPos, url, storageUuid, entityId, elapsedTicks, webTicks)
            );
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: failed to send PlayWebDiscMessage: {}", t.toString());
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
            LOGGER.info("[WebDisc][JukeboxUpgrade] playDisc TAIL: failed to register in WebDiscJukeboxSyncRegistry: {}", t.toString());
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