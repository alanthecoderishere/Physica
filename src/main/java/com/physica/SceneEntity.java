package com.physica;

import org.joml.Vector3f;
import org.joml.Quaternionf;

public class SceneEntity {
    public int id;
    public String type; // "cube" or "sphere"

    // ── Physics state ────────────────────────────────────────────────────────
    public Vector3f position;
    public Quaternionf rotation;
    public Vector3f scale;

    public Vector3f velocity;
    public Vector3f angularVelocity;   // rad/s  — drives rotation each tick
    public Vector3f acceleration;      // per-frame accumulated forces / mass

    public float mass        = 1.0f;
    public float restitution = 0.5f;   // bounciness  [0..1]
    public float friction    = 0.4f;   // surface friction coefficient
    public float drag        = 0.98f;  // linear velocity damping per tick
    public float angularDrag = 0.90f;  // angular damping per tick

    public boolean isStatic;

    // ── Visual state (decoupled from physics for interpolation) ───────────────
    public Vector3f visualPosition;
    public Quaternionf visualRotation;

    public SceneEntity(int id, String type, Vector3f position, Vector3f scale) {
        this.id   = id;
        this.type = type;

        this.position        = position;
        this.rotation        = new Quaternionf();
        this.visualPosition  = new Vector3f(position);
        this.visualRotation  = new Quaternionf();

        this.scale           = scale;
        this.velocity        = new Vector3f();
        this.angularVelocity = new Vector3f();
        this.acceleration    = new Vector3f();

        // Heuristic: flat cubes at y<=1 are treated as ground planes
        this.isStatic = type.equalsIgnoreCase("cube") && position.y <= 1.0f;
    }

    /** Apply an instantaneous impulse (world-space) to this entity. */
    public void applyImpulse(Vector3f impulse) {
        if (isStatic) return;
        velocity.add(new Vector3f(impulse).div(mass));
    }

    /** Accumulate a continuous force (world-space) for one physics tick.
     *  Call clearForces() at the start of each tick before accumulating. */
    public void addForce(Vector3f force) {
        if (isStatic) return;
        acceleration.add(new Vector3f(force).div(mass));
    }

    public void clearForces() {
        acceleration.set(0, 0, 0);
    }
}
