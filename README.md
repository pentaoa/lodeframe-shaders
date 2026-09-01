# Lodeframe Shaders

Shader-pack compatibility for [Lodeframe](https://github.com/pentaoa/lodeframe), the native Metal renderer for Minecraft: Java Edition on Apple Silicon.

This repository will contain the shader-pack parser, shader translation pipeline, and world-rendering integration needed to run established OptiFine/Iris-format packs on Metal. It is a compatibility implementation for the pack format, not a Metal backend for the Iris mod itself.

## What works today

The first executable frontend is available. It inspects an OptiFine/Iris-format shader pack directly from a ZIP file or directory and reports the work needed before Metal translation:

- dimension and program discovery;
- vertex, fragment, geometry, compute, and tessellation stage discovery;
- recursive absolute and relative `#include` resolution;
- include-cycle and missing-file diagnostics;
- GLSL version and `DRAWBUFFERS` detection;
- `shaders.properties` requirements for custom uniforms, images, textures, and conditional programs.

It does **not** render shader packs yet. This is the input boundary for the GLSL preprocessing, SPIR-V/MSL translation, and Metal pass scheduler that follow.

## Inspect a pack

Java 25 is required.

```shell
./gradlew build
java -jar build/libs/lodeframe-shaders-0.1.0-alpha.1.jar inspect /path/to/shaderpack.zip
```

Local shader packs are test inputs only. They are excluded from version control and are never bundled or redistributed by this project.

## Real-pack baseline

The frontend is exercised locally against BSL 10.1.3. The current baseline resolves all 179 shader-stage entries and 2,263 include edges across 91 programs without diagnostics. That pack establishes the first implementation targets: legacy GLSL 120/130 semantics, `DRAWBUFFERS` routing, compute stages, custom uniforms/images/textures, and conditional pass scheduling.

## Direction

Lodeframe Shaders implements compatibility with the established pack format independently of Iris. Packs remain the shared interface; the runtime and Metal renderer are ours.

The next milestones are:

1. preprocess OptiFine/Iris macros and legacy GLSL interfaces into explicit stage inputs and outputs;
2. compile the normalized GLSL through SPIR-V into Metal-compatible shaders;
3. turn pack programs and properties into a Metal render-pass graph;
4. integrate the resulting pipeline with Lodeframe and validate rendered output.

## Project family

- [Lodeframe](https://github.com/pentaoa/lodeframe) — native Metal renderer
- **Lodeframe Shaders** — shader-pack compatibility layer
- Lodeframe Lab — conformance and performance tooling (planned)

Minecraft is a trademark of Microsoft. Lodeframe Shaders is not affiliated with Mojang Studios, Microsoft, Apple, Iris, or OptiFine.
