package com.physica;

import org.joml.Vector3f;
import org.joml.Quaternionf;

public class SceneEntity {
    public int id;
    public String type; // e.g., "cube" or "sphere"
    public Vector3f position;
    public Quaternionf rotation;
    public Vector3f scale;
    public Vector3f velocity;
    public Vector3f acceleration;
    public float mass;
    public boolean isStatic;
    
    public Vector3f visualPosition;
    public Quaternionf visualRotation;
    
    public SceneEntity(int id, String type, Vector3f position, Vector3f scale) {
        this.id = id;
        this.type = type;
        this.position = position;
        this.rotation = new Quaternionf();
        this.visualPosition = new Vector3f(position);
        this.visualRotation = new Quaternionf();
        this.scale = scale;
        this.velocity = new Vector3f();
        this.acceleration = new Vector3f();
        this.mass = 1.0f;
        // Make the ground cube static so it doesn't fall
        this.isStatic = type.equalsIgnoreCase("cube") && position.y <= 1.0f;
    }
}
