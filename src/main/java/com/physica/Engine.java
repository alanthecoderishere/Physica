package com.physica;

import org.joml.Matrix4f;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import imgui.ImGui;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import java.util.ArrayList;

public class Engine {
    private long window;
    private int width = 1280;
    private int height = 720;
    private int windowWidth = 1280;
    private int windowHeight = 720;
    
    private Shader solidShader;
    private Shader gridShader;
    private Camera camera;
    
    // Mouse tracking for viewport vs ImGui panel detection
    private final double[] mouseX = {0};
    private final double[] mouseY = {0};
    
    // Panel bounds (must match EditorUI constants)
    private static final int UI_LEFT_W    = 340;
    private static final int UI_RIGHT_W   = 280;
    private static final int UI_CONSOLE_H = 200;
    private static final int UI_TOPBAR_H  = 32;
    
    private PhysicaEntity cube;
    private PhysicaEntity sphere;
    private PhysicaEntity grid;
    
    private PhysicaConsole console;
    private HeliumParser parser;
    private PhysicsSolver physics;
    private SimulationCache cache;
    private TimelineController timeline;
    private EditorUI editorUI;

    public void run() {
        init();
        loop();
        
        editorUI.cleanup();
        
        Callbacks.glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(width, height, "Physica Engine v0.1.0", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Set Window Icon
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer icon = stbi_load("physicaLogo.png", w, h, comp, 4);
            if (icon != null) {
                GLFWImage image = GLFWImage.malloc(stack);
                image.set(w.get(0), h.get(0), icon);
                GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
                images.put(0, image);
                glfwSetWindowIcon(window, images);
                stbi_image_free(icon);
            }
        }

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            this.width = w;
            this.height = h;
            glViewport(0, 0, w, h);
        });

        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            this.windowWidth = w;
            this.windowHeight = h;
        });

        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (vidmode.width() - width) / 2, (vidmode.height() - height) / 2);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // v-sync
        glfwShowWindow(window);

        GL.createCapabilities();

        // Background color
        glClearColor(0.039f, 0.039f, 0.039f, 1.0f); // #0A0A0A
        glEnable(GL_DEPTH_TEST);
        // Enable blending for potential transparency or smoother lines
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Setup Camera at Orbit Pos: 0.0, 6.5, 18.0
        camera = new Camera(0.0f, 6.5f, 18.0f);
        
        // Initialize EditorUI FIRST so it can set its callbacks
        // We will then wrap/chain them for the camera
        editorUI = new EditorUI();
        editorUI.init(window, parser, console, timeline, ""); // Initial script set later

        // Setup input callbacks and chain them with ImGui's
        final org.lwjgl.glfw.GLFWScrollCallback[] prevScroll = {null};
        prevScroll[0] = glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (prevScroll[0] != null) prevScroll[0].invoke(win, xoffset, yoffset);
            
            if (isInViewport()) {
                camera.zoom((float) yoffset * 1.5f);
            }
        });

        double[] lastX = {0};
        double[] lastY = {0};
        final org.lwjgl.glfw.GLFWCursorPosCallback[] prevCursor = {null};
        prevCursor[0] = glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (prevCursor[0] != null) prevCursor[0].invoke(win, xpos, ypos);

            float dx = (float) (xpos - lastX[0]);
            float dy = (float) (ypos - lastY[0]);
            lastX[0] = xpos;
            lastY[0] = ypos;

            if (isInViewport()) {
                if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
                    camera.addYaw(dx * 0.005f);
                    camera.addPitch(dy * 0.005f);
                } else if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS || 
                           glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_MIDDLE) == GLFW_PRESS) {
                    camera.pan(-dx * 0.015f, dy * 0.015f);
                }
            }
        });
        
        // Shaders
        try {
            solidShader = new Shader("src/main/resources/shaders/basic.vert", "src/main/resources/shaders/basic.frag");
            gridShader = new Shader("src/main/resources/shaders/grid.vert", "src/main/resources/shaders/grid.frag");
        } catch (Exception e) {
            System.err.println("Failed to load shaders: " + e.getMessage());
        }

        cube = PhysicaEntity.createCube();
        // Create unit sphere (radius 1.0), scale is applied per entity
        sphere = PhysicaEntity.createSphere(1.0f, 64, 64);
        grid = PhysicaEntity.createGrid(100, 1.0f);

        // Initialize Console and Parser
        console = new PhysicaConsole();
        console.log("Physica Engine v0.1.0");
        parser = new HeliumParser(console);
        physics = new PhysicsSolver();
        cache = new SimulationCache();
        timeline = new TimelineController(cache);

        // Default script showcasing the new full physics system
        String liveScript =
            "phy: enable;\n" +
            "phy_gravity: {0,-9.81,0};\n" +
            "phy_wind: {0.3,0,0};\n" +
            "spawn(sphere): id[1], rad{1.5}, pos{-2,12,0}, mass{1.0}, bounce{0.65}, friction{0.3};\n" +
            "spawn(sphere): id[2], rad{1.0}, pos{2,16,0}, mass{0.5}, bounce{0.8}, friction{0.1};\n" +
            "spawn(cube): id[3], size{12,1,12}, pos{0,0,0}, static{true}, friction{0.6};\n" +
            "anim_interpolation: linear;\n" +
            "phy_sim: loop;";
        parser.parseScript(liveScript);
        
        // Update EditorUI with the parsed script
        editorUI.updateScript(liveScript);
    }

    private void loop() {
        Matrix4f projection = new Matrix4f();
        Matrix4f view = new Matrix4f();
        Matrix4f model = new Matrix4f();

        double lastTime = glfwGetTime();
        double fixedTimeStep = 1.0 / 60.0;
        double accumulator = 0.0;
        float currentSimTime = 0.0f;
        
        int frames = 0;
        int displayedFps = 0;
        double fpsTimer = lastTime;

        while (!glfwWindowShouldClose(window)) {
            if (parser.wasJustParsed()) {
                cache.clear();
                currentSimTime = 0.0f;
                parser.clearJustParsedFlag();
            }

            double currentTime = glfwGetTime();
            double frameTime = currentTime - lastTime;
            lastTime = currentTime;
            accumulator += frameTime;

            while (accumulator >= fixedTimeStep) {
                if (parser.isPhysicsEnabled() && !timeline.hasForcedRenderTime()) {
                    // Sync global physics config from script
                    physics.setGravity(parser.getGravity().x, parser.getGravity().y, parser.getGravity().z);
                    physics.setWind(parser.getWind().x, parser.getWind().y, parser.getWind().z);
                    // Drain one-shot impulses from the script queue
                    parser.drainImpulses();
                    physics.update(new ArrayList<>(parser.getEntities().values()), (float)fixedTimeStep);
                    currentSimTime += fixedTimeStep;
                    cache.saveSnapshot(currentSimTime, new ArrayList<>(parser.getEntities().values()));
                }
                accumulator -= fixedTimeStep;
            }

            // Sync visual states based on script config
            timeline.setInterpolation(parser.isInterpolationLinear());
            timeline.setRenderTime(parser.getForcedRenderTime());
            timeline.applyVisualState(new ArrayList<>(parser.getEntities().values()), currentSimTime);

            // UI Feedback on viewport frame (via Window Title as proxy)
            frames++;
            if (currentTime - fpsTimer >= 1.0) {
                displayedFps = frames;
                String status = parser.isPhysicsEnabled() ? "RUNNING (Pastel Green)" : "STOPPED";
                glfwSetWindowTitle(window, String.format("Physica Engine v0.1.0 | FPS: %d | Physics: %s", frames, status));
                frames = 0;
                fpsTimer += 1.0;
            }

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            float aspect = (float) width / height;
            projection.setPerspective((float) Math.toRadians(60.0f), aspect, 0.1f, 100.0f);
            
            camera.update(view);

            if (gridShader != null) {
                gridShader.use();
                gridShader.setMatrix4f("projection", projection);
                gridShader.setMatrix4f("view", view);
                model.identity();
                gridShader.setMatrix4f("model", model);
                gridShader.setVector3f("color", 0.1f, 0.3f, 0.1f); 
                grid.draw(GL_LINES);
            }

            if (solidShader != null) {
                solidShader.use();
                solidShader.setMatrix4f("projection", projection);
                solidShader.setMatrix4f("view", view);
                // pastel green #B2F2BB -> 0.698, 0.949, 0.733
                solidShader.setVector3f("color", 0.698f, 0.949f, 0.733f); 
                // Dynamic Rendering based on Script State
                for (SceneEntity entity : parser.getEntities().values()) {
                    model.identity().translate(entity.visualPosition).rotate(entity.visualRotation).scale(entity.scale);
                    solidShader.setMatrix4f("model", model);
                    
                    if (entity.type.equalsIgnoreCase("cube")) {
                        cube.draw(GL_TRIANGLES);
                    } else if (entity.type.equalsIgnoreCase("sphere")) {
                        sphere.draw(GL_TRIANGLES);
                    }
                }
            }

            editorUI.render(displayedFps, currentSimTime, windowWidth, windowHeight);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    public static void main(String[] args) {
        new Engine().run();
    }

    /** Returns true if the current mouse position is inside the 3D viewport area,
     *  i.e. NOT over any ImGui panel (left editor, right inspector, top bar, bottom console). */
    private boolean isInViewport() {
        double[] x = new double[1];
        double[] y = new double[1];
        glfwGetCursorPos(window, x, y);
        
        return x[0] > UI_LEFT_W
            && x[0] < (windowWidth  - UI_RIGHT_W)
            && y[0] > UI_TOPBAR_H
            && y[0] < (windowHeight - UI_CONSOLE_H);
    }

}
