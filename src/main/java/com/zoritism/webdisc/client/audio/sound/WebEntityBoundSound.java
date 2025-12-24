package com.zoritism.webdisc.client.audio.sound;

import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.world.entity.Entity;

public class WebEntityBoundSound extends WebFileSound implements TickableSoundInstance {

    private final Entity entity;
    private boolean stopped;

    public WebEntityBoundSound(String url, Entity entity) {
        super(url, entity.position());
        this.entity = entity;
        try {
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    protected void stopInternal() {
        this.stopped = true;
        try {
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean canPlaySound() {
        return !entity.isSilent();
    }

    @Override
    public void tick() {
        if (entity.isRemoved()) {
            stopInternal();
        } else {
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
        }
    }
}