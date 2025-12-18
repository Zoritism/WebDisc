package com.zoritism.webdisc.client;

import com.zoritism.webdisc.WebDiscMod;
import com.zoritism.webdisc.client.audio.AudioHandlerClient;
import com.zoritism.webdisc.client.audio.sound.WebEntityBoundSound;
import com.zoritism.webdisc.client.audio.sound.WebFileSound;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WebDiscClientHandler {

    private WebDiscClientHandler() {}

    private static final Map<Vec3, WebFileSound> soundsByPos = new HashMap<>();
    private static final Map<UUID, WebFileSound> soundsByUuid = new HashMap<>();

    public static void play(Vec3 center, String url, UUID uuid, int entityId) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        Entity entity = (entityId >= 0) ? level.getEntity(entityId) : null;
        boolean entityBound = entity != null;

        WebFileSound current = !uuid.equals(Util.NIL_UUID)
                ? soundsByUuid.get(uuid)
                : soundsByPos.get(center);

        if (current != null) {
            mc.getSoundManager().stop(current);
            if (!uuid.equals(Util.NIL_UUID)) {
                soundsByUuid.remove(uuid);
            } else {
                soundsByPos.remove(center);
            }
        }

        if (url == null || url.isEmpty()) {
            WebDiscMod.LOGGER.info("[WebDisc][Client] play(): empty URL at {}, stop only", center);
            return;
        }

        AudioHandlerClient handler = new AudioHandlerClient();
        boolean has = handler.hasOgg(url);
        WebDiscMod.LOGGER.info("[WebDisc][Client] play(): center={}, url='{}', uuid={}, entityId={}, hasOgg={}",
                center, url, uuid, entityId, has);

        if (!has) {
            mc.player.sendSystemMessage(Component.translatable("webdisc.song.downloading"));
            handler.downloadAsOgg(url).thenAccept(success -> {
                if (!success) {
                    mc.player.sendSystemMessage(Component.translatable("webdisc.song.failed"));
                    WebDiscMod.LOGGER.info("[WebDisc][Client] play(): download failed for url='{}'", url);
                    return;
                }
                mc.player.sendSystemMessage(Component.translatable("webdisc.song.ready"));
                WebDiscMod.LOGGER.info("[WebDisc][Client] play(): download ok, creating sound for url='{}'", url);

                WebFileSound fs = entityBound
                        ? new WebEntityBoundSound(url, entity)
                        : new WebFileSound(url, center);

                if (!uuid.equals(Util.NIL_UUID)) {
                    soundsByUuid.put(uuid, fs);
                } else {
                    soundsByPos.put(center, fs);
                }
                mc.getSoundManager().play(fs);
            });
            return;
        }

        WebFileSound fs = entityBound
                ? new WebEntityBoundSound(url, entity)
                : new WebFileSound(url, center);

        if (!uuid.equals(Util.NIL_UUID)) {
            soundsByUuid.put(uuid, fs);
        } else {
            soundsByPos.put(center, fs);
        }
        WebDiscMod.LOGGER.info("[WebDisc][Client] play(): playing existing ogg for url='{}' at {}", url, center);
        mc.getSoundManager().play(fs);
    }
}