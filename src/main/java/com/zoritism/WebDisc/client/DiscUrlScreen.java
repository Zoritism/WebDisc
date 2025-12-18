package com.zoritism.webdisc.client;

import com.zoritism.webdisc.network.WebDiscNetwork;
import com.zoritism.webdisc.network.payload.SetDiscUrlMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class DiscUrlScreen extends Screen {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 44;

    private EditBox urlField;
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

        urlField = new EditBox(font, x + 8, y + 18, WIDTH - 16, 12,
                Component.translatable("screen.webdisc.input"));
        urlField.setCanLoseFocus(false);
        urlField.setBordered(true);
        urlField.setMaxLength(400);
        urlField.setValue(initialUrl);
        addRenderableWidget(urlField);
        setInitialFocus(urlField);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            String newUrl = urlField != null ? urlField.getValue() : "";
            WebDiscNetwork.CHANNEL.sendToServer(new SetDiscUrlMessage(newUrl));
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.closeContainer();
            }
            return true;
        }
        if (urlField != null && (urlField.keyPressed(keyCode, scanCode, modifiers) || urlField.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        gg.fill(x, y, x + WIDTH, y + HEIGHT, 0xAA000000);
        if (urlField == null) {
            init();
        }
        if (urlField != null) {
            urlField.render(gg, mouseX, mouseY, partialTick);
        }
        gg.drawString(font, Component.translatable("screen.webdisc.label"), x + 8, y + 6, 0xFFFFFF, false);
    }
}

