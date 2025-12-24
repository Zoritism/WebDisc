package com.zoritism.webdisc.client.audio.sound;

import com.mojang.logging.LogUtils;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

public class WebEntityBoundSound extends WebFileSound implements TickableSoundInstance {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Entity entity;
    private boolean stopped;

    public WebEntityBoundSound(String url, Entity entity) {
        super(url, entity.position());
        this.entity = entity;
        try {
            LOGGER.info("[WebDisc][WebEntityBoundSound] created for entity id={}, class={}, url='{}', urlKey='{}'",
                    entity.getId(), entity.getClass().getName(), getOriginalUrl(), getUrlKey());
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    protected void stopInternal() {
        this.stopped = true;
        try {
            LOGGER.info("[WebDisc][WebEntityBoundSound] stopInternal: entityId={}, urlKey='{}'", entity.getId(), getUrlKey());
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