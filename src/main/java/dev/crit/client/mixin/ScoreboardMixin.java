package dev.crit.client.mixin;

import dev.crit.client.CritClient;
import dev.crit.client.modules.Modules;
import dev.crit.client.modules.ScoreboardModule;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Scoreboard Tweaks: the line limit and the background darkness. The game builds the sidebar from a sorted list that it
 * cuts to 15 entries; the first hook changes that 15. The second scales the alpha of the two background boxes.
 * Both are optional (require = 0) so a game version that lays the sidebar out differently never stops the client from
 * starting: the settings just do nothing there. Each hook logs once when it first runs, so the log shows whether it took.
 */
@Mixin(Hud.class)
public abstract class ScoreboardMixin {
    private static boolean critclient$linesLogged, critclient$bgLogged;

    @ModifyArg(method = {"displayScoreboardSidebar", "extractScoreboardSidebar", "extractSidebar", "renderScoreboardSidebar"},
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;limit(J)Ljava/util/stream/Stream;"),
            index = 0, require = 0)
    private long critclient$lines(long original) {
        if (!critclient$linesLogged) {
            critclient$linesLogged = true;
            CritClient.LOG.info("Scoreboard Tweaks: line limit hook is active (game limit {})", original);
        }
        ScoreboardModule m = Modules.SCOREBOARD;
        return m.enabled() ? m.maxLines() : original;
    }

    @ModifyArg(method = {"displayScoreboardSidebar", "extractScoreboardSidebar", "extractSidebar", "renderScoreboardSidebar"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I"),
            index = 0, require = 0)
    private float critclient$background(float original) {
        if (!critclient$bgLogged) {
            critclient$bgLogged = true;
            CritClient.LOG.info("Scoreboard Tweaks: background hook is active");
        }
        ScoreboardModule m = Modules.SCOREBOARD;
        return m.enabled() ? (float) Math.max(0.0, Math.min(1.0, original * m.background())) : original;
    }
}
