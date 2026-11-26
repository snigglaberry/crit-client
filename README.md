this shit is fully ai i dont know java or kotlin therefoureth this is straight slop


features we need:
crosshair indicator
kill effects
hit effects 
idk just add more shit

## Appearance

Press **Right Shift → Settings** to change the **Theme**, **Colorway**, and **Accent color** independently.

- **Smooth** (default): antialiased rounded corners and the Inter font.
- **Minecraft**: the flat, square Crit layout (formerly Basic) with Minecraft's default font.

The startup and resource-reload splash uses the standalone Crit logo, your colorway for the background, and your accent color and theme shape for the loading bar.

To move an overlay, open **Modules** and click the **Key Overlay**, **Overlays**, or **Armour HUD** card. The switch on each card toggles that overlay.

## Launcher skins

Crit Client loads shared offline skins from the instance's own config at startup, with no CustomSkinLoader. See [the launcher API and integration prompt](docs/crit-launcher-skins.md).

## Build and install on Windows

Close Minecraft, then run `.\gradlew.bat installDefault --console=plain` to build and install into Crit Launcher's default instance in one command. The installer backs up the previous JAR to the instance's `game/critclient-backups` folder. Add `--offline` if Gradle's dependencies are already cached.
