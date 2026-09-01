# Lodeframe Shaders

Shader-pack compatibility for [Lodeframe](https://github.com/pentaoa/lodeframe), the native Metal renderer for Minecraft: Java Edition on Apple Silicon.

This repository contains the shader-pack parser and legacy GLSL compatibility layer used to run established OptiFine/Iris-format packs through Lodeframe's Metal backend. It implements the pack interface independently rather than embedding the Iris renderer.

## What works today

The frontend inspects an OptiFine/Iris-format shader pack directly from a ZIP file or directory and provides:

- dimension and program discovery;
- vertex, fragment, geometry, compute, and tessellation stage discovery;
- recursive absolute and relative `#include` resolution;
- include-cycle and missing-file diagnostics;
- GLSL version and `DRAWBUFFERS` detection;
- `shaders.properties` requirements for custom uniforms, images, textures, and conditional programs.
- legacy GLSL 120/130 normalization for Metal's SPIR-V frontend;
- Minecraft and Sodium vertex-interface adaptation;
- Iris render-stage constants, uniform layouts, sampler discovery, and `DRAWBUFFERS` routing.

The Lodeframe runtime now consumes this output for terrain, water, sky, entities, hand rendering, particles, weather, directional shadows, composite passes, and final output. Support is still pre-alpha and rendered-output compatibility varies by pack.

## Inspect a pack

Java 25 is required.

```shell
./gradlew build
java -jar build/libs/lodeframe-shaders-0.1.0-alpha.1.jar inspect /path/to/shaderpack.zip
```

Local shader packs are test inputs only. They are excluded from version control and are never bundled or redistributed by this project.

## Real-pack baseline

The frontend is exercised locally against BSL 10.1.3. It resolves all 179 shader-stage entries and 2,263 include edges across 91 programs without diagnostics, and every program currently selected by the runtime passes an offline GLSL-to-SPIR-V frontend check. BSL remains the first rendered-output conformance target.

## Direction

Lodeframe Shaders implements compatibility with the established pack format independently of Iris. Packs remain the shared interface; the runtime and Metal renderer are ours.

The next milestones are:

1. complete BSL rendered-output conformance and visual regression coverage;
2. extend block-entity, cloud, and less common render-stage coverage;
3. add remaining pack features such as compute/image stages where Metal equivalents are available;
4. broaden the real-pack compatibility suite without weakening Metal correctness or performance.

## Project family

- [Lodeframe](https://github.com/pentaoa/lodeframe) — native Metal renderer
- **Lodeframe Shaders** — shader-pack compatibility layer
- Lodeframe Lab — conformance and performance tooling (planned)

Minecraft is a trademark of Microsoft. Lodeframe Shaders is not affiliated with Mojang Studios, Microsoft, Apple, Iris, or OptiFine.
