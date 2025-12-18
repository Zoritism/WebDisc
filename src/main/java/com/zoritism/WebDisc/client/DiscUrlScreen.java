package com.zoritism.webdisc.client;

import com.zoritism.webdisc.network.WebDiscNetwork;
import com.zoritism.webdisc.network.message.SetUrlMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class DiscUrlScreen extends Screen {

    private static final int WIDTH = 220;
    private static final int HEIGHT = 90;

    private EditBox urlField;
    private Button acceptButton;
    private Button cancelButton;
    private final String initialUrl;

    public DiscUrlScreen(String initialUrl) {
        super(Component.translatable("screen.webdisc.title"));
        this.initialUrl = initialUrl == null ? "" : initialUrl;
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

        urlField = new EditBox(font, x + 10, y + 25, WIDTH - 20, 14,
                Component.translatable("screen.webdisc.input"));
        urlField.setCanLoseFocus(false);
        urlField.setBordered(true);
        urlField.setMaxLength(400);
        urlField.setValue(initialUrl);
        addRenderableWidget(urlField);
        setInitialFocus(urlField);

        int btnY = y + HEIGHT - 25;
        acceptButton = Button.builder(
                        Component.translatable("screen.webdisc.accept"),
                        b -> onAccept())
                .bounds(x + 10, btnY, 90, 20)
                .build();
        cancelButton = Button.builder(
                        Component.translatable("screen.webdisc.cancel"),
                        b -> onCancel())
                .bounds(x + WIDTH - 10 - 90, btnY, 90, 20)
                .build();

        addRenderableWidget(acceptButton);
        addRenderableWidget(cancelButton);
    }

    private void onAccept() {
        String newUrl = urlField != null ? urlField.getValue().trim() : "";
        WebDiscNetwork.CHANNEL.sendToServer(new SetUrlMessage(newUrl));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void onCancel() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onCancel();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            onAccept();
            return true;
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

        super.render(gg, mouseX, mouseY, partialTick);
    }
}