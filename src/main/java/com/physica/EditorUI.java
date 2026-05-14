package com.physica;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImString;

import java.util.List;
import java.util.Map;

public class EditorUI {
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3  imGuiGl3  = new ImGuiImplGl3();

    // ── dimensions ───────────────────────────────────────────────────────────
    private static final int LEFT_W     = 340;
    private static final int RIGHT_W    = 280;
    private static final int CONSOLE_H  = 200;
    private static final int TOPBAR_H   = 32;

    // ── colours ──────────────────────────────────────────────────────────────
    // Pastel green family
    private static final float[] CLR_ACCENT  = {0.70f, 0.95f, 0.73f, 1.00f};  // #B2F2BB
    private static final float[] CLR_DIM     = {0.30f, 0.60f, 0.35f, 1.00f};
    private static final float[] CLR_RED     = {0.90f, 0.35f, 0.35f, 1.00f};
    private static final float[] CLR_YELLOW  = {0.90f, 0.85f, 0.35f, 1.00f};
    private static final float[] CLR_SUBTLE  = {0.40f, 0.40f, 0.40f, 1.00f};

    // ── state ─────────────────────────────────────────────────────────────────
    private ImString scriptInput;
    private HeliumParser parser;
    private PhysicaConsole console;
    private TimelineController timeline;

    // Timeline scrubber (seconds)
    private final float[] scrubTime = {0f};
    private float maxCachedTime = 0f;

    // ── init ──────────────────────────────────────────────────────────────────
    public void init(long window, HeliumParser parser, PhysicaConsole console,
                     TimelineController timeline, String initialScript) {
        this.parser   = parser;
        this.console  = console;
        this.timeline = timeline;
        this.scriptInput = new ImString(initialScript, 16_000);

        ImGui.createContext();

        applyTheme();

        imGuiGlfw.init(window, true);
        imGuiGl3.init("#version 410 core");
    }

    // ── render ────────────────────────────────────────────────────────────────
    public void render(int fps, float simTime, int w, int h) {
        imGuiGlfw.newFrame();
        ImGui.newFrame();

        if (simTime > maxCachedTime) maxCachedTime = simTime;

        renderTopBar(fps, simTime, w);
        renderCodeEditor(w, h);
        renderEntityInspector(w, h);
        renderConsole(w, h);

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOP BAR  ·  FPS · physics status · sim time · camera controls hint
    // ─────────────────────────────────────────────────────────────────────────
    private void renderTopBar(int fps, float simTime, int w) {
        ImGui.setNextWindowPos(0, 0);
        ImGui.setNextWindowSize(w, TOPBAR_H);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.06f, 0.06f, 0.06f, 1f);
        ImGui.begin("##topbar",
                ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoMove     | ImGuiWindowFlags.NoResize   |
                ImGuiWindowFlags.NoScrollbar);

        // Logo / engine name
        ImGui.pushStyleColor(ImGuiCol.Text, CLR_ACCENT[0], CLR_ACCENT[1], CLR_ACCENT[2], 1f);
        ImGui.text("PHYSICA  v0.1.0");
        ImGui.popStyleColor();

        ImGui.sameLine(0, 40);

        // Physics status badge
        boolean phys = parser.isPhysicsEnabled();
        float[] badge = phys ? CLR_ACCENT : CLR_RED;
        ImGui.pushStyleColor(ImGuiCol.Text, badge[0], badge[1], badge[2], 1f);
        ImGui.text(phys ? "● PHYSICS: RUNNING" : "● PHYSICS: STOPPED");
        ImGui.popStyleColor();

        ImGui.sameLine(0, 40);
        ImGui.pushStyleColor(ImGuiCol.Text, CLR_SUBTLE[0], CLR_SUBTLE[1], CLR_SUBTLE[2], 1f);
        ImGui.text(String.format("FPS: %d    T: %.3fs    ENTITIES: %d",
                fps, simTime, parser.getEntities().size()));
        ImGui.popStyleColor();

        ImGui.sameLine(0, 40);
        ImGui.pushStyleColor(ImGuiCol.Text, CLR_DIM[0], CLR_DIM[1], CLR_DIM[2], 1f);
        ImGui.text("LMB: orbit    RMB/MMB: pan    Scroll: zoom");
        ImGui.popStyleColor();

        ImGui.end();
        ImGui.popStyleColor(); // WindowBg
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEFT PANEL  ·  Helium script editor + timeline scrubber
    // ─────────────────────────────────────────────────────────────────────────
    private void renderCodeEditor(int w, int h) {
        int editorH = h - CONSOLE_H - TOPBAR_H;
        ImGui.setNextWindowPos(0, TOPBAR_H);
        ImGui.setNextWindowSize(LEFT_W, editorH);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.04f, 0.04f, 0.04f, 0.97f);
        ImGui.begin("script.phy",
                ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize);

        // ── Toolbar row ──────────────────────────────────────────────────────
        ImGui.pushStyleColor(ImGuiCol.Button,        0.10f, 0.30f, 0.12f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.18f, 0.50f, 0.20f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,  0.28f, 0.70f, 0.30f, 1f);

        if (ImGui.button("▶  RUN", 80, 22)) {
            parser.parseScript(scriptInput.get());
            maxCachedTime = 0f;
            scrubTime[0] = 0f;
        }

        ImGui.sameLine(0, 6);

        // Pause live sim toggle
        boolean frozen = timeline.hasForcedRenderTime();
        if (frozen) {
            ImGui.pushStyleColor(ImGuiCol.Button,        0.30f, 0.10f, 0.10f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.50f, 0.15f, 0.15f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive,  0.70f, 0.20f, 0.20f, 1f);
            if (ImGui.button("⏵  LIVE", 72, 22)) {
                parser.clearForcedRenderTime();
                scrubTime[0] = 0f;
            }
            ImGui.popStyleColor(3);
        } else {
            if (ImGui.button("⏸  FREEZE", 80, 22)) {
                parser.setForcedRenderTime(scrubTime[0]);
            }
        }
        ImGui.popStyleColor(3);

        ImGui.sameLine(0, 10);
        ImGui.pushStyleColor(ImGuiCol.Text, CLR_DIM[0], CLR_DIM[1], CLR_DIM[2], 1f);
        ImGui.text(".phy  |  Helium 1.0");
        ImGui.popStyleColor();

        ImGui.separator();

        // ── Code editor ──────────────────────────────────────────────────────
        int codeAreaH = editorH - 130; // leave room for timeline
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.02f, 0.02f, 0.02f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Text,    CLR_ACCENT[0], CLR_ACCENT[1], CLR_ACCENT[2], 1f);
        ImGui.inputTextMultiline("##src", scriptInput, LEFT_W - 16, codeAreaH,
                ImGuiInputTextFlags.AllowTabInput);
        ImGui.popStyleColor(2);

        ImGui.separator();

        // ── Timeline scrubber ─────────────────────────────────────────────────
        ImGui.pushStyleColor(ImGuiCol.Text, CLR_SUBTLE[0], CLR_SUBTLE[1], CLR_SUBTLE[2], 1f);
        ImGui.text("TIMELINE");
        ImGui.popStyleColor();

        float maxT = Math.max(maxCachedTime, 10f);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab,        CLR_ACCENT[0], CLR_ACCENT[1], CLR_ACCENT[2], 1f);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive,  CLR_DIM[0],    CLR_DIM[1],    CLR_DIM[2], 1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg,           0.08f, 0.08f, 0.08f, 1f);
        ImGui.setNextItemWidth(LEFT_W - 16);
        if (ImGui.sliderFloat("##scrub", scrubTime, 0f, maxT, "%.2f s")) {
            parser.setForcedRenderTime(scrubTime[0]);
        }
        ImGui.popStyleColor(3);

        ImGui.pushStyleColor(ImGuiCol.Text, CLR_DIM[0], CLR_DIM[1], CLR_DIM[2], 1f);
        ImGui.text(String.format("Scrub: %.3fs  /  Cached: %.3fs", scrubTime[0], maxCachedTime));
        ImGui.popStyleColor();

        ImGui.end();
        ImGui.popStyleColor(); // WindowBg
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RIGHT PANEL  ·  Entity inspector
    // ─────────────────────────────────────────────────────────────────────────
    private void renderEntityInspector(int w, int h) {
        int inspH = h - CONSOLE_H - TOPBAR_H;
        ImGui.setNextWindowPos(w - RIGHT_W, TOPBAR_H);
        ImGui.setNextWindowSize(RIGHT_W, inspH);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.04f, 0.04f, 0.04f, 0.92f);
        ImGui.begin("Inspector",
                ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize);

        ImGui.pushStyleColor(ImGuiCol.Text, CLR_SUBTLE[0], CLR_SUBTLE[1], CLR_SUBTLE[2], 1f);
        ImGui.text("ENTITIES");
        ImGui.popStyleColor();
        ImGui.separator();

        Map<Integer, SceneEntity> entities = parser.getEntities();
        if (entities.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, CLR_SUBTLE[0], CLR_SUBTLE[1], CLR_SUBTLE[2], 1f);
            ImGui.text("  No entities spawned.");
            ImGui.popStyleColor();
        }

        for (SceneEntity e : entities.values()) {
            String header = String.format("[%d] %s", e.id, e.type.toUpperCase());
            ImGui.pushStyleColor(ImGuiCol.Text, CLR_ACCENT[0], CLR_ACCENT[1], CLR_ACCENT[2], 1f);
            boolean open = ImGui.treeNode(header + "##" + e.id);
            ImGui.popStyleColor();

            if (open) {
                ImGui.pushStyleColor(ImGuiCol.Text, CLR_DIM[0], CLR_DIM[1], CLR_DIM[2], 1f);

                ImGui.text(String.format("  Pos   %.2f  %.2f  %.2f",
                        e.visualPosition.x, e.visualPosition.y, e.visualPosition.z));
                ImGui.text(String.format("  Scale %.2f  %.2f  %.2f",
                        e.scale.x, e.scale.y, e.scale.z));
                ImGui.text(String.format("  Vel   %.2f  %.2f  %.2f",
                        e.velocity.x, e.velocity.y, e.velocity.z));
                ImGui.text("  Static: " + (e.isStatic ? "yes" : "no"));
                ImGui.text("  Mass:   " + e.mass);

                ImGui.popStyleColor();
                ImGui.treePop();
            }
        }

        ImGui.separator();

        // Sim stats block
        ImGui.pushStyleColor(ImGuiCol.Text, CLR_SUBTLE[0], CLR_SUBTLE[1], CLR_SUBTLE[2], 1f);
        ImGui.text("SIMULATION");
        ImGui.popStyleColor();
        ImGui.separator();

        boolean phys = parser.isPhysicsEnabled();
        float[] col = phys ? CLR_ACCENT : CLR_RED;
        ImGui.pushStyleColor(ImGuiCol.Text, col[0], col[1], col[2], 1f);
        ImGui.text("  Physics:   " + (phys ? "RUNNING" : "STOPPED"));
        ImGui.popStyleColor();

        ImGui.pushStyleColor(ImGuiCol.Text, CLR_DIM[0], CLR_DIM[1], CLR_DIM[2], 1f);
        ImGui.text("  Interp:    " + (parser.isInterpolationLinear() ? "LINEAR" : "NONE"));
        float rTime = parser.getForcedRenderTime();
        ImGui.text("  RenderT:   " + (rTime >= 0 ? String.format("%.2fs", rTime) : "LIVE"));
        ImGui.popStyleColor();

        ImGui.end();
        ImGui.popStyleColor(); // WindowBg
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BOTTOM PANEL  ·  Console log
    // ─────────────────────────────────────────────────────────────────────────
    private void renderConsole(int w, int h) {
        ImGui.setNextWindowPos(0, h - CONSOLE_H);
        ImGui.setNextWindowSize(w, CONSOLE_H);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.02f, 0.02f, 0.02f, 0.98f);
        ImGui.begin("Console",
                ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize);

        List<String> logs = console.getLogs();
        for (String line : logs) {
            // colour coding: errors red, warnings yellow, rest accent
            float[] c = CLR_DIM;
            String lower = line.toLowerCase();
            if (lower.contains("error") || lower.contains("fail") || lower.contains("exception")) {
                c = CLR_RED;
            } else if (lower.contains("warn") || lower.contains("missing")) {
                c = CLR_YELLOW;
            } else if (lower.contains("spawn") || lower.contains("physics") || lower.contains("interp")) {
                c = CLR_ACCENT;
            }
            ImGui.pushStyleColor(ImGuiCol.Text, c[0], c[1], c[2], 1f);
            ImGui.text(line);
            ImGui.popStyleColor();
        }

        // Auto-scroll
        if (ImGui.getScrollY() >= ImGui.getScrollMaxY() - 10) {
            ImGui.setScrollHereY(1.0f);
        }

        ImGui.end();
        ImGui.popStyleColor(); // WindowBg
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Theme
    // ─────────────────────────────────────────────────────────────────────────
    private void applyTheme() {
        imgui.ImGuiStyle s = ImGui.getStyle();

        s.setColor(ImGuiCol.WindowBg,          0.04f, 0.04f, 0.04f, 0.97f);
        s.setColor(ImGuiCol.ChildBg,           0.03f, 0.03f, 0.03f, 1.00f);
        s.setColor(ImGuiCol.PopupBg,           0.06f, 0.06f, 0.06f, 1.00f);
        s.setColor(ImGuiCol.Border,            0.12f, 0.35f, 0.14f, 0.60f);
        s.setColor(ImGuiCol.FrameBg,           0.08f, 0.08f, 0.08f, 1.00f);
        s.setColor(ImGuiCol.FrameBgHovered,    0.12f, 0.12f, 0.12f, 1.00f);
        s.setColor(ImGuiCol.TitleBg,           0.04f, 0.04f, 0.04f, 1.00f);
        s.setColor(ImGuiCol.TitleBgActive,     0.06f, 0.18f, 0.07f, 1.00f);
        s.setColor(ImGuiCol.MenuBarBg,         0.04f, 0.04f, 0.04f, 1.00f);
        s.setColor(ImGuiCol.ScrollbarBg,       0.02f, 0.02f, 0.02f, 1.00f);
        s.setColor(ImGuiCol.ScrollbarGrab,     0.15f, 0.40f, 0.17f, 1.00f);
        s.setColor(ImGuiCol.ScrollbarGrabHovered, 0.25f, 0.60f, 0.27f, 1.00f);
        s.setColor(ImGuiCol.Separator,         0.12f, 0.35f, 0.14f, 0.80f);
        s.setColor(ImGuiCol.Header,            0.10f, 0.28f, 0.12f, 1.00f);
        s.setColor(ImGuiCol.HeaderHovered,     0.17f, 0.45f, 0.19f, 1.00f);
        s.setColor(ImGuiCol.HeaderActive,      0.24f, 0.62f, 0.27f, 1.00f);
        s.setColor(ImGuiCol.Button,            0.10f, 0.28f, 0.12f, 1.00f);
        s.setColor(ImGuiCol.ButtonHovered,     0.18f, 0.50f, 0.20f, 1.00f);
        s.setColor(ImGuiCol.ButtonActive,      0.28f, 0.70f, 0.30f, 1.00f);
        s.setColor(ImGuiCol.SliderGrab,        0.70f, 0.95f, 0.73f, 1.00f);
        s.setColor(ImGuiCol.SliderGrabActive,  0.40f, 0.70f, 0.44f, 1.00f);
        s.setColor(ImGuiCol.Text,              0.70f, 0.95f, 0.73f, 1.00f);
        s.setColor(ImGuiCol.TextDisabled,      0.30f, 0.50f, 0.32f, 1.00f);

        s.setWindowRounding(2f);
        s.setFrameRounding(2f);
        s.setScrollbarRounding(2f);
        s.setItemSpacing(8, 4);
        s.setFramePadding(5, 3);
    }

    public void cleanup() {
        imGuiGl3.dispose();
        imGuiGlfw.dispose();
        ImGui.destroyContext();
    }
}
