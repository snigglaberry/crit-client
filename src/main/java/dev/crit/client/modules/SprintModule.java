package dev.crit.client.modules;


/** Holds the sprint key down for you, so moving forward always sprints when you're able to. */
public final class SprintModule extends Module {
    public SprintModule() {
        super("sprint", "Always Sprint", "Keeps you sprinting while you move, whenever the game would allow it.", false);
    }

}
