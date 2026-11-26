package dev.crit.client.mixin;

import dev.crit.client.modules.Modules;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Always Sprint: after the keyboard has been sampled, pretend the sprint key is held. The game's own
 * checks (food, using an item, swimming) still decide whether you actually sprint, so this only ever
 * sprints "if possible".
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void critclient$alwaysSprint(CallbackInfo ci) {
        if (!Modules.SPRINT.enabled()) return;
        ClientInput self = (ClientInput) (Object) this;
        Input keys = self.keyPresses;
        if (keys != null && !keys.sprint()) {
            self.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(),
                    keys.jump(), keys.shift(), true);
        }
    }
}
