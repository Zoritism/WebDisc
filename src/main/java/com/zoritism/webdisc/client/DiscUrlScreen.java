package com.zoritism.webdisc.client;

import com.mojang.logging.LogUtils;
import com.zoritism.webdisc.network.NetworkHandler;
import com.zoritism.webdisc.client.audio.AudioHandlerClient;
import com.zoritism.webdisc.client.audio.WebDiscDurationHelper;
import com.zoritism.webdisc.network.message.FinalizeRecordMessage;
import com.zoritism.webdisc.network.message.SetUrlMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

public class DiscUrlScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int WIDTH = 220;
    private static final int HEIGHT = 104;

    private enum UiState {
        WAITING,
        DOWNLOADING,
        SUCCESS,
        ERROR
    }

    private EditBox urlField;
    private Button saveButton;
    private Button closeOrCancelButton;

    private final String initialUrl;

    private UiState state = UiState.WAITING;
    private Component statusText = Component.translatable("screen.webdisc.status.waiting");
    private String lastUrl = "";

    private CompletableFuture<Boolean> activeDownloadFuture;

    public DiscUrlScreen(String initialUrl) {
        super(Component.translatable("screen.webdisc.title"));
        this.initialUrl = initialUrl == null ? "" : initialUrl;
        this.lastUrl = this.initialUrl;
    }

    public static void open(String initialUrl) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.setScreen(new DiscUrlScreen(initialUrl));
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

        urlField = new EditBox(
                font,
                x + 10,
                y + 25,
                WIDTH - 20,
                14,
                Component.translatable("screen.webdisc.input")
        );
        urlField.setCanLoseFocus(false);
        urlField.setBordered(true);
        urlField.setMaxLength(400);
        urlField.setValue(initialUrl);
        addRenderableWidget(urlField);
        setInitialFocus(urlField);

        int btnY = y + HEIGHT - 25;

        saveButton = Button.builder(
                        Component.translatable("screen.webdisc.accept"),
                        b -> onSavePressed())
                .bounds(x + 10, btnY, 90, 20)
                .build();

        closeOrCancelButton = Button.builder(
                        Component.translatable("screen.webdisc.close"),
                        b -> onCloseOrCancelPressed())
                .bounds(x + WIDTH - 10 - 90, btnY, 90, 20)
                .build();

        addRenderableWidget(saveButton);
        addRenderableWidget(closeOrCancelButton);

        refreshControls();
    }

    private void setState(UiState newState, Component newStatus) {
        this.state = newState;
        this.statusText = newStatus != null ? newStatus : Component.empty();
        refreshControls();
    }

    private void refreshControls() {
        boolean downloading = state == UiState.DOWNLOADING;
        boolean success = state == UiState.SUCCESS;

        if (urlField != null) {
            urlField.setEditable(!downloading && !success);
        }
        if (saveButton != null) {
            saveButton.active = !downloading && !success;
        }
        if (closeOrCancelButton != null) {
            closeOrCancelButton.setMessage(
                    downloading
                            ? Component.translatable("screen.webdisc.cancel")
                            : Component.translatable("screen.webdisc.close")
            );
        }
    }

    private void onSavePressed() {
        if (state == UiState.DOWNLOADING || state == UiState.SUCCESS) {
            return;
        }

        String url = urlField != null ? urlField.getValue().trim() : "";
        if (url.isEmpty()) {
            setState(UiState.ERROR, Component.translatable("screen.webdisc.status.invalid_url"));
            return;
        }

        this.lastUrl = url;

        // 1) Сохраняем URL на сервере (сброс finalized/duration делается на сервере)
        NetworkHandler.CHANNEL.sendToServer(new SetUrlMessage(url));

        // 2) Запускаем клиентскую скачку (GUI остаётся открытым)
        setState(UiState.DOWNLOADING, Component.translatable("screen.webdisc.status.downloading"));

        AudioHandlerClient handler = new AudioHandlerClient();

        // если вдруг уже скачано - можно не грузить, а сразу пробить длительность
        if (handler.hasOgg(url)) {
            onDownloadedOk(url);
            return;
        }

        activeDownloadFuture = handler.downloadAsOgg(url);
        activeDownloadFuture.thenAccept(success -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            mc.execute(() -> {
                if (state != UiState.DOWNLOADING) {
                    // уже отменили/закрыли/поменяли состояние
                    return;
                }
                if (!Boolean.TRUE.equals(success)) {
                    setState(UiState.ERROR, Component.translatable("screen.webdisc.status.error"));
                    return;
                }
                onDownloadedOk(url);
            });
        });
    }

    private void onDownloadedOk(String url) {
        int ticks = WebDiscDurationHelper.getLengthTicksForUrl(url);
        if (ticks <= 0) {
            setState(UiState.ERROR, Component.translatable("screen.webdisc.status.duration_failed"));
            return;
        }

        // Финализация на сервере: выставит finalized=true, durationTicks, bucketTicks
        NetworkHandler.CHANNEL.sendToServer(new FinalizeRecordMessage(url, ticks));

        setState(UiState.SUCCESS, Component.translatable("screen.webdisc.status.success"));
    }

    private void onCloseOrCancelPressed() {
        if (state == UiState.DOWNLOADING) {
            cancelActiveDownload(true);
            setState(UiState.WAITING, Component.translatable("screen.webdisc.status.waiting"));
            return;
        }
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void cancelActiveDownload(boolean deleteFiles) {
        try {
            if (activeDownloadFuture != null) {
                activeDownloadFuture.cancel(true);
                activeDownloadFuture = null;
            }
        } catch (Throwable ignored) {}

        if (lastUrl == null || lastUrl.isEmpty()) {
            return;
        }

        try {
            AudioHandlerClient handler = new AudioHandlerClient();
            handler.cancelDownload(lastUrl);
            if (deleteFiles) {
                handler.deleteAllFilesForUrl(lastUrl);
            }
        } catch (Throwable t) {
            LOGGER.info("[WebDisc] DiscUrlScreen.cancelActiveDownload: {}", t.toString());
        }
    }

    @Override
    public void onClose() {
        // Требование: закрытие GUI = cancel, но GUI не должен закрываться во время DOWNLOADING
        if (state == UiState.DOWNLOADING) {
            cancelActiveDownload(true);
            setState(UiState.WAITING, Component.translatable("screen.webdisc.status.waiting"));
            return;
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (state == UiState.DOWNLOADING) {
                cancelActiveDownload(true);
                setState(UiState.WAITING, Component.translatable("screen.webdisc.status.waiting"));
                return true;
            }
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (state != UiState.DOWNLOADING && state != UiState.SUCCESS) {
                onSavePressed();
                return true;
            }
        }

        if (urlField != null && (urlField.keyPressed(keyCode, scanCode, modifiers) || urlField.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        String value = urlField != null ? urlField.getValue() : initialUrl;
        super.resize(mc, w, h);
        if (urlField != null) {
            urlField.setValue(value);
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

        gg.fill(x, y, x + WIDTH, y + HEIGHT, 0xCC000000);
        gg.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF202020);

        gg.drawString(font, Component.translatable("screen.webdisc.label"), x + 10, y + 10, 0xFFFFFF, false);

        // статус по центру над кнопками и под инпутом
        int statusY = y + 44;
        int statusW = font.width(statusText);
        gg.drawString(font, statusText, x + (WIDTH - statusW) / 2, statusY, 0xE0E0E0, false);

        super.render(gg, mouseX, mouseY, partialTick);
    }
}