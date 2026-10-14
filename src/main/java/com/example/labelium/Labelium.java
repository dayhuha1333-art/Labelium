package com.example.labelium;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Mod(modid = Labelium.MODID, name = Labelium.NAME, version = Labelium.VERSION,
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public class Labelium {

    public static final String MODID = "labelium";
    public static final String NAME = "Labelium";
    public static final String VERSION = "1.0.0";

    public static final KeyBinding OPEN_GUI = new KeyBinding(
        "key.labelium.open", Keyboard.KEY_RSHIFT, "Labelium"
    );

    @Mod.EventHandler
    @SideOnly(Side.CLIENT)
    public void preInit(FMLPreInitializationEvent event) {
        ClientRegistry.registerKeyBinding(OPEN_GUI);
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
        MinecraftForge.EVENT_BUS.register(new WaypointRenderer());
        LabelData.load();
    }

    public static class KeyHandler {
        private boolean wasPressed = false;

        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            boolean pressed = OPEN_GUI.isKeyDown();
            if (pressed && !wasPressed) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.currentScreen instanceof GuiLabelium) {
                    mc.displayGuiScreen(null);
                } else if (mc.currentScreen == null) {
                    mc.displayGuiScreen(new GuiLabelium());
                }
            }
            wasPressed = pressed;
        }
    }

    public static class Label {
        public String name;
        public int x, y, z;
        public boolean enabled;

        public Label(String name, int x, int y, int z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.enabled = true;
        }
    }

    public static class LabelData {
        private static final List<Label> LABELS = new ArrayList<>();
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static File file;

        public static List<Label> getLabels() {
            return LABELS;
        }

        public static void add(Label label) {
            LABELS.add(label);
            save();
        }

        public static void remove(int index) {
            if (index >= 0 && index < LABELS.size()) {
                LABELS.remove(index);
                save();
            }
        }

        public static void save() {
            if (file == null) return;
            try (Writer w = new FileWriter(file)) {
                GSON.toJson(LABELS, w);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static void load() {
            file = new File(Minecraft.getMinecraft().mcDataDir, "config/labelium.json");
            if (!file.exists()) return;
            try (Reader r = new FileReader(file)) {
                Type type = new TypeToken<List<Label>>() {}.getType();
                List<Label> loaded = GSON.fromJson(r, type);
                if (loaded != null) {
                    LABELS.clear();
                    LABELS.addAll(loaded);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public static class WaypointRenderer {

        @SubscribeEvent
        public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
            if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) return;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.world == null) return;

            int[] screen = new int[2];
            for (Label label : LabelData.getLabels()) {
                if (!label.enabled) continue;

                Vec3d pos = new Vec3d(label.x + 0.5, label.y + 0.5, label.z + 0.5);
                if (worldToScreen(pos, event.getPartialTicks(), screen)) {
                    drawDot(screen[0], screen[1], 4);
                    mc.fontRenderer.drawStringWithShadow(label.name, screen[0] + 6, screen[1] - 4, 0xFF0000);
                }
            }
        }

        private boolean worldToScreen(Vec3d worldPos, float partialTicks, int[] out) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getRenderViewEntity() == null) return false;

            Vec3d cam = mc.getRenderViewEntity().getPositionEyes(partialTicks);

            double dx = worldPos.x - cam.x;
            double dy = worldPos.y - cam.y;
            double dz = worldPos.z - cam.z;

            double yaw = Math.toRadians(mc.getRenderViewEntity().rotationYaw);
            double pitch = Math.toRadians(mc.getRenderViewEntity().rotationPitch);

            double x1 = dx * Math.cos(yaw) - dz * Math.sin(yaw);
            double z1 = dx * Math.sin(yaw) + dz * Math.cos(yaw);

            double y1 = dy * Math.cos(pitch) - z1 * Math.sin(pitch);
            double z2 = dy * Math.sin(pitch) + z1 * Math.cos(pitch);

            if (z2 >= -0.1) return false;

            double fov = mc.gameSettings.fovSetting;
            double aspect = (double) mc.displayWidth / mc.displayHeight;
            double tanHalfFov = Math.tan(Math.toRadians(fov) / 2.0);

            double ndcX = x1 / (-z2 * tanHalfFov * aspect);
            double ndcY = y1 / (-z2 * tanHalfFov);

            if (ndcX < -1 || ndcX > 1 || ndcY < -1 || ndcY > 1) return false;

            ScaledResolution sr = new ScaledResolution(mc);
            out[0] = (int) ((ndcX + 1) / 2 * sr.getScaledWidth());
            out[1] = (int) ((1 - ndcY) / 2 * sr.getScaledHeight());
            return true;
        }

        private void drawDot(int x, int y, int size) {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1f, 0f, 0f, 1f);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            buf.pos(x - size, y + size, 0).endVertex();
            buf.pos(x + size, y + size, 0).endVertex();
            buf.pos(x + size, y - size, 0).endVertex();
            buf.pos(x - size, y - size, 0).endVertex();
            tess.draw();

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    @SideOnly(Side.CLIENT)
    public static class GuiLabelium extends GuiScreen {

        private GuiTextField xField, yField, zField;
        private boolean choosingType = false;

        @Override
        public void initGui() {
            this.buttonList.clear();
            int cx = this.width / 2;

            this.buttonList.add(new GuiButton(0, cx - 50, 30, 100, 20, "+"));

            if (choosingType) {
                this.buttonList.add(new GuiButton(1, cx - 50, 55, 100, 20, "Here"));
                this.buttonList.add(new GuiButton(2, cx - 50, 80, 100, 20, "There"));

                xField = new GuiTextField(3, this.fontRenderer, cx - 50, 110, 100, 18);
                yField = new GuiTextField(4, this.fontRenderer, cx - 50, 130, 100, 18);
                zField = new GuiTextField(5, this.fontRenderer, cx - 50, 150, 100, 18);
            }

            int listY = choosingType ? 180 : 60;
            for (int i = 0; i < LabelData.getLabels().size(); i++) {
                Label label = LabelData.getLabels().get(i);
                String text = label.name + " [" + label.x + ", " + label.y + ", " + label.z + "]";
                GuiButton b = new GuiButton(100 + i, cx - 110, listY + i * 22, 200, 20, text);
                if (label.enabled) {
                    b.packedFGColour = 0xFF0000;
                } else {
                    b.packedFGColour = 0xFFFFFF;
                }
                this.buttonList.add(b);

                GuiButton del = new GuiButton(200 + i, cx + 95, listY + i * 22, 20, 20, "X");
                del.packedFGColour = 0xFF5555;
                this.buttonList.add(del);
            }
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (button.id == 0) {
                choosingType = !choosingType;
                this.initGui();
            } else if (button.id == 1) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.player != null) {
                    LabelData.add(new Label("Here",
                        (int) mc.player.posX,
                        (int) mc.player.posY,
                        (int) mc.player.posZ));
                }
                choosingType = false;
                this.initGui();
            } else if (button.id == 2) {
                try {
                    int x = Integer.parseInt(xField.getText().trim());
                    int y = Integer.parseInt(yField.getText().trim());
                    int z = Integer.parseInt(zField.getText().trim());
                    LabelData.add(new Label("There", x, y, z));
                    choosingType = false;
                    this.initGui();
                } catch (NumberFormatException ignored) {
                }
            } else if (button.id >= 200) {
                int idx = button.id - 200;
                LabelData.remove(idx);
                this.initGui();
            } else if (button.id >= 100) {
                int idx = button.id - 100;
                if (idx < LabelData.getLabels().size()) {
                    Label l = LabelData.getLabels().get(idx);
                    l.enabled = !l.enabled;
                    LabelData.save();
                    this.initGui();
                }
            }
            super.actionPerformed(button);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();
            this.drawCenteredString(this.fontRenderer, "Labelium", this.width / 2, 10, 0xFFFFFF);
            if (choosingType) {
                this.drawString(this.fontRenderer, "X:", this.width / 2 - 70, 115, 0xFFFFFF);
                this.drawString(this.fontRenderer, "Y:", this.width / 2 - 70, 135, 0xFFFFFF);
                this.drawString(this.fontRenderer, "Z:", this.width / 2 - 70, 155, 0xFFFFFF);
                if (xField != null) {
                    xField.drawTextBox();
                    yField.drawTextBox();
                    zField.drawTextBox();
                }
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            if (xField != null) {
                xField.textboxKeyTyped(typedChar, keyCode);
                yField.textboxKeyTyped(typedChar, keyCode);
                zField.textboxKeyTyped(typedChar, keyCode);
            }
            if (keyCode == 1) {
                this.mc.displayGuiScreen(null);
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            if (xField != null) {
                xField.mouseClicked(mouseX, mouseY, mouseButton);
                yField.mouseClicked(mouseX, mouseY, mouseButton);
                zField.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
