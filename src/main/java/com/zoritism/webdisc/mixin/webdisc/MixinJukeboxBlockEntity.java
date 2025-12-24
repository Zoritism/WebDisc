package com.zoritism.webdisc.mixin.webdisc;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.item.WebDiscItem;
import com.zoritism.webdisc.network.NetworkHandler;
import com.zoritism.webdisc.network.message.PlayWebDiscMessage;
import com.zoritism.webdisc.server.WebDiscJukeboxSyncRegistry;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mixin(value = JukeboxBlockEntity.class, remap = true)
public abstract class MixinJukeboxBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static UUID uuidForJukebox(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return Util.NIL_UUID;
        String key = "webdisc:vanilla_jukebox:" + level.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendStop(ServerLevel level, BlockPos pos, UUID uuid) {
        if (level == null || pos == null) return;
        UUID safeUuid = (uuid != null) ? uuid : Util.NIL_UUID;
        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                    new PlayWebDiscMessage(pos, "", safeUuid, -1, 0, 0)
            );
            LOGGER.info("[WebDisc][Jukebox] sent STOP PlayWebDiscMessage: uuid={}, pos={}", safeUuid, pos);
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][Jukebox] stop: failed to send STOP PlayWebDiscMessage: {}", t.toString());
        }
    }

    @Inject(method = "startPlaying", at = @At("TAIL"))
    private void webdisc$onStartPlaying(CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;

        BlockPos pos;
        Level level;
        try {
            pos = self.getBlockPos();
            level = self.getLevel();
        } catch (Throwable t) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return;
        }

        ItemStack stack;
        try {
            stack = self.getItem(0);
        } catch (Throwable t) {
            return;
        }
        if (stack == null || stack.isEmpty()) return;

        CompoundTag tag;
        try {
            tag = stack.getTag();
        } catch (Throwable t) {
            return;
        }
        if (tag == null) return;

        boolean finalized = tag.getBoolean("webdisc:finalized");
        String url = tag.getString(WebDiscItem.URL_NBT);
        if (!finalized) return;
        if (url == null || url.isEmpty()) return;

        int lengthTicks;
        try {
            lengthTicks = WebDiscItem.getDurationTicks(stack);
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][Jukebox] startPlaying: failed to read durationTicks: {}", t.toString());
            return;
        }
        if (lengthTicks <= 0) {
            LOGGER.info("[WebDisc][Jukebox] startPlaying: invalid durationTicks={}, pos={}", lengthTicks, pos);
            return;
        }

        try {
            self.setItem(0, ItemStack.EMPTY);
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][Jukebox] startPlaying: failed to clear jukebox slot: {}", t.toString());
        }

        UUID uuid = uuidForJukebox(serverLevel, pos);

        long now = serverLevel.getGameTime();
        long finish = now + (long) lengthTicks;

        try {
            WebDiscJukeboxSyncRegistry.put(
                    uuid,
                    serverLevel.dimension(),
                    pos,
                    -1,
                    url,
                    lengthTicks,
                    now,
                    finish
            );
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][Jukebox] startPlaying: failed to register in WebDiscJukeboxSyncRegistry: {}", t.toString());
        }

        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(pos)),
                    new PlayWebDiscMessage(pos, url, uuid, -1, 0, lengthTicks)
            );
            LOGGER.info("[WebDisc][Jukebox] startPlaying: sent PlayWebDiscMessage(uuid={}, pos={}, lengthTicks={})", uuid, pos, lengthTicks);
        } catch (Throwable t) {
            LOGGER.info("[WebDisc][Jukebox] startPlaying: failed to send PlayWebDiscMessage: {}", t.toString());
        }
    }

    @Inject(method = "stopPlaying", at = @At("TAIL"))
    private void webdisc$onStopPlaying(CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;

        BlockPos pos;
        Level level;
        try {
            pos = self.getBlockPos();
            level = self.getLevel();
        } catch (Throwable t) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return;
        }

        UUID uuid = uuidForJukebox(serverLevel, pos);

        try {
            WebDiscJukeboxSyncRegistry.remove(uuid);
        } catch (Throwable ignored) {}

        sendStop(serverLevel, pos, uuid);
    }

    @Inject(method = "popOutRecord", at = @At("HEAD"))
    private void webdisc$onPopOutRecord(CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;

        BlockPos pos;
        Level level;
        try {
            pos = self.getBlockPos();
            level = self.getLevel();
        } catch (Throwable t) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return;
        }

        UUID uuid = uuidForJukebox(serverLevel, pos);

        try {
            WebDiscJukeboxSyncRegistry.remove(uuid);
        } catch (Throwable ignored) {}

        sendStop(serverLevel, pos, uuid);
    }
}