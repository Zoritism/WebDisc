package com.zoritism.webdisc.mixin.webdisc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimpleChannel.class, remap = false)
public abstract class MixinSimpleChannelBlockFinishedWhenMuted {

    private static final String OLD_SC_FINISHED_MSG =
            "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.SoundFinishedNotificationMessage";

    @Inject(method = "sendToServer(Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private void webdisc$blockFinishedWhenMuted(Object msg, CallbackInfo ci) {
        if (msg == null) {
            return;
        }

        String className;
        try {
            className = msg.getClass().getName();
        } catch (Throwable ignored) {
            return;
        }

        // В новом SophisticatedCore этого сообщения нет, но оставляем поведение для старых сборок без hard-dependency.
        if (!OLD_SC_FINISHED_MSG.equals(className)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Options opt = mc.options;
        if (opt == null) {
            return;
        }

        float master;
        float records;
        try {
            master = opt.getSoundSourceVolume(SoundSource.MASTER);
            records = opt.getSoundSourceVolume(SoundSource.RECORDS);
        } catch (Throwable ignored) {
            return;
        }

        if (master <= 0.0F || records <= 0.0F) {
            ci.cancel();
        }
    }
}