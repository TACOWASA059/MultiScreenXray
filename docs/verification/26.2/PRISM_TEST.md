# Minecraft 26.2 PrismLauncher verification

Tested the packaged `1.0.2` JARs in real PrismLauncher instances on Windows with an NVIDIA RTX 3070.

| Loader | Backend | Result | Evidence |
|---|---|---|---|
| Fabric | OpenGL | Passed: joined a world and opened multiple X-ray windows | [Screenshot](fabric/opengl.png) |
| Fabric | Vulkan | Passed: joined a world, opened multiple X-ray windows, and used the F8 key binding | [Screenshot](fabric/vulkan.png) |
| NeoForge | OpenGL | Passed: joined a world and opened two X-ray windows | [Screenshot](neoforge/opengl.png) |
| NeoForge | Vulkan | Passed with `earlyWindowControl = true`: joined a world and opened two X-ray windows | [Screenshot](neoforge/vulkan.png) |

The clean build completed successfully with `gradlew clean build`. The tested NeoForge version was `26.2.0.88`; Fabric used Fabric API `0.152.1+26.2`.
