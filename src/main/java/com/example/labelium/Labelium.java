package com.example.showfps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = ShowFPS.MODID, name = ShowFPS.NAME, version = ShowFPS.VERSION,
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public class ShowFPS {

    public static final String MODID = "showfps";
    public static final String NAME = "ShowFPS";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    @SideOnly(Side.CLIENT)
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) return;

        Minecraft mc = Minecraft.getMinecraft();
        int fps = Minecraft.getDebugFPS();
        String text = fps + " FPS";

        mc.fontRenderer.drawStringWithShadow(text, 2, 2, 0xFFFFFF);
    }
}
