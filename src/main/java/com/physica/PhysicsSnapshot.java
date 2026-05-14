package com.physica;

import org.joml.Vector3f;
import org.joml.Quaternionf;

public record PhysicsSnapshot(int id, Vector3f position, Quaternionf rotation) {
    public PhysicsSnapshot(int id, Vector3f position, Quaternionf rotation) {
        this.id = id;
        this.position = new Vector3f(position);
        this.rotation = new Quaternionf(rotation);
    }
}
