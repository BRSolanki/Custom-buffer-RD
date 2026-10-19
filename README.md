# CustomBuffer Shader for Minecraft Bedrock (MCPE)

A custom RenderDragon shader utilizing the **CustomBuffers** native mod (LeviLauncher) for Minecraft: Bedrock Edition. It leverages raw vanilla RenderChunk code combined with screen-space color buffer reflections and scene-depth shoreline foam.

## Features

- **Screen-Space Water Reflections (`s_ScreenBuffer`)**:
  - Dynamically samples the rendered scene buffer behind transparent geometry.
  - Generates realistic animated surface ripples, grazing-angle Fresnel reflection, and smooth edge falloff.
  - Multi-tap blur filter for soft reflections.
- **Shoreline Depth Foam (`s_SceneDepthBuffer`)**:
  - Computes water depth using the linear distance difference between the transparent fragment and the opaque scene depth buffer.
  - Generates animated procedural shoreline foam where water intersects blocks and shores.
- **Full Modern MCBE Vanilla Accuracy**:
  - Preserves exact 1.20+ vertex decoding (texture coordinates & lightmap bit unpacking).
  - Accurate foliage and biome mask tinting (`v_ditheringAndMaskTinting`).
  - Seasons leaf coloring and underwater fog adaptation (`MeshContext`).
  - Billboard rendering and instancing support.
- **Subpack Configuration**:
  - **Default**: Reflections + Shoreline Foam enabled.
  - **No Reflections**: Shoreline Foam only (performance boost).
  - **No Shoreline Foam**: Reflections only.

## Requirements

- Android device (`arm64-v8a`) running Android 9+
- [LeviLauncher](https://github.com/LiteLDev/LeviLaunchroid)
- **CustomBuffers** native mod (`CustomBuffers.levipack`) enabled in LeviLauncher
- Minecraft Bedrock 1.20 / 1.21+

## Repository Structure

```
├── .github/workflows/
│   ├── build.yml                 # GitHub Actions workflow to build .mcpack
│   └── build-check.yml           # PR build validation
├── assets/                       # Pack icon, biomes client config, assets
├── include/                      # BGFX shader and material headers
├── src/
│   ├── common/                   # Shared uniforms and engine material config
│   ├── newb/
│   │   └── pack_config.toml      # Pack metadata, materials list & subpacks
│   └── materials/
│       ├── project.json          # Target platforms (ESSL 3.00, etc.)
│       └── RenderChunk/
│           ├── buffers/
│           │   ├── ScreenBuffer.json      # Sampler registration (slot 3)
│           │   └── SceneDepthBuffer.json  # Sampler registration (slot 4)
│           ├── config.json       # Macro passes and overwrite mappings
│           ├── varying.def.sc    # Attribute and varying register definitions
│           ├── vertex.sc         # Vertex shader with MCBE raw decoding
│           └── fragment.sc       # Fragment shader with reflections & foam
├── tool/                         # Python build & pack utility (Lazurite wrapper)
├── build.bat                     # Windows build script
├── build.sh                      # Linux/macOS/CI build script
└── requirements.txt              # Build dependencies (lazurite, rich)
```

## Building via GitHub Actions

This repository is pre-configured to build automatically via GitHub Actions:

1. Push this repository to GitHub.
2. In your GitHub repository, navigate to the **Actions** tab.
3. Select the **build** workflow from the left sidebar.
4. Click **Run workflow**:
   - Choose target platform: **Android**, **Windows**, or **Multi-platform**.
   - Enter a sub-version number (e.g. `46` or `1`).
   - Click **Run workflow**.
5. Once complete, download the built `.mcpack` from the workflow artifacts.
