# TorchGlow (experimental) — Paper 26.3

Creates invisible Minecraft LIGHT blocks around **ordinary TORCH, WALL_TORCH, SOUL_TORCH and SOUL_WALL_TORCH** blocks when placed. Light is produced by multiple level-15 sources within a maximum radius of 30 blocks. It is not one torch emitting light 30 blocks via vanilla mechanics, nor does it light every spot in a 30-block sphere.

## Install
1. Back up your world first.
2. Compile using Java 25 and Gradle (`gradle build`) or via GitHub Actions workflow. Download the JAR from Actions artifacts.
3. Shut down server, put `TorchGlow-0.1.0.jar` into `plugins/` and restart Paper 26.3.
4. Test with a newly placed ordinary torch. Use `/torchglow status`.
5. For torches already placed within 12 blocks of you, `/torchglow scan`.
6. `/torchglow clear` removes owned lights in loaded chunks.

## Limitations
- **Experimental, not tested on a live Paper server.** Back up before enabling.
- The plugin places actual LIGHT blocks, which may interfere with building or other block plugins.
- Only currently loaded chunks are affected; it does not force chunks to load.
- LIGHT blocks in unloaded chunks can remain after removal or clear. Before removing the plugin, load relevant chunks and use `/torchglow clear`.
- Extra light is *approximate*: light placement candidates may be blocked by terrain, and visibility checks may not prevent every leak around corners.
- Large numbers of torches may affect tick performance and world save size. Start with a small test region.
- Player placed torches are detected automatically; existing torches require the scan command.
- Glass, water and other non-opaque materials are treated as transparent for line-of-sight checks.
- Plugin state is stored in `plugins/TorchGlow/lights.txt`; back this up alongside your world.
