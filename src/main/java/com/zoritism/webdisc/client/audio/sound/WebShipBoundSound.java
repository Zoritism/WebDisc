package com.zoritism.webdisc.client.audio.sound;

import com.zoritism.heavybullet.util.VS2ClientTransforms;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class WebShipBoundSound extends WebFileSound implements TickableSoundInstance {

    private final BlockPos pos;
    private boolean stopped;

    public WebShipBoundSound(String url, BlockPos pos) {
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
            world = pos.getCenter();
        }

        this.x = world.x;
        this.y = world.y;
        this.z = world.z;
    }
}