<div align="center">

# ⬡ PHYSICA

**A 3D code-driven physics sandbox engine — built with Java 25, LWJGL, and JOML.**

![Java](https://img.shields.io/badge/Java-25-B2F2BB?style=flat-square&logo=openjdk&logoColor=black)
![LWJGL](https://img.shields.io/badge/LWJGL-3.3.3-B2F2BB?style=flat-square)
![JOML](https://img.shields.io/badge/JOML-1.10.5-B2F2BB?style=flat-square)
![ImGui](https://img.shields.io/badge/ImGui--Java-1.86.11-B2F2BB?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-B2F2BB?style=flat-square)
![Status](https://img.shields.io/badge/status-v0.1.0%20alpha-B2F2BB?style=flat-square)

*Spawn entities. Write physics scripts. Scrub through time.*

</div>

---

## What is Physica?

Physica is a minimal, code-first 3D physics sandbox. Instead of clicking through a GUI to place objects, you write short **Helium scripts** (`.phy` files) directly in the editor panel. The engine parses your script, spawns entities into the 3D scene, runs physics, and caches the simulation so you can scrub backwards and forwards through time.

Think: *a blend of a physics playground and a timeline-based animation tool, but driven entirely by code.*

---

## Features

### 🟢 Phase 1 — Core Rendering
- GLFW window with OpenGL 4.1 Core Profile
- Deep charcoal background (`#0A0A0A`) with pastel green aesthetic (`#B2F2BB`)
- 3D floor grid rendered with dim green line shaders
- Orbit camera (drag, pan, scroll-to-zoom)
- Flat-shaded geometry via GLSL `dFdx/dFdy` derivatives — no manual normals

### 🟢 Phase 2 — Helium Scripting Engine
- Custom lexer/parser for the `.phy` script format
- Live script editor inside the engine window — edit & hit **▶ RUN** to recompile
- `PhysicaConsole` with colour-coded log output (green, red, yellow) and auto-scroll

### 🟢 Phase 3 — Physics Solver
- Fixed-timestep physics loop (60 Hz) decoupled from the render loop
- Gravity (`-9.81 m/s²`) applied per entity
- **Octree** spatial partitioning to limit collision checks
- **SAT-Lite** Cube-Cube collision via `Intersectionf.testAabAab`
- Distance-based **Sphere-Sphere** collision with impulse resolution
- **Sphere-Cube** collision with penetration correction and bounce

### 🟢 Phase 4 — Timeline & Interpolation
- `PhysicsSnapshot` Java 25 `record` — immutable state per entity per tick
- `SimulationCache` stores all snapshots across the full simulation run
- `TimelineController` with **LERP** (position) and **SLERP** (rotation) between ticks
- **Timeline scrubber** — drag to any point in time; the renderer shows the interpolated state
- **FREEZE / LIVE** toggle — freeze the scene at the scrubbed time or return to real-time

### 🟢 Phase 5 — ImGui Editor UI
- Left panel: live Helium code editor with ▶ RUN / ⏸ FREEZE / ⏵ LIVE controls
- Right panel: entity inspector tree (position, velocity, scale, mass, static flag)
- Bottom panel: colour-coded console with auto-scroll
- Top bar: physics status badge, FPS, entity count, sim time, camera hints
- Full viewport-bounds check — ImGui panels never steal camera input

---

## Helium Script Syntax

```phy
phy: enable;

spawn(sphere): id[1], rad{2.0}, pos{0, 10, 0};
spawn(cube):   id[2], size{8, 1, 8}, pos{0, 0.5, 0};

anim_interpolation: linear;
anim_render: 1.5;

phy_sim: loop;
```

| Command | Description |
|---------|-------------|
| `phy: enable;` | Enable physics (gravity + collisions) |
| `spawn(cube): id[N], size{x,y,z}, pos{x,y,z};` | Spawn a cube entity |
| `spawn(sphere): id[N], rad{r}, pos{x,y,z};` | Spawn a sphere entity |
| `anim_interpolation: linear;` | Enable LERP/SLERP between cached frames |
| `anim_render: T;` | Freeze viewport at time `T` seconds |
| `phy_sim: loop;` | Start the simulation loop |

---

## Getting Started

### Prerequisites
- **JDK 21+** (tested with Eclipse Temurin 21)
- macOS (Apple Silicon or Intel — native LWJGL libs included)
- No global Gradle required — the Gradle Wrapper is bundled

### Run
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./gradlew run
```

### Controls

| Input | Action |
|-------|--------|
| Left Mouse Drag | Orbit camera |
| Right / Middle Mouse Drag | Pan camera |
| Scroll Wheel | Zoom in / out |
| **▶ RUN** button | Re-parse and run the script |
| **⏸ FREEZE** button | Lock viewport to current scrub time |
| **⏵ LIVE** button | Return to real-time simulation |
| Timeline Slider | Scrub to any cached physics time |

---

## Architecture

```
Engine.java              — GLFW window, render loop, fixed-timestep integrator
├── HeliumParser.java    — Lexer + parser for .phy scripts
├── PhysicsSolver.java   — Gravity, impulse resolution, SAT-Lite collisions
│   └── Octree.java      — Spatial partitioning (AABB buckets, depth 5)
├── SimulationCache.java — Timestamped PhysicsSnapshot storage
├── TimelineController   — LERP/SLERP interpolation for visual state
├── EditorUI.java        — ImGui panels (editor, inspector, console, scrubber)
├── Camera.java          — Orbit + pan + zoom view matrix
├── Shader.java          — GLSL loader and uniform setter
├── PhysicaEntity.java   — GPU mesh builder (cube, sphere, grid VAO/VBO)
└── SceneEntity.java     — Runtime entity state (physics + visual vectors)
```

---

## Roadmap

- [ ] Phase 6 — Multi-mesh rendering + `.obj` import
- [ ] Phase 7 — Helium v2 (conditionals, variables, loops)
- [ ] Phase 8 — Export simulation to JSON keyframes
- [ ] Phase 9 — Constraints & joints (hinges, springs)
- [ ] Phase 10 — Linux / Windows native builds

---

## License

MIT © [alanthecoderishere](https://github.com/alanthecoderishere)
