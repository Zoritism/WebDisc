package com.zoritism.webdisc.server;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.network.NetworkHandler;
import com.zoritism.webdisc.network.message.WebdiscJukeboxSyncMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WebDiscJukeboxSyncTicker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SYNC_INTERVAL_TICKS = 40;

    private WebDiscJukeboxSyncTicker() {}

    private static long lastSyncGameTime = 0L;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        long now = serverLevel.getGameTime();
        if (now < lastSyncGameTime + SYNC_INTERVAL_TICKS) {
            return;
        }
        lastSyncGameTime = now;

        Map<UUID, WebDiscJukeboxSyncRegistry.Entry> snap = WebDiscJukeboxSyncRegistry.snapshot();
        if (snap.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, WebDiscJukeboxSyncRegistry.Entry> mapEntry : snap.entrySet()) {
            UUID uuid = mapEntry.getKey();
            WebDiscJukeboxSyncRegistry.Entry e = mapEntry.getValue();
            if (e == null || uuid == null) continue;
            if (!serverLevel.dimension().equals(e.dimension)) {
                continue;
            }

            long start = e.startGameTimeTicks;
            long life = now - start;
            if (life > WebDiscJukeboxSyncRegistry.MAX_LIFETIME_TICKS) {
                try {
                    LOGGER.info(
                            "[WebDisc][JukeboxSyncTicker] removing stale entry: storageUuid={}, dim={}, pos={}, url='{}', startGameTimeTicks={}, lifeTicks={}",
                            uuid, e.dimension.location(), e.pos, e.url, start, life
                    );
                } catch (Throwable ignored) {}
                WebDiscJukeboxSyncRegistry.remove(uuid);
                continue;
            }

            int elapsedTicks = computeElapsedTicks(serverLevel, e);
            if (elapsedTicks < 0) {
                continue;
            }

            int discLength = Math.max(1, e.discLengthTicks);
            int remainingTicks = discLength - elapsedTicks;
            if (remainingTicks < 0) remainingTicks = 0;
            if (remainingTicks > discLength) remainingTicks = discLength;

            try {
                WebdiscJukeboxSyncMessage msg = new WebdiscJukeboxSyncMessage(
                        e.storageUuid,
                        e.pos,
                        e.url,
                        e.entityId,
                        remainingTicks,
                        discLength,
                        e.discFinishGameTimeTicks
                );
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(e.pos)),
                        msg
                );
                LOGGER.info(
                        "[WebDisc][JukeboxSyncTicker] sent sync via TRACKING_CHUNK for storageUuid={}, pos={}, dim={}, remainingTicks={}, lengthTicks={}",
                        e.storageUuid, e.pos, serverLevel.dimension().location(), remainingTicks, discLength
                );
            } catch (Throwable t) {
                try {
                    LOGGER.info(
                            "[WebDisc][JukeboxSyncTicker] failed to send sync for storageUuid={} at {}:  {}",
                            e.storageUuid, e.pos, t.toString()
                    );
                } catch (Throwable ignored) {}
            }
        }
    }

    private static int computeElapsedTicks(ServerLevel level, WebDiscJukeboxSyncRegistry.Entry e) {
        try {
            if (e == null || level == null) return -1;
            int discLength = Math.max(1, e.discLengthTicks);
            long now = level.getGameTime();
            long dtRaw = now - e.startGameTimeTicks;
            long dtClamped = dtRaw;
            if (dtClamped < 0L) dtClamped = 0L;
            if (dtClamped > discLength) dtClamped = discLength;
            return (int) dtClamped;
        } catch (Throwable t) {
            try {
                LOGGER.info(
                        "[WebDisc][JukeboxSyncTicker] computeElapsedTicks failed for storageUuid={}:  {}",
                        e != null ? e.storageUuid : null, t.toString()
                );
            } catch (Throwable ignored) {}
            return -1;
        }
    }
}