package com.physica;

import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.util.List;

public class TimelineController {
    private SimulationCache cache;
    private boolean interpolationEnabled = false;
    private float renderTime = -1.0f; 
    
    public TimelineController(SimulationCache cache) {
        this.cache = cache;
    }
    
    public void setInterpolation(boolean enabled) {
        this.interpolationEnabled = enabled;
    }
    
    public void setRenderTime(float time) {
        this.renderTime = time;
    }
    
    public float getRenderTime() {
        return renderTime;
    }
    
    public boolean hasForcedRenderTime() {
        return renderTime >= 0.0f;
    }
    
    public void applyVisualState(List<SceneEntity> entities, float currentTime) {
        float evalTime = hasForcedRenderTime() ? renderTime : currentTime;
        List<SimulationCache.FrameData> frames = cache.getFrames();
        
        if (frames.isEmpty()) {
            for (SceneEntity e : entities) {
                e.visualPosition.set(e.position);
                e.visualRotation.set(e.rotation);
            }
            return;
        }
        
        SimulationCache.FrameData frameA = frames.get(0);
        SimulationCache.FrameData frameB = frames.get(frames.size() - 1);
        
        if (evalTime <= frameA.time) {
            applyFrame(entities, frameA);
            return;
        }
        if (evalTime >= frameB.time) {
            applyFrame(entities, frameB);
            return;
        }
        
        for (int i = 0; i < frames.size() - 1; i++) {
            if (evalTime >= frames.get(i).time && evalTime <= frames.get(i+1).time) {
                frameA = frames.get(i);
                frameB = frames.get(i+1);
                break;
            }
        }
        
        float t = (evalTime - frameA.time) / (frameB.time - frameA.time);
        
        for (SceneEntity e : entities) {
            PhysicsSnapshot snapA = frameA.entities.get(e.id);
            PhysicsSnapshot snapB = frameB.entities.get(e.id);
            
            if (snapA != null && snapB != null) {
                if (interpolationEnabled) {
                    // In-place JOML interpolation to avoid GC allocation
                    e.visualPosition.set(snapA.position()).lerp(snapB.position(), t);
                    e.visualRotation.set(snapA.rotation()).slerp(snapB.rotation(), t);
                } else {
                    e.visualPosition.set(snapA.position());
                    e.visualRotation.set(snapA.rotation());
                }
            } else if (snapA != null) {
                e.visualPosition.set(snapA.position());
                e.visualRotation.set(snapA.rotation());
            }
        }
    }
    
    private void applyFrame(List<SceneEntity> entities, SimulationCache.FrameData frame) {
        for (SceneEntity e : entities) {
            PhysicsSnapshot snap = frame.entities.get(e.id);
            if (snap != null) {
                e.visualPosition.set(snap.position());
                e.visualRotation.set(snap.rotation());
            }
        }
    }
}
