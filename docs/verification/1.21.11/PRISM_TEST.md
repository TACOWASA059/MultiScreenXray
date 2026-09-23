# Prism Launcher packaged JAR test

Tested on 2026-09-24 using the release JARs produced by `gradlew clean build`.

| Loader | Runtime | Result |
| --- | --- | --- |
| Fabric | Fabric Loader 0.19.3, Fabric API 0.141.4 | Joined a singleplayer world and opened an extra X-ray window; no mixin or render exception |
| Forge | Forge 61.1.0 | Joined a singleplayer world and opened two extra X-ray windows; no mixin or render exception |
| NeoForge | NeoForge 21.11.42 | Joined a singleplayer world and opened an extra X-ray window; no mixin or render exception |

The Forge run specifically verifies the `DebugRenderer` gizmo path that previously raised `Gizmos cannot be created here` in the extra render pass.
