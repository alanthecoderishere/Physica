package com.physica;

import java.util.ArrayList;
import java.util.List;

public class Octree {
    private static final int MAX_ENTITIES = 4;
    private static final int MAX_DEPTH = 5;

    private AABB bounds;
    private List<SceneEntity> entities;
    private Octree[] children;
    private int depth;

    public Octree(AABB bounds, int depth) {
        this.bounds = bounds;
        this.depth = depth;
        this.entities = new ArrayList<>();
    }

    public void insert(SceneEntity entity) {
        if (children != null) {
            int index = getIndex(entity);
            if (index != -1) {
                children[index].insert(entity);
                return;
            }
        }

        entities.add(entity);

        if (entities.size() > MAX_ENTITIES && depth < MAX_DEPTH) {
            if (children == null) {
                split();
            }
            int i = 0;
            while (i < entities.size()) {
                SceneEntity e = entities.get(i);
                int index = getIndex(e);
                if (index != -1) {
                    children[index].insert(e);
                    entities.remove(i);
                } else {
                    i++;
                }
            }
        }
    }

    public List<SceneEntity> retrieve(List<SceneEntity> returnEntities, SceneEntity entity) {
        int index = getIndex(entity);
        if (index != -1 && children != null) {
            children[index].retrieve(returnEntities, entity);
        } else if (children != null) {
            // Overlaps multiple children
            for (Octree child : children) {
                if (child.bounds.intersects(getEntityAABB(entity))) {
                    child.retrieve(returnEntities, entity);
                }
            }
        }
        returnEntities.addAll(entities);
        return returnEntities;
    }

    public void clear() {
        entities.clear();
        if (children != null) {
            for (int i = 0; i < 8; i++) {
                children[i].clear();
            }
            children = null;
        }
    }

    private void split() {
        float x = bounds.minX;
        float y = bounds.minY;
        float z = bounds.minZ;
        float w = (bounds.maxX - bounds.minX) / 2.0f;
        float h = (bounds.maxY - bounds.minY) / 2.0f;
        float d = (bounds.maxZ - bounds.minZ) / 2.0f;

        children = new Octree[8];
        children[0] = new Octree(new AABB(x + w, y + h, z, x + w*2, y + h*2, z + d), depth + 1);
        children[1] = new Octree(new AABB(x, y + h, z, x + w, y + h*2, z + d), depth + 1);
        children[2] = new Octree(new AABB(x, y, z, x + w, y + h, z + d), depth + 1);
        children[3] = new Octree(new AABB(x + w, y, z, x + w*2, y + h, z + d), depth + 1);
        children[4] = new Octree(new AABB(x + w, y + h, z + d, x + w*2, y + h*2, z + d*2), depth + 1);
        children[5] = new Octree(new AABB(x, y + h, z + d, x + w, y + h*2, z + d*2), depth + 1);
        children[6] = new Octree(new AABB(x, y, z + d, x + w, y + h, z + d*2), depth + 1);
        children[7] = new Octree(new AABB(x + w, y, z + d, x + w*2, y + h, z + d*2), depth + 1);
    }

    private int getIndex(SceneEntity entity) {
        AABB eBounds = getEntityAABB(entity);
        int index = -1;
        float midX = bounds.minX + (bounds.maxX - bounds.minX) / 2.0f;
        float midY = bounds.minY + (bounds.maxY - bounds.minY) / 2.0f;
        float midZ = bounds.minZ + (bounds.maxZ - bounds.minZ) / 2.0f;

        boolean top = (eBounds.minY > midY);
        boolean bottom = (eBounds.maxY < midY);
        boolean left = (eBounds.maxX < midX);
        boolean right = (eBounds.minX > midX);
        boolean front = (eBounds.maxZ < midZ);
        boolean back = (eBounds.minZ > midZ);

        if (top) {
            if (right && front) index = 0;
            else if (left && front) index = 1;
            else if (right && back) index = 4;
            else if (left && back) index = 5;
        } else if (bottom) {
            if (left && front) index = 2;
            else if (right && front) index = 3;
            else if (left && back) index = 6;
            else if (right && back) index = 7;
        }
        return index;
    }

    private AABB getEntityAABB(SceneEntity entity) {
        float rx = entity.scale.x / 2.0f;
        float ry = entity.scale.y / 2.0f;
        float rz = entity.scale.z / 2.0f;
        if (entity.type.equalsIgnoreCase("sphere")) {
            rx = ry = rz = entity.scale.x; 
        }
        return new AABB(
            entity.position.x - rx, entity.position.y - ry, entity.position.z - rz,
            entity.position.x + rx, entity.position.y + ry, entity.position.z + rz
        );
    }
}
