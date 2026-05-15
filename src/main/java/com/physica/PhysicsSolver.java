package com.physica;

import org.joml.Vector3f;
import org.joml.Intersectionf;

import java.util.ArrayList;
import java.util.List;

public class PhysicsSolver {
    private final Vector3f gravity = new Vector3f(0, -9.81f, 0);
    private final Vector3f wind    = new Vector3f(0, 0, 0);
    private Octree octree;

    // Scratch vectors — reused every tick to avoid GC pressure
    private final Vector3f _relVel   = new Vector3f();
    private final Vector3f _normal   = new Vector3f();
    private final Vector3f _tangent  = new Vector3f();
    private final Vector3f _impulse  = new Vector3f();

    public PhysicsSolver() {
        octree = new Octree(new AABB(-200, -200, -200, 200, 200, 200), 0);
    }

    public void setGravity(float x, float y, float z) { gravity.set(x, y, z); }
    public void setWind(float x, float y, float z)    { wind.set(x, y, z); }

    public void update(List<SceneEntity> entities, float dt) {
        octree.clear();
        for (SceneEntity e : entities) octree.insert(e);

        for (SceneEntity e : entities) {
            if (e.isStatic) continue;

            // 1 — Global gravity + wind (both treated as accelerations)
            e.velocity.x += (gravity.x + wind.x) * dt;
            e.velocity.y += (gravity.y + wind.y) * dt;
            e.velocity.z += (gravity.z + wind.z) * dt;

            // 2 — Linear & angular drag
            e.velocity.mul(e.drag);
            e.angularVelocity.mul(e.angularDrag);

            // 3 — Integrate position
            e.position.x += e.velocity.x * dt;
            e.position.y += e.velocity.y * dt;
            e.position.z += e.velocity.z * dt;

            // 4 — Integrate rotation from angular velocity
            float speed = e.angularVelocity.length();
            if (speed > 0.0001f) {
                float angle = speed * dt;
                e.rotation.rotateAxis(angle,
                    e.angularVelocity.x / speed,
                    e.angularVelocity.y / speed,
                    e.angularVelocity.z / speed);
                e.rotation.normalize();
            }

            // 5 — Collisions
            List<SceneEntity> potentials = new ArrayList<>();
            octree.retrieve(potentials, e);
            for (SceneEntity other : potentials) {
                if (e != other) resolveCollision(e, other);
            }
        }
    }

    // ── Collision dispatch ────────────────────────────────────────────────────

    private void resolveCollision(SceneEntity a, SceneEntity b) {
        if (a.isStatic && b.isStatic) return;
        String ta = a.type.toLowerCase(), tb = b.type.toLowerCase();
        if (ta.equals("sphere") && tb.equals("sphere")) sphereSphere(a, b);
        else if (ta.equals("cube")   && tb.equals("cube"))   cubeCube(a, b);
        else sphereCube(ta.equals("sphere") ? a : b, ta.equals("cube") ? a : b);
    }

    // ── Sphere–Sphere ─────────────────────────────────────────────────────────

    private void sphereSphere(SceneEntity a, SceneEntity b) {
        _normal.set(b.position).sub(a.position);
        float dist = _normal.length();
        float radSum = a.scale.x + b.scale.x;
        if (dist >= radSum || dist < 0.0001f) return;

        _normal.div(dist); // normalize in-place
        float penetration = radSum - dist;

        // Positional correction
        float invMassSum = (a.isStatic ? 0 : 1f/a.mass) + (b.isStatic ? 0 : 1f/b.mass);
        if (invMassSum == 0) return;
        if (!a.isStatic) a.position.sub(_normal.x*penetration*(1f/(a.mass*invMassSum)),
                                         _normal.y*penetration*(1f/(a.mass*invMassSum)),
                                         _normal.z*penetration*(1f/(a.mass*invMassSum)));
        if (!b.isStatic) b.position.add(_normal.x*penetration*(1f/(b.mass*invMassSum)),
                                         _normal.y*penetration*(1f/(b.mass*invMassSum)),
                                         _normal.z*penetration*(1f/(b.mass*invMassSum)));

        // Relative velocity
        _relVel.set(b.velocity).sub(a.velocity);
        float velAlongNormal = _relVel.dot(_normal);
        if (velAlongNormal > 0) return;

        float e = Math.min(a.restitution, b.restitution);
        float j = -(1 + e) * velAlongNormal / invMassSum;

        // Normal impulse
        applyImpulsePair(a, b, _normal, j);

        // Friction tangent impulse
        _tangent.set(_relVel).sub(_normal.x*velAlongNormal, _normal.y*velAlongNormal, _normal.z*velAlongNormal);
        float tLen = _tangent.length();
        if (tLen > 0.0001f) {
            _tangent.div(tLen);
            float jt = -_relVel.dot(_tangent) / invMassSum;
            float mu = (a.friction + b.friction) * 0.5f;
            jt = Math.max(-mu * j, Math.min(jt, mu * j));
            applyImpulsePair(a, b, _tangent, jt);
        }

        // Angular spin from impact
        applyAngularImpact(a, _normal, -j * 0.3f);
        applyAngularImpact(b, _normal,  j * 0.3f);
    }

    // ── Cube–Cube (MTV on shortest overlap axis) ───────────────────────────────

    private void cubeCube(SceneEntity a, SceneEntity b) {
        AABB ba = getAABB(a), bb = getAABB(b);
        float ox = Math.min(ba.maxX, bb.maxX) - Math.max(ba.minX, bb.minX);
        float oy = Math.min(ba.maxY, bb.maxY) - Math.max(ba.minY, bb.minY);
        float oz = Math.min(ba.maxZ, bb.maxZ) - Math.max(ba.minZ, bb.minZ);
        if (ox <= 0 || oy <= 0 || oz <= 0) return;

        float nx = 0, ny = 0, nz = 0, pen;
        if (oy <= ox && oy <= oz) { ny = a.position.y < b.position.y ? -1 : 1; pen = oy; }
        else if (ox <= oz)        { nx = a.position.x < b.position.x ? -1 : 1; pen = ox; }
        else                      { nz = a.position.z < b.position.z ? -1 : 1; pen = oz; }

        _normal.set(nx, ny, nz);
        float invMassSum = (a.isStatic ? 0 : 1f/a.mass) + (b.isStatic ? 0 : 1f/b.mass);
        if (invMassSum == 0) return;
        float corr = pen / invMassSum * 0.8f;
        if (!a.isStatic) a.position.sub(nx*corr/a.mass, ny*corr/a.mass, nz*corr/a.mass);
        if (!b.isStatic) b.position.add(nx*corr/b.mass, ny*corr/b.mass, nz*corr/b.mass);

        _relVel.set(b.velocity).sub(a.velocity);
        float velAlongNormal = _relVel.dot(_normal);
        if (velAlongNormal > 0) return;

        float e = Math.min(a.restitution, b.restitution);
        float j = -(1 + e) * velAlongNormal / invMassSum;
        applyImpulsePair(a, b, _normal, j);

        // Friction
        _tangent.set(_relVel).sub(nx*velAlongNormal, ny*velAlongNormal, nz*velAlongNormal);
        float tLen = _tangent.length();
        if (tLen > 0.0001f) {
            _tangent.div(tLen);
            float jt = -_relVel.dot(_tangent) / invMassSum;
            float mu = (a.friction + b.friction) * 0.5f;
            jt = Math.max(-mu*j, Math.min(jt, mu*j));
            applyImpulsePair(a, b, _tangent, jt);
        }
    }

    // ── Sphere–Cube ───────────────────────────────────────────────────────────

    private void sphereCube(SceneEntity sphere, SceneEntity cube) {
        AABB box = getAABB(cube);
        float rad = sphere.scale.x;

        float cx = Math.max(box.minX, Math.min(sphere.position.x, box.maxX));
        float cy = Math.max(box.minY, Math.min(sphere.position.y, box.maxY));
        float cz = Math.max(box.minZ, Math.min(sphere.position.z, box.maxZ));

        _normal.set(sphere.position).sub(cx, cy, cz);
        float dist = _normal.length();
        if (dist >= rad) return;
        if (dist < 0.0001f) { _normal.set(0, 1, 0); dist = 0.0001f; }
        _normal.div(dist);

        float pen = rad - dist;
        float invMassSum = (sphere.isStatic ? 0 : 1f/sphere.mass) + (cube.isStatic ? 0 : 1f/cube.mass);
        if (invMassSum == 0) return;
        if (!sphere.isStatic) sphere.position.add(_normal.x*pen/sphere.mass/invMassSum,
                                                    _normal.y*pen/sphere.mass/invMassSum,
                                                    _normal.z*pen/sphere.mass/invMassSum);
        if (!cube.isStatic)   cube.position.sub(_normal.x*pen/cube.mass/invMassSum,
                                                  _normal.y*pen/cube.mass/invMassSum,
                                                  _normal.z*pen/cube.mass/invMassSum);

        _relVel.set(sphere.velocity).sub(cube.velocity);
        float velAlongNormal = _relVel.dot(_normal);
        if (velAlongNormal > 0) return;

        float e = Math.min(sphere.restitution, cube.restitution);
        float j = -(1 + e) * velAlongNormal / invMassSum;
        applyImpulsePair(sphere, cube, _normal, j);

        // Friction
        _tangent.set(_relVel).sub(_normal.x*velAlongNormal, _normal.y*velAlongNormal, _normal.z*velAlongNormal);
        float tLen = _tangent.length();
        if (tLen > 0.0001f) {
            _tangent.div(tLen);
            float jt = -_relVel.dot(_tangent) / invMassSum;
            float mu = (sphere.friction + cube.friction) * 0.5f;
            jt = Math.max(-mu*j, Math.min(jt, mu*j));
            applyImpulsePair(sphere, cube, _tangent, jt);
        }

        // Spin the sphere on contact
        applyAngularImpact(sphere, _normal, j * 0.5f / Math.max(sphere.scale.x, 0.1f));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyImpulsePair(SceneEntity a, SceneEntity b, Vector3f normal, float j) {
        _impulse.set(normal).mul(j);
        if (!a.isStatic) a.velocity.sub(new Vector3f(_impulse).div(a.mass));
        if (!b.isStatic) b.velocity.add(new Vector3f(_impulse).div(b.mass));
    }

    private void applyAngularImpact(SceneEntity e, Vector3f normal, float scale) {
        if (e.isStatic) return;
        // torque-like spin: cross normal with up to get a rotation axis
        float ax = normal.y * 0 - normal.z * 1;
        float ay = normal.z * 0 - normal.x * 0;
        float az = normal.x * 1 - normal.y * 0;
        e.angularVelocity.add(ax * scale, ay * scale, az * scale);
    }

    private AABB getAABB(SceneEntity e) {
        return new AABB(
            e.position.x - e.scale.x/2f, e.position.y - e.scale.y/2f, e.position.z - e.scale.z/2f,
            e.position.x + e.scale.x/2f, e.position.y + e.scale.y/2f, e.position.z + e.scale.z/2f
        );
    }
}
