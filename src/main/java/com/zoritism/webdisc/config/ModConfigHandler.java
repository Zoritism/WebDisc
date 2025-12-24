package com.zoritism.webdisc.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class ModConfigHandler {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    public static class Common {

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> webdiscWhitelistedWebsites;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> webdiscWhitelistedUrls;
        public final ForgeConfigSpec.BooleanValue webdiscDownloadFFmpeg;
        public final ForgeConfigSpec.BooleanValue webdiscDownloadYoutubeDL;



        public Common(ForgeConfigSpec.Builder builder) {

            builder.push("WebDisc");
            webdiscWhitelistedWebsites = builder
                    .comment("Site names shown to player when URL is not allowed")
                    .defineList(
                            "whitelistedWebsites",
                            List.of("YouTube", "Discord CDN", "Google Drive", "Dropbox"),
                            o -> o instanceof String
                    );

            webdiscWhitelistedUrls = builder
                    .comment("URL prefixes allowed for WebDisc playback")
                    .defineList(
                            "whitelistedUrls",
                            List.of(
                                    "https://youtu.be",
                                    "https://www.youtube.com",
                                    "https://youtube.com",
                                    "https://cdn.discordapp.com",
                                    "https://drive.google.com/uc",
                                    "https://www.dropbox.com/scl",
                                    "https://dropbox.com/scl"
                            ),
                            o -> o instanceof String
                    );

            webdiscDownloadFFmpeg = builder
                    .comment("Download ffmpeg into WebDisc/ffmpeg if not found in PATH or local folder")
                    .define("downloadFFmpeg", true);

            webdiscDownloadYoutubeDL = builder
                    .comment("Download yt-dlp into WebDisc/youtubedl if not found in PATH or local folder")
                    .define("downloadYoutubeDL", true);

            builder.pop();
        }
    }
}