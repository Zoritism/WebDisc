package com.zoritism.webdisc.config;

import com.zoritism.webdisc.WebDiscMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public final class WebDiscConfig {

    private WebDiscConfig() {}

    public static final ForgeConfigSpec serverSpec;
    public static final Server SERVER;

    public static final ForgeConfigSpec clientSpec;
    public static final Client CLIENT;

    static {
        Pair<Server, ForgeConfigSpec> serverPair =
                new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = serverPair.getLeft();
        serverSpec = serverPair.getRight();

        Pair<Client, ForgeConfigSpec> clientPair =
                new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        clientSpec = clientPair.getRight();
    }

    public static final class Server {
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> whitelistedWebsites;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> whitelistedUrls;

        Server(ForgeConfigSpec.Builder b) {
            b.comment("Server-side whitelist for WebDisc URLs").push("server");

            whitelistedWebsites = b
                    .comment("Site names shown to player when URL is not allowed")
                    .defineList("whitelistedWebsites",
                            List.of("YouTube", "Discord CDN", "Google Drive", "Dropbox"),
                            String.class::isInstance);

            whitelistedUrls = b
                    .comment("URL prefixes allowed for WebDisc playback")
                    .defineList("whitelistedUrls",
                            List.of(
                                    "https://youtu.be",
                                    "https://www.youtube.com",
                                    "https://youtube.com",
                                    "https://cdn.discordapp.com",
                                    "https://drive.google.com/uc",
                                    "https://www.dropbox.com/scl",
                                    "https://dropbox.com/scl"
                            ),
                            String.class::isInstance);

            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue downloadFFmpeg;
        public final ForgeConfigSpec.BooleanValue downloadYoutubeDL;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Client-side settings for WebDisc executables").push("client");

            downloadFFmpeg = b
                    .comment("Download ffmpeg into WebDisc/ffmpeg if not found in PATH or local folder")
                    .define("downloadFFmpeg", false);

            downloadYoutubeDL = b
                    .comment("Download yt-dlp into WebDisc/youtubedl if not found in PATH or local folder")
                    .define("downloadYoutubeDL", false);

            b.pop();
        }
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading e) {
        WebDiscMod.LOGGER.info("[WebDisc] Config loaded: {}", e.getConfig().getFileName());
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading e) {
        WebDiscMod.LOGGER.info("[WebDisc] Config reloaded: {}", e.getConfig().getFileName());
    }
}