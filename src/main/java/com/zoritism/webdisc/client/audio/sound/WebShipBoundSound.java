package com.zoritism.webdisc.client.audio.sound;

import com.mojang.logging.LogUtils;
import com.zoritism.heavybullet.util.VS2ClientTransforms;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Звук, привязанный к BlockPos, который может быть на корабле VS2.
 * Каждый тик пересчитывает world-позицию через VS2ClientTransforms.
 */
public class WebShipBoundSound extends WebFileSound implements TickableSoundInstance {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final BlockPos pos;
    private boolean stopped;

    public WebShipBoundSound(String url, BlockPos pos) {
        // стартовая позиция: если есть корабль — world, иначе обычный центр блока
        super(url, initialPos(pos));
        this.pos = pos;
    }

    private static Vec3 initialPos(BlockPos pos) {
        if (pos == null) return Vec3.ZERO;
        Vec3 world = VS2ClientTransforms.shipLocalBlockCenterToWorld(pos);
        return world != null ? world : pos.getCenter();
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    protected void stopInternal() {
        this.stopped = true;
    }

    @Override
    public void tick() {
        if (pos == null) {
            stopInternal();
            return;
        }

        Vec3 world = VS2ClientTransforms.shipLocalBlockCenterToWorld(pos);
        if (world == null) {
            // не на корабле (или корабль не найден) — обычная world позиция блока
            world = pos.getCenter();
        }

        this.x = world.x;
        this.y = world.y;
        this.z = world.z;
    }
}