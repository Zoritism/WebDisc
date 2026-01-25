package com.zoritism.webdisc.mixin.webdisc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler$SoundInfo", remap = false)
public abstract class MixinServerStorageSoundHandlerSoundInfo {

    private static final Logger logger = LoggerFactory.getLogger("WebDisc");

    @Shadow
    @Final
    private WeakReference<Runnable> onFinishedHandler;

    @Shadow
    private long finishTime;

    @Shadow
    private long lastKeepAliveTime;

    @Inject(method = "runOnFinished()V", at = @At("HEAD"))
    private void webdisc$runOnFinishedHead(CallbackInfo ci) {
        Runnable handler = null;
        try {
            handler = (onFinishedHandler != null) ? onFinishedHandler.get() : null;
        } catch (Throwable ignored) {}

        logger.info("[WebDisc] SC SoundInfo.runOnFinished HEAD handlerNull={} finishTime={} lastKeepAliveTime={}",
                handler == null, finishTime, lastKeepAliveTime);
    }

    @Inject(method = "runOnFinished()V", at = @At("TAIL"))
    private void webdisc$runOnFinishedTail(CallbackInfo ci) {
        logger.info("[WebDisc] SC SoundInfo.runOnFinished TAIL finishTime={} lastKeepAliveTime={}",
                finishTime, lastKeepAliveTime);
    }
}