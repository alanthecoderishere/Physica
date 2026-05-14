package com.physica;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class SimulationCache {
    public static class FrameData {
        public final float time;
        public final Map<Integer, PhysicsSnapshot> entities;
        
        public FrameData(float time) {
            this.time = time;
            this.entities = new HashMap<>();
        }
    }
    
    private final List<FrameData> frames = new ArrayList<>();
    
    public void clear() {
        frames.clear();
    }
    
    public void saveSnapshot(float time, List<SceneEntity> sceneEntities) {
        FrameData frame = new FrameData(time);
        for (SceneEntity entity : sceneEntities) {
            frame.entities.put(entity.id, new PhysicsSnapshot(entity.id, entity.position, entity.rotation));
        }
        frames.add(frame);
    }
    
    public List<FrameData> getFrames() {
        return frames;
    }
}
