package dev.crit.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crit.client.accounts.Sessions;
import dev.crit.client.keys.CpsTracker;
import dev.crit.client.keys.KeyConfig;
import dev.crit.client.keys.NetStats;
import dev.crit.client.keys.OverlayRenderer;
import dev.crit.client.keys.SpeedTracker;
import dev.crit.client.keys.ScrollState;
import dev.crit.client.modules.Modules;
import dev.crit.client.skins.SkinLibrary;
import dev.crit.client.title.MenuBackground;
import dev.crit.client.title.MenuSettings;
import dev.crit.client.ui.Theme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CritClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("CritClient");
    public static final String SITE = "https://critlauncher.xyz";
    /** Skin data keeps living where Chez's Wardrobe put it, so existing skins carry over. */
    public static Path DIR;

    public static String version() {
        return FabricLoader.getInstance().getModContainer("critclient")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    }

    /** The screen currently receiving input, or null in-game. */
    public static Screen currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gui != null ? mc.gui.screen() : null;
    }

    @Override
    public void onInitializeClient() {
        DIR = FabricLoader.getInstance().getConfigDir().resolve("skinstudio");
        try {
            Files.createDirectories(DIR.resolve("skins"));
            Files.createDirectories(DIR.resolve("cache"));
        } catch (IOException e) {
            LOG.warn("Could not create config dir", e);
        }
        SkinLibrary.load();
        KeyConfig.load();
        Theme.load();
        MenuSettings.load();
        Modules.load();
        dev.crit.client.title.QuickPlay.load();

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            Sessions.applySavedOnce(); // select the startup account, then load its skin
            dev.crit.client.sky.CustomSky.reload();
            MenuBackground.load(mc);
        });

        // remembers the last server / world for the title screen's Quick Play
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> dev.crit.client.title.QuickPlay.onJoin(client));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            SpeedTracker.tick(mc);
            for (dev.crit.client.modules.Module m : Modules.all()) m.tick(mc);
            Modules.tick(); // flush any pending config change
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Modules.tick());

        // Scoreboard Tweaks: wraps the game's own scoreboard so it can be scaled and moved.
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, dev.crit.client.modules.ScoreboardModule::wrap);

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("critclient", "subway"),
                (g, deltaTracker) -> {
                    if (Modules.SUBWAY.enabled()) Modules.SUBWAY.render(g, Minecraft.getInstance());
                });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("critclient", "keys"),
                (g, deltaTracker) -> {
                    Options o = Minecraft.getInstance().options;
                    int sw = g.guiWidth(), sh = g.guiHeight();
                    boolean leftDown = o.keyAttack.isDown(), rightDown = o.keyUse.isDown();
                    CpsTracker.update(leftDown, rightDown);

                    // The Key Overlay module owns WASD / space / mouse; the Overlays module owns the readouts.
                    boolean keysOn = KeyConfig.overlayEnabled, hudOn = KeyConfig.overlaysEnabled, armorOn = KeyConfig.armorEnabled;
                    if (!keysOn && !hudOn && !armorOn) return;

                    KeyConfig.Overlay wa = KeyConfig.wasd;
                    if (keysOn && wa.enabled) {
                        int x = Math.max(0, Math.min(Math.round(wa.x * sw), sw - wa.w()));
                        int y = Math.max(0, Math.min(Math.round(wa.y * sh), sh - wa.h()));
                        OverlayRenderer.drawWasd(g, x, y, wa.scale,
                                o.keyUp.isDown(), o.keyLeft.isDown(), o.keyDown.isDown(), o.keyRight.isDown(),
                                KeyConfig.showSpace, o.keyJump.isDown());
                    }

                    KeyConfig.Overlay mo = KeyConfig.mouse;
                    if (keysOn && mo.enabled) {
                        int x = Math.max(0, Math.min(Math.round(mo.x * sw), sw - mo.w()));
                        int y = Math.max(0, Math.min(Math.round(mo.y * sh), sh - mo.h()));
                        OverlayRenderer.drawMouse(g, x, y, mo.scale, leftDown, rightDown, o.keyPickItem.isDown(),
                                ScrollState.up(), ScrollState.down());
                    }

                    KeyConfig.Overlay cp = KeyConfig.cps;
                    if (hudOn && cp.enabled) {
                        int x = Math.max(0, Math.min(Math.round(cp.x * sw), sw - cp.w()));
                        int y = Math.max(0, Math.min(Math.round(cp.y * sh), sh - cp.h()));
                        OverlayRenderer.drawCps(g, x, y, cp.scale, CpsTracker.left());
                    }

                    KeyConfig.Overlay st = KeyConfig.status;
                    Minecraft mc = Minecraft.getInstance();
                    if (hudOn && st.enabled && mc.player != null) {
                        int x = Math.max(0, Math.min(Math.round(st.x * sw), sw - st.w()));
                        int y = Math.max(0, Math.min(Math.round(st.y * sh), sh - st.h()));
                        OverlayRenderer.drawStatus(g, x, y, st.scale, statusLabel(mc, o));
                    }

                    KeyConfig.Overlay pi = KeyConfig.ping;
                    if (hudOn && pi.enabled && mc.player != null && mc.getConnection() != null) {
                        NetStats.tickPing(mc);
                        int ms = NetStats.ping();
                        if (ms < 0) { // no answer yet: fall back to the tab list's number
                            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                            if (info != null && info.getLatency() > 0) ms = info.getLatency();
                        }
                        int x = Math.max(0, Math.min(Math.round(pi.x * sw), sw - pi.w()));
                        int y = Math.max(0, Math.min(Math.round(pi.y * sh), sh - pi.h()));
                        OverlayRenderer.drawPing(g, x, y, pi.scale, ms);
                    }

                    KeyConfig.Overlay fp = KeyConfig.fps;
                    if (hudOn && fp.enabled) {
                        int x = Math.max(0, Math.min(Math.round(fp.x * sw), sw - fp.w()));
                        int y = Math.max(0, Math.min(Math.round(fp.y * sh), sh - fp.h()));
                        OverlayRenderer.drawFps(g, x, y, fp.scale, mc.getFps());
                    }

                    // Server TPS from the server's time updates, so it works on any server, not just singleplayer.
                    KeyConfig.Overlay tp = KeyConfig.tps;
                    if (hudOn && tp.enabled && mc.player != null) {
                        int t = NetStats.tps();
                        if (t < 0 && mc.getSingleplayerServer() != null) {
                            float mspt = mc.getSingleplayerServer().getAverageTickTimeNanos() / 1_000_000f;
                            t = Math.round(Math.min(20f, 1000f / Math.max(mspt, 50f)));
                        }
                        int x = Math.max(0, Math.min(Math.round(tp.x * sw), sw - tp.w()));
                        int y = Math.max(0, Math.min(Math.round(tp.y * sh), sh - tp.h()));
                        OverlayRenderer.drawTps(g, x, y, tp.scale, t);
                    }

                    KeyConfig.Overlay sp = KeyConfig.speed;
                    if (hudOn && sp.enabled && mc.player != null) {
                        int x = Math.max(0, Math.min(Math.round(sp.x * sw), sw - sp.w()));
                        int y = Math.max(0, Math.min(Math.round(sp.y * sh), sh - sp.h()));
                        OverlayRenderer.drawSpeed(g, x, y, sp.scale, SpeedTracker.blocksPerSecond());
                    }

                    KeyConfig.Overlay cm = KeyConfig.compass;
                    if (hudOn && cm.enabled && mc.player != null) {
                        int x = Math.max(0, Math.min(Math.round(cm.x * sw), sw - cm.w()));
                        int y = Math.max(0, Math.min(Math.round(cm.y * sh), sh - cm.h()));
                        OverlayRenderer.drawCompass(g, x, y, cm.scale, mc.player.getYRot());
                    }

                    KeyConfig.Overlay ar = KeyConfig.armor;
                    if (armorOn && mc.player != null) {
                        int x = Math.max(0, Math.min(Math.round(ar.x * sw), sw - ar.w()));
                        int y = Math.max(0, Math.min(Math.round(ar.y * sh), sh - ar.h()));
                        var pl = mc.player;
                        OverlayRenderer.drawArmor(g, x, y, ar.scale, new net.minecraft.world.item.ItemStack[]{
                                pl.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
                                pl.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                                pl.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS),
                                pl.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)});
                    }

                    KeyConfig.Overlay co = KeyConfig.coords;
                    if (hudOn && co.enabled && mc.player != null) {
                        int x = Math.max(0, Math.min(Math.round(co.x * sw), sw - co.w()));
                        int y = Math.max(0, Math.min(Math.round(co.y * sh), sh - co.h()));
                        OverlayRenderer.drawCoords(g, x, y, co.scale, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
                    }
                });
    }

    private static String statusLabel(Minecraft mc, Options o) {
        var p = mc.player;
        if (p.isShiftKeyDown()) return "CROUCHING";
        if (p.isSwimming()) return "SWIMMING";
        if (p.isFallFlying()) return "GLIDING";
        if (p.isSprinting()) return "SPRINTING";
        boolean moving = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown();
        return moving ? "WALKING" : "STANDING";
    }

    /** Only called on a new press, after Minecraft checks its own global shortcuts. */
    public static boolean handleMenuHotkey(Minecraft mc, InputConstants.Key key) {
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() != GLFW.GLFW_KEY_RIGHT_SHIFT
                || mc.gui.overlay() != null) return false;

        Screen screen = mc.gui.screen();
        // Sign editors accept text on the screen itself, not through a focused widget.
        if (screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen
                || screen instanceof BookEditScreen || screen instanceof BookSignScreen
                || (screen instanceof KeyBindsScreen binds && binds.selectedKey != null)) return false;

        var focus = screen == null ? null : screen.getCurrentFocusPath();
        var focused = focus == null ? null : focus.leafComponent();
        if (focused instanceof EditBox || focused instanceof MultiLineEditBox) return false;

        if (screen instanceof CritScreen crit) {
            if (crit.isCapturingKey()) return false;
            crit.onClose();
        } else {
            mc.gui.setScreen(new CritScreen(screen));
        }
        return true;
    }
}
