package com.zoritism.webdisc.mixin.webdisc;

import com.zoritism.webdisc.WebDiscPlaybackRegistry;
import com.zoritism.webdisc.client.WebDiscClientHandler;
import com.zoritism.webdisc.server.WebDiscJukeboxSyncRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.UUID;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.SoundFinishedNotificationMessage", remap = false)
public abstract class MixinSoundFinishedNotificationMessage {

    @Inject(
            method = "handleMessage(Lnet/minecraft/server/level/ServerPlayer;Lnet/p3pp3rf1y/sophisticatedcore/upgrades/jukebox/SoundFinishedNotificationMessage;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void webdisc$logAndFilter(ServerPlayer sender,
                                             Object msg,
                                             CallbackInfo ci) {
        if (sender == null || msg == null) {
            return;
        }

        UUID storageUuid = null;
        try {
            Field f = msg.getClass().getDeclaredField("storageUuid");
            f.setAccessible(true);
            Object uuidObj = f.get(msg);
            if (uuidObj instanceof UUID u) {
                storageUuid = u;
            }
        } catch (Throwable ignored) {}

        if (storageUuid == null) {
            return;
        }

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