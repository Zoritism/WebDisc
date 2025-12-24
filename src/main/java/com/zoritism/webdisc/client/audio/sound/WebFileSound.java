package com.zoritism.webdisc.client.audio.sound;

import com.zoritism.webdisc.util.WebHashing;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class WebFileSound implements SoundInstance {

    private static final String MODID = "webdisc";

    private final String originalUrl;

    private final String urlKey;
    protected double x;
    protected double y;
    protected double z;

    public WebFileSound(String url, Vec3 pos) {
        this.originalUrl = url == null ? "" : url;
        this.urlKey = hashUrl(url);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        try {
        } catch (Throwable ignored) {}
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getUrlKey() {
        return urlKey;
    }

    private static String minecraftify(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static String hashUrl(String url) {
        String mc = minecraftify(url);
        String hash = WebHashing.sha256(mc);
        return (hash == null || hash.isEmpty()) ? mc : hash;
    }

    @Override
    public ResourceLocation getLocation() {


        ResourceLocation loc = new ResourceLocation(MODID, "customsound/" + urlKey);
        try {
        } catch (Throwable ignored) {}
        return loc;
    }

    @Nullable
    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        return new WeighedSoundEvents(getLocation(), null);
    }

    @Override
    public Sound getSound() {
        return new Sound(
                getLocation().toString(),
                ConstantFloat.of(getVolume()),
                ConstantFloat.of(getPitch()),
                1,
                Sound.Type.SOUND_EVENT,
                true,
                false,
                64
        );
    }

    @Override
    public SoundSource getSource() {
        return SoundSource.RECORDS;
    }

    @Override
    public boolean isLooping() {
        return false;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public int getDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        return 1.0F;
    }

    @Override
    public float getPitch() {
        return 1.0F;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public Attenuation getAttenuation() {
        return Attenuation.LINEAR;
    }
}