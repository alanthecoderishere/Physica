package com.physica;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeliumParser {
    private final PhysicaConsole console;
    private final SequencedMap<Integer, SceneEntity> entities = new LinkedHashMap<>();

    // State
    private boolean physicsEnabled   = false;
    private boolean interpolationLinear = false;
    private float   forcedRenderTime = -1.0f;
    private boolean justParsed       = false;

    // Physics globals (read by Engine → PhysicsSolver)
    private final Vector3f gravity = new Vector3f(0, -9.81f, 0);
    private final Vector3f wind    = new Vector3f(0, 0, 0);

    // Pending one-shot impulses drained by Engine each tick
    private final List<int[]>     pendingImpulseIds  = new ArrayList<>();
    private final List<Vector3f>  pendingImpulseVecs = new ArrayList<>();

    // Regexes
    private static final Pattern TYPE_PAT  = Pattern.compile("spawn\\s*\\(\\s*(.*?)\\s*\\)");
    private static final Pattern ID_PAT    = Pattern.compile("id\\s*\\[\\s*(.*?)\\s*\\]");
    private static final Pattern SIZE_PAT  = Pattern.compile("size\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern RAD_PAT   = Pattern.compile("rad\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern POS_PAT   = Pattern.compile("pos\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern MASS_PAT  = Pattern.compile("mass\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern BNC_PAT   = Pattern.compile("bounce\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern FRIC_PAT  = Pattern.compile("friction\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern DRAG_PAT  = Pattern.compile("drag\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern STAT_PAT  = Pattern.compile("static\\s*\\{\\s*(.*?)\\s*\\}");
    private static final Pattern VEC3_PAT  = Pattern.compile("\\{\\s*(.*?)\\s*\\}");

    public HeliumParser(PhysicaConsole console) { this.console = console; }

    public void parseScript(String script) {
        console.log("Script loaded: script.phy");
        entities.clear();
        physicsEnabled      = false;
        interpolationLinear = false;
        forcedRenderTime    = -1.0f;
        gravity.set(0, -9.81f, 0);
        wind.set(0, 0, 0);
        pendingImpulseIds.clear();
        pendingImpulseVecs.clear();
        justParsed = true;

        for (String raw : script.split(";")) {
            String cmd = raw.trim().replaceAll("\\s+", " ");
            if (cmd.isEmpty()) continue;

            if (cmd.equals("phy: enable")) {
                physicsEnabled = true;
                console.log("Physics enabled");
            } else if (cmd.equals("phy_sim: loop")) {
                console.log("Simulation started (loop)");
            } else if (cmd.startsWith("spawn")) {
                parseSpawn(cmd);
            } else if (cmd.startsWith("phy_gravity:")) {
                Vector3f v = parseVec3(cmd);
                gravity.set(v);
                console.log(String.format("Gravity → (%.2f, %.2f, %.2f)", v.x, v.y, v.z));
            } else if (cmd.startsWith("phy_wind:")) {
                Vector3f v = parseVec3(cmd);
                wind.set(v);
                console.log(String.format("Wind → (%.2f, %.2f, %.2f)", v.x, v.y, v.z));
            } else if (cmd.startsWith("apply_impulse:")) {
                parseApplyImpulse(cmd);
            } else if (cmd.startsWith("anim_interpolation:")) {
                String t = cmd.split(":", 2)[1].trim();
                interpolationLinear = t.equalsIgnoreCase("linear");
                console.log("Interpolation: " + t);
            } else if (cmd.startsWith("anim_render:")) {
                forcedRenderTime = Float.parseFloat(cmd.split(":", 2)[1].trim());
                console.log("Render time locked to " + forcedRenderTime + "s");
            } else if (cmd.startsWith("anim_time:") || cmd.startsWith("anim_timestamp:") || cmd.startsWith("phy_timestamp")) {
                // accepted but no-op at runtime
            } else {
                console.log("Unknown command: " + cmd);
            }
        }
    }

    private void parseSpawn(String cmd) {
        try {
            Matcher tm = TYPE_PAT.matcher(cmd); String type = tm.find() ? tm.group(1).trim() : "cube";
            Matcher im = ID_PAT.matcher(cmd);   int id = im.find() ? Integer.parseInt(im.group(1).trim()) : -1;
            if (id == -1) { console.log("spawn: missing id"); return; }

            Vector3f scale = new Vector3f(1);
            Matcher sm = SIZE_PAT.matcher(cmd), rm = RAD_PAT.matcher(cmd);
            if (sm.find()) {
                String[] p = sm.group(1).split(",");
                scale.set(Float.parseFloat(p[0].trim()), Float.parseFloat(p[1].trim()), Float.parseFloat(p[2].trim()));
            } else if (rm.find()) {
                float r = Float.parseFloat(rm.group(1).trim()); scale.set(r, r, r);
            }

            Vector3f pos = new Vector3f(0);
            Matcher pm = POS_PAT.matcher(cmd);
            if (pm.find()) {
                String[] p = pm.group(1).split(",");
                pos.set(Float.parseFloat(p[0].trim()), Float.parseFloat(p[1].trim()), Float.parseFloat(p[2].trim()));
            }

            SceneEntity e = new SceneEntity(id, type, pos, scale);

            Matcher mm = MASS_PAT.matcher(cmd);  if (mm.find()) e.mass        = Float.parseFloat(mm.group(1).trim());
            Matcher bm = BNC_PAT.matcher(cmd);   if (bm.find()) e.restitution = Float.parseFloat(bm.group(1).trim());
            Matcher fm = FRIC_PAT.matcher(cmd);  if (fm.find()) e.friction    = Float.parseFloat(fm.group(1).trim());
            Matcher dm = DRAG_PAT.matcher(cmd);  if (dm.find()) e.drag        = Float.parseFloat(dm.group(1).trim());
            Matcher stm = STAT_PAT.matcher(cmd); if (stm.find()) e.isStatic   = stm.group(1).trim().equalsIgnoreCase("true");

            entities.put(id, e);
            console.log(String.format("Spawned %s id:%d  mass:%.1f  bounce:%.2f  friction:%.2f",
                type, id, e.mass, e.restitution, e.friction));
        } catch (Exception ex) {
            console.log("spawn error: " + ex.getMessage());
        }
    }

    private void parseApplyImpulse(String cmd) {
        try {
            Matcher im = ID_PAT.matcher(cmd);
            if (!im.find()) return;
            int id = Integer.parseInt(im.group(1).trim());
            // extract force{x,y,z}
            Matcher fm = Pattern.compile("force\\s*\\{\\s*(.*?)\\s*\\}").matcher(cmd);
            if (!fm.find()) return;
            String[] p = fm.group(1).split(",");
            float fx = Float.parseFloat(p[0].trim());
            float fy = Float.parseFloat(p[1].trim());
            float fz = Float.parseFloat(p[2].trim());
            pendingImpulseIds.add(new int[]{id});
            pendingImpulseVecs.add(new Vector3f(fx, fy, fz));
            console.log(String.format("Impulse queued → id:%d  (%.1f, %.1f, %.1f)", id, fx, fy, fz));
        } catch (Exception ex) {
            console.log("apply_impulse error: " + ex.getMessage());
        }
    }

    private Vector3f parseVec3(String cmd) {
        Matcher m = VEC3_PAT.matcher(cmd);
        if (!m.find()) return new Vector3f(0);
        String[] p = m.group(1).split(",");
        return new Vector3f(Float.parseFloat(p[0].trim()), Float.parseFloat(p[1].trim()), Float.parseFloat(p[2].trim()));
    }

    /** Drain pending impulses — called by Engine at the start of each physics tick. */
    public void drainImpulses() {
        for (int i = 0; i < pendingImpulseIds.size(); i++) {
            int id = pendingImpulseIds.get(i)[0];
            SceneEntity e = entities.get(id);
            if (e != null) e.applyImpulse(pendingImpulseVecs.get(i));
        }
        pendingImpulseIds.clear();
        pendingImpulseVecs.clear();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public SequencedMap<Integer, SceneEntity> getEntities()    { return entities; }
    public Vector3f getGravity()                               { return gravity; }
    public Vector3f getWind()                                  { return wind; }
    public boolean isPhysicsEnabled()                          { return physicsEnabled; }
    public boolean isInterpolationLinear()                     { return interpolationLinear; }
    public float   getForcedRenderTime()                       { return forcedRenderTime; }
    public boolean wasJustParsed()                             { return justParsed; }
    public void    clearJustParsedFlag()                       { justParsed = false; }
    public void    setForcedRenderTime(float t)                { forcedRenderTime = t; }
    public void    clearForcedRenderTime()                     { forcedRenderTime = -1.0f; }
}
