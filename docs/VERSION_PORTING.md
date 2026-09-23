# Version porting workflow

Use this checklist when adding a Minecraft version. Finish and verify one branch before starting the next.

## 1. Create the branch

- Branch names are the Minecraft version: `1.20.1`, `1.20.4`, `1.21.1`, `1.21.11`, `26.2`, `26.3`.
- Base each branch on the completed previous version branch. Do not inherit the MultiLoader Template Git history.
- Keep shared features in `common`; loader entry points and registration belong in `fabric`, `forge`, and `neoforge`.

Example:

```powershell
git switch 1.20.4
git switch -c 1.21.1
```

## 2. Update the toolchain

Update `gradle.properties`, the Gradle wrapper, and loader plugins together.

| Minecraft | Java | Loaders |
|---|---:|---|
| 1.20.1 | 17 | Fabric, Forge |
| 1.20.4 | 17 | Fabric, Forge, NeoForge |
| 1.21.1 | 21 | Fabric, Forge, NeoForge |
| 1.21.11 | 21 | Fabric, Forge, NeoForge |
| 26.2 | 25 | Fabric, NeoForge |
| 26.3 | 25 | Fabric, NeoForge |

Confirm all of these values:

- Minecraft version and supported version range
- Fabric Loader and Fabric API
- Forge and NeoForge versions and loader ranges
- Java version and Gradle version
- Mod version, group, ID, author, MIT license, project IDs, and client environment metadata

## 3. Port and compile

1. Compile `common` and fix Minecraft API and Mixin target changes.
2. Port Fabric registration and compile it.
3. Port Forge registration and compile it.
4. Port NeoForge registration and compile it when that loader is supported.
5. Keep F8, Shift+F8, and F9 registered through each loader's key mapping API.
6. Keep the extra-window viewport, dynamic FOV, entity lighting, fluid rendering, hand rendering, pause behavior, and window geometry persistence working together.

Run the normal build from a fresh checkout. NeoGradle 1.20.4 may fail when `clean` and `build` are requested in the same invocation, so use `gradlew build` in CI and run `gradlew clean` separately only when needed.

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat publishMods --dry-run --console=plain
git diff --check
```

## 4. Test the version

- Run the common JUnit tests for selector normalization, defaults, bounds, copying, and config persistence.
- Use a separate run directory for every loader. Never reuse a Fabric world for Forge or NeoForge.
- Launch every supported loader and capture the normal view and X-ray view.
- Confirm textured selected blocks, surrounding outlines, fluids, full-bright entities, the hand, FOV changes, and different window sizes.
- Confirm leaving a world closes every extra window and pausing the game pauses the X-ray view.
- Check `latest.log` for Mixin application, injection, and transformer failures.
- Test the built release JAR in the matching Prism Launcher instance using `C:\Users\PC_User\.claude\skills\mod-version-test\SKILL.md`. Back up and restore the instance's original mods.
- Store evidence in `docs/verification/<minecraft-version>/<loader>/main.png` and `xray.png`.

## 5. Prepare release files

- Inspect the exact non-sources, non-javadoc JAR selected by each publishing task.
- Confirm the file name contains the correct loader, Minecraft version, and mod version.
- Confirm packaged `fabric.mod.json` or `META-INF/mods.toml`, the Mixin config, icon, and language files.
- Keep full-size showcase images in `docs/media` and separate listing images under 850 px wide in `docs/media/listing`.
- Update `CHANGELOG.md`, README support table, CurseForge description, and Modrinth description when features or compatibility change.

## 6. Commit and publish

1. Commit only after all supported loaders pass build and runtime tests.
2. Verify the commit's parent is the previous completed version branch.
3. Run the publishing task once. It uploads every supported loader to both services.
4. Verify every remote version has the correct loader, game version, file, and environment.

```powershell
.\gradlew.bat publishMods --console=plain
```

Required environment variables:

- `MODRINTH_TOKEN`
- `CURSEFORGE_TOKEN`

Modrinth must report `client_side=required` and `server_side=unsupported`. CurseForge must include the `Client` environment. Fabric uploads must declare Fabric API as required.

Use tags in the form `<minecraft-version>-v<mod-version>`, for example `1.20.4-v1.0.1`, when the GitHub release workflow should run.
