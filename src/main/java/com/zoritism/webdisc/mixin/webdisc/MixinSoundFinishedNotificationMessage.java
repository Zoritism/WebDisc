package com.zoritism.webdisc.mixin.webdisc;

import com.zoritism.webdisc.WebDiscPlaybackRegistry;
import com.zoritism.webdisc.client.WebDiscClientHandler;
import com.zoritism.webdisc.server.WebDiscJukeboxSyncRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.SoundFinishedNotificationMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.UUID;

@Mixin(value = SoundFinishedNotificationMessage.class, remap = false)
public abstract class MixinSoundFinishedNotificationMessage {

    @Inject(
            method = "handleMessage(Lnet/minecraft/server/level/ServerPlayer;Lnet/p3pp3rf1y/sophisticatedcore/upgrades/jukebox/SoundFinishedNotificationMessage;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void webdisc$logAndFilter(ServerPlayer sender,
                                             SoundFinishedNotificationMessage msg,
                                             CallbackInfo ci) {
        if (sender == null || msg == null) {
            return;
        }

        UUID storageUuid = null;
        try {
            Field f = SoundFinishedNotificationMessage.class.getDeclaredField("storageUuid");
            f.setAccessible(true);
            Object uuidObj = f.get(msg);
            if (uuidObj instanceof UUID u) {
                storageUuid = u;
            }
        } catch (Throwable ignored) {}

        if (storageUuid == null) {
            return;
        }

        try {
            long gt = sender.level().getGameTime();
            String dim = sender.level().dimension().location().toString();
        } catch (Throwable ignored) {}

        boolean isWebDiscSlot = false;
        try {
            isWebDiscSlot = WebDiscPlaybackRegistry.isWebDisc(storageUuid);
        } catch (Throwable ignored) {}

        try {
            WebDiscClientHandler.onStorageSoundFinished(storageUuid);
        } catch (Throwable ignored) {}

        try {
            WebDiscPlaybackRegistry.clear(storageUuid);
        } catch (Throwable ignored) {}

        try {
            WebDiscJukeboxSyncRegistry.remove(storageUuid);
        } catch (Throwable ignored) {}

        if (isWebDiscSlot) {
            try {
            } catch (Throwable ignored) {}
        }
    }
}