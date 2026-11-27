# SAO UI (Minecraft 1.21.4 Fabric Mod)

A Sword Art Online inspired HUD, menu, and RPG leveling mod for Minecraft Java Edition (`1.21.4`, Fabric).

## Features

- **SAO Player HUD**: Slanted health bar with smooth damage tracking, level display, and SAO-style layout.
- **Mob Cursors & HP Bars**: World-projected floating cursors and health bars above mobs.
- **Multi-Segment Boss Bars**: Replaces the vanilla boss bar with SAO-style segmented health bars.
- **Independent SAO Level & Mob Scaling System**:
  - Custom RPG XP progression with gradual player stat bonuses (Max HP, Attack Damage, Armor, and Armor Toughness).
  - Dynamic mob scaling synchronized between server and client.
- **Polygon Shatter Death Effect**: SAO-style polygonal shatter animation when entities die.
- **SAO Cascading Menu**: Replaces the pause menu with an SAO-style cascading icon menu, player card, and logout confirmation dialog (with quick access back to the vanilla menu).
- **Localization**: Supports English (`en_us`) and Traditional Chinese (`zh_tw`).

## Requirements

### Playing
- **Minecraft**: `1.21.4`
- **Fabric Loader**: `>= 0.16.0`
- **Fabric API**: `0.119.4+1.21.4` (or compatible `1.21.4` release)
- **Java**: `21` or higher

### Building from Source
- **JDK 21** (e.g. Eclipse Temurin 21)
- **Gradle**: `8.14.3` (or run `gradle wrapper` locally to generate `gradlew`)

## Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/kazuya4931/SAO-UI-minecraft.git
   cd SAO-UI-minecraft
   ```

2. Build the mod JAR:
   ```bash
   gradle build
   ```
   *(If Gradle Wrapper is present, use `./gradlew build` on macOS/Linux or `.\gradlew.bat build` on Windows.)*

3. Find the compiled mod JAR in:
   ```text
   build/libs/sao-ui-1.21.4-2.jar
   ```
   Copy it (not the `-dev.jar`) into your Minecraft `mods/` folder.

4. To launch a test client directly while tweaking the code:
   ```bash
   gradle runClient
   ```

## Project Structure

- `gradle.properties` — Minecraft, Yarn mappings, Fabric Loader/API, and mod version settings
- `build.gradle` / `settings.gradle` — Gradle & Fabric Loom build configuration
- `src/main/java/com/saoui/`
  - `SaoUiMod.java` — Common entrypoint (networking, level stat syncing, mob scaling hooks)
  - `SaoUiClient.java` — Client entrypoint (HUD layer registration, camera projection, effects)
  - `hud/` — Player HUD (`PlayerHud`), mob bars (`MobBars`), boss bars (`BossHud`), and damage tracking (`HealthTracker`)
  - `render/` — Drawing primitives (`SaoDraw`, `SaoBar`, `ArcBar`, `Projection`)
  - `level/` — SAO level formulas, stat scaling, payloads, and dynamic mob scaling (`SaoLevelLogic`, `MobScaling`, `SaoEntities`)
  - `fx/ShatterEffect.java` — Polygon shatter death animation
  - `screen/SaoMenuScreen.java` — SAO cascading pause menu
  - `mixin/` — Mixins hooking into vanilla HUD, rendering, and entity lifecycle
- `src/main/resources/`
  - `fabric.mod.json` — Fabric mod metadata
  - `saoui.mixins.json` — Mixin configuration
  - `assets/saoui/lang/` — Language files (`en_us.json`, `zh_tw.json`)

## License

This project is licensed under the [MIT License](https://opensource.org/licenses/MIT).
