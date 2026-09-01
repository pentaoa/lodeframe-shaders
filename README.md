# Lodeframe Shaders

Shader-pack compatibility for [Lodeframe](https://github.com/pentaoa/lodeframe), the native Metal renderer for Minecraft: Java Edition on Apple Silicon.

This repository will contain the shader-pack parser, shader translation pipeline, and world-rendering integration needed to run established OptiFine/Iris-format packs on Metal. It is a compatibility implementation for the pack format, not a Metal backend for the Iris mod itself.

## Status

Planning and architecture work only. There is no usable build yet.

Core renderer correctness comes first: render attachments, depth-only passes, resource lifecycle, synchronization, and reproducible benchmarks must be stable before the shader pipeline is integrated.

## Project family

- [Lodeframe](https://github.com/pentaoa/lodeframe) — native Metal renderer
- **Lodeframe Shaders** — shader-pack compatibility layer
- Lodeframe Lab — conformance and performance tooling (planned)

Minecraft is a trademark of Microsoft. Lodeframe Shaders is not affiliated with Mojang Studios, Microsoft, Apple, Iris, or OptiFine.
