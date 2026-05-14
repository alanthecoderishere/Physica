package com.physica;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private Vector3f target;
    private Vector3f up;
    
    private float distance;
    private float pitch;
    private float yaw;

    public Camera(float x, float y, float z) {
        position = new Vector3f(x, y, z);
        target = new Vector3f(0.0f, 2.5f, 0.0f); // Look slightly up
        up = new Vector3f(0.0f, 1.0f, 0.0f);
        
        Vector3f diff = new Vector3f(position).sub(target);
        distance = diff.length();
        pitch = (float) Math.asin(diff.y / distance);
        yaw = (float) Math.atan2(diff.x, diff.z);
    }

    public void update(Matrix4f viewMatrix) {
        float horizontalDistance = distance * (float) Math.cos(pitch);
        float verticalDistance = distance * (float) Math.sin(pitch);
        
        float xOffset = horizontalDistance * (float) Math.sin(yaw);
        float zOffset = horizontalDistance * (float) Math.cos(yaw);
        
        position.set(
            target.x + xOffset,
            target.y + verticalDistance,
            target.z + zOffset
        );
        
        viewMatrix.setLookAt(position, target, up);
    }
    
    public void addPitch(float amount) {
        pitch += amount;
        // Constrain pitch to avoid gimbal lock/flipping
        if (pitch > 1.5f) pitch = 1.5f;
        if (pitch < -1.5f) pitch = -1.5f;
    }
    
    public void addYaw(float amount) {
        yaw += amount;
    }
    
    public void zoom(float amount) {
        distance -= amount;
        if (distance < 1.0f) distance = 1.0f;
        if (distance > 100.0f) distance = 100.0f;
    }
    
    public void pan(float dx, float dy) {
        Vector3f forward = new Vector3f(target).sub(position).normalize();
        Vector3f right = new Vector3f(forward).cross(up).normalize();
        Vector3f realUp = new Vector3f(right).cross(forward).normalize();
        
        target.add(right.mul(dx));
        target.add(realUp.mul(dy));
    }
}
