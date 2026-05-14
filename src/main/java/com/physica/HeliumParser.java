package com.physica;

import org.joml.Vector3f;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeliumParser {
    private final PhysicaConsole console;
    private final SequencedMap<Integer, SceneEntity> entities = new LinkedHashMap<>();
    private boolean physicsEnabled = false;
    private boolean interpolationLinear = false;
    private float forcedRenderTime = -1.0f;
    private boolean justParsed = false;

    // Robust regex to extract properties ignoring whitespaces
    private static final Pattern TYPE_PATTERN = Pattern.compile("spawn\\s*\\(\\s*(.*?)\\s*\\)");
    private static final Pattern ID_PATTERN = Pattern.compile("id\\s*\\[\\s*(.*?)\\s*\\]");
    private static final Pattern SIZE_PATTERN = Pattern.compile("size\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern RAD_PATTERN = Pattern.compile("rad\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern POS_PATTERN = Pattern.compile("pos\\s*\\{\\s*(.*?)\\s*\\}");

    public HeliumParser(PhysicaConsole console) {
        this.console = console;
    }

    public void parseScript(String script) {
        console.log("Script loaded: script.phy");
        entities.clear();
        physicsEnabled = false;
        interpolationLinear = false;
        forcedRenderTime = -1.0f;
        justParsed = true;

        String[] commands = script.split(";");
        for (String command : commands) {
            command = command.trim();
            if (command.isEmpty()) continue;

            if (command.equals("phy: enable")) {
                physicsEnabled = true;
                console.log("Physics enabled");
            } else if (command.startsWith("spawn")) {
                parseSpawnCommand(command);
            } else if (command.equals("phy_sim: loop")) {
                console.log("Simulation started (loop)");
            } else if (command.startsWith("anim_interpolation:")) {
                String type = command.split(":")[1].trim();
                interpolationLinear = type.equalsIgnoreCase("linear");
                console.log("Interpolation set to: " + type);
            } else if (command.startsWith("anim_render:")) {
                String rtStr = command.split(":")[1].trim();
                forcedRenderTime = Float.parseFloat(rtStr);
                console.log("Forced render time set to " + rtStr);
            } else if (command.startsWith("anim_time:") || command.startsWith("anim_timestamp:")) {
                String timestamp = command.split(":")[1].trim();
                console.log("Anim config set to " + timestamp);
            } else if (command.startsWith("phy_timestamp")) {
                String timestamp = command.split(":")[1].trim();
                console.log("Timestamp set to " + timestamp);
            } else {
                console.log("Unknown command: " + command);
            }
        }
    }

    private void parseSpawnCommand(String command) {
        try {
            Matcher typeMatcher = TYPE_PATTERN.matcher(command);
            String type = typeMatcher.find() ? typeMatcher.group(1).trim() : "unknown";

            Matcher idMatcher = ID_PATTERN.matcher(command);
            int id = idMatcher.find() ? Integer.parseInt(idMatcher.group(1).trim()) : -1;

            Vector3f scale = new Vector3f(1.0f);
            Matcher sizeMatcher = SIZE_PATTERN.matcher(command);
            Matcher radMatcher = RAD_PATTERN.matcher(command);
            
            if (sizeMatcher.find()) {
                String[] parts = sizeMatcher.group(1).split(",");
                scale.set(Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim()), Float.parseFloat(parts[2].trim()));
            } else if (radMatcher.find()) {
                float rad = Float.parseFloat(radMatcher.group(1).trim());
                scale.set(rad, rad, rad); // Use radius as uniform scale
            }

            Vector3f pos = new Vector3f(0.0f);
            Matcher posMatcher = POS_PATTERN.matcher(command);
            if (posMatcher.find()) {
                String[] parts = posMatcher.group(1).split(",");
                pos.set(Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim()), Float.parseFloat(parts[2].trim()));
            }

            if (id != -1) {
                entities.put(id, new SceneEntity(id, type, pos, scale));
                console.log("Spawned " + type + " (id: " + id + ")");
            } else {
                console.log("Failed to find id for spawn: " + command);
            }
        } catch (Exception e) {
            console.log("Error parsing spawn command: " + e.getMessage());
        }
    }

    public SequencedMap<Integer, SceneEntity> getEntities() {
        return entities;
    }

    public boolean isPhysicsEnabled() {
        return physicsEnabled;
    }

    public boolean isInterpolationLinear() {
        return interpolationLinear;
    }

    public float getForcedRenderTime() {
        return forcedRenderTime;
    }

    public boolean wasJustParsed() {
        return justParsed;
    }

    public void clearJustParsedFlag() {
        this.justParsed = false;
    }

    public void setForcedRenderTime(float t) {
        this.forcedRenderTime = t;
    }

    public void clearForcedRenderTime() {
        this.forcedRenderTime = -1.0f;
    }
}
