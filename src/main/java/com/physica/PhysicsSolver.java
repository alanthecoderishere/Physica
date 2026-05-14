package com.physica;

import org.joml.Vector3f;
import org.joml.Intersectionf;

import java.util.ArrayList;
import java.util.List;

public class PhysicsSolver {
    private static final Vector3f GRAVITY = new Vector3f(0, -9.81f, 0);
    private Octree octree;

    public PhysicsSolver() {
        octree = new Octree(new AABB(-50, -50, -50, 50, 50, 50), 0);
    }

    public void update(List<SceneEntity> entities, float dt) {
        octree.clear();
        for (SceneEntity entity : entities) {
            octree.insert(entity);
        }

        for (SceneEntity entity : entities) {
            if (entity.isStatic) continue;

            // Apply Gravity
            entity.velocity.add(new Vector3f(GRAVITY).mul(dt));
            
            // Apply Velocity
            entity.position.add(new Vector3f(entity.velocity).mul(dt));

            // Octree Collision Retrieval
            List<SceneEntity> potentials = new ArrayList<>();
            octree.retrieve(potentials, entity);

            for (SceneEntity other : potentials) {
                if (entity == other) continue;
                resolveCollision(entity, other);
            }
        }
    }

    private void resolveCollision(SceneEntity a, SceneEntity b) {
        if (a.isStatic && b.isStatic) return;

        if (a.type.equalsIgnoreCase("sphere") && b.type.equalsIgnoreCase("sphere")) {
            // Distance-based Sphere-Sphere
            Vector3f diff = new Vector3f(b.position).sub(a.position);
            float dist = diff.length();
            float radSum = a.scale.x + b.scale.x; 

            if (dist < radSum) {
                float penetration = radSum - dist;
                Vector3f normal = new Vector3f(diff).normalize();
                
                if (!a.isStatic) a.position.sub(new Vector3f(normal).mul(penetration * 0.5f));
                if (!b.isStatic) b.position.add(new Vector3f(normal).mul(penetration * 0.5f));
                
                Vector3f relVel = new Vector3f(b.velocity).sub(a.velocity);
                float velAlongNormal = relVel.dot(normal);
                if (velAlongNormal > 0) return;
                
                float e = 0.6f; // Bounciness
                float j = -(1 + e) * velAlongNormal;
                j /= (1/a.mass + 1/b.mass);
                
                Vector3f impulse = new Vector3f(normal).mul(j);
                if (!a.isStatic) a.velocity.sub(new Vector3f(impulse).mul(1/a.mass));
                if (!b.isStatic) b.velocity.add(new Vector3f(impulse).mul(1/b.mass));
            }
        } else if (a.type.equalsIgnoreCase("cube") && b.type.equalsIgnoreCase("cube")) {
            // Cube-Cube (SAT Lite via AABB intersection)
            AABB boxA = getAABB(a);
            AABB boxB = getAABB(b);
            
            if (Intersectionf.testAabAab(boxA.minX, boxA.minY, boxA.minZ, boxA.maxX, boxA.maxY, boxA.maxZ,
                                         boxB.minX, boxB.minY, boxB.minZ, boxB.maxX, boxB.maxY, boxB.maxZ)) {
                // Hacky fallback to keep things resting on each other
                if (!a.isStatic && a.position.y > b.position.y) {
                    a.position.y = boxB.maxY + a.scale.y / 2.0f;
                    a.velocity.y = 0;
                }
            }
        } else {
            // Sphere-Cube
            SceneEntity sphere = a.type.equalsIgnoreCase("sphere") ? a : b;
            SceneEntity cube = a.type.equalsIgnoreCase("cube") ? a : b;
            
            AABB box = getAABB(cube);
            float rad = sphere.scale.x;
            
            float cx = Math.max(box.minX, Math.min(sphere.position.x, box.maxX));
            float cy = Math.max(box.minY, Math.min(sphere.position.y, box.maxY));
            float cz = Math.max(box.minZ, Math.min(sphere.position.z, box.maxZ));
            
            float distance = new Vector3f(cx, cy, cz).distance(sphere.position);
            
            if (distance < rad) {
                if (!sphere.isStatic) {
                    Vector3f normal = new Vector3f(sphere.position).sub(cx, cy, cz);
                    if (normal.lengthSquared() == 0) normal.set(0, 1, 0);
                    normal.normalize();
                    
                    float penetration = rad - distance;
                    sphere.position.add(new Vector3f(normal).mul(penetration));
                    
                    // Simple bounce
                    if (sphere.velocity.y < 0 && normal.y > 0.5f) {
                        sphere.velocity.y *= -0.5f; 
                        sphere.velocity.x *= 0.9f; 
                        sphere.velocity.z *= 0.9f;
                    } else {
                        sphere.velocity.reflect(normal).mul(0.5f);
                    }
                }
            }
        }
    }

    private AABB getAABB(SceneEntity entity) {
        float rx = entity.scale.x / 2.0f;
        float ry = entity.scale.y / 2.0f;
        float rz = entity.scale.z / 2.0f;
        return new AABB(
            entity.position.x - rx, entity.position.y - ry, entity.position.z - rz,
            entity.position.x + rx, entity.position.y + ry, entity.position.z + rz
        );
    }
}
