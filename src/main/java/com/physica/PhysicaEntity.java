package com.physica;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class PhysicaEntity {
    private int vaoId;
    private int vboId;
    private int vertexCount;

    public PhysicaEntity(float[] vertices) {
        this.vertexCount = vertices.length / 3;
        
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void draw(int mode) {
        glBindVertexArray(vaoId);
        glDrawArrays(mode, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }

    public static PhysicaEntity createCube() {
        float[] vertices = new float[]{
            // Back face
            -0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f,         
             0.5f,  0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
            // Front face
            -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,
             0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f,
            // Left face
            -0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f, -0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
            // Right face
             0.5f,  0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,
             0.5f, -0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,
            // Bottom face
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,
             0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f, -0.5f,
            // Top face
            -0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,
             0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f
        };
        return new PhysicaEntity(vertices);
    }

    public static PhysicaEntity createSphere(float radius, int sectors, int stacks) {
        List<Float> vertices = new ArrayList<>();
        float sectorStep = (float) (2 * Math.PI / sectors);
        float stackStep = (float) (Math.PI / stacks);

        for (int i = 0; i <= stacks; ++i) {
            float stackAngle = (float) (Math.PI / 2 - i * stackStep);
            float xz = radius * (float) Math.cos(stackAngle);
            float y = radius * (float) Math.sin(stackAngle);

            for (int j = 0; j <= sectors; ++j) {
                float sectorAngle = j * sectorStep;
                float x = xz * (float) Math.cos(sectorAngle);
                float z = xz * (float) Math.sin(sectorAngle);
                vertices.add(x);
                vertices.add(y);
                vertices.add(z);
            }
        }

        List<Float> finalVertices = new ArrayList<>();
        for (int i = 0; i < stacks; ++i) {
            int k1 = i * (sectors + 1);
            int k2 = k1 + sectors + 1;

            for (int j = 0; j < sectors; ++j, ++k1, ++k2) {
                if (i != 0) {
                    finalVertices.add(vertices.get(k1 * 3));
                    finalVertices.add(vertices.get(k1 * 3 + 1));
                    finalVertices.add(vertices.get(k1 * 3 + 2));

                    finalVertices.add(vertices.get(k2 * 3));
                    finalVertices.add(vertices.get(k2 * 3 + 1));
                    finalVertices.add(vertices.get(k2 * 3 + 2));

                    finalVertices.add(vertices.get((k1 + 1) * 3));
                    finalVertices.add(vertices.get((k1 + 1) * 3 + 1));
                    finalVertices.add(vertices.get((k1 + 1) * 3 + 2));
                }

                if (i != (stacks - 1)) {
                    finalVertices.add(vertices.get((k1 + 1) * 3));
                    finalVertices.add(vertices.get((k1 + 1) * 3 + 1));
                    finalVertices.add(vertices.get((k1 + 1) * 3 + 2));

                    finalVertices.add(vertices.get(k2 * 3));
                    finalVertices.add(vertices.get(k2 * 3 + 1));
                    finalVertices.add(vertices.get(k2 * 3 + 2));

                    finalVertices.add(vertices.get((k2 + 1) * 3));
                    finalVertices.add(vertices.get((k2 + 1) * 3 + 1));
                    finalVertices.add(vertices.get((k2 + 1) * 3 + 2));
                }
            }
        }

        float[] arr = new float[finalVertices.size()];
        for (int i = 0; i < finalVertices.size(); i++) arr[i] = finalVertices.get(i);
        return new PhysicaEntity(arr);
    }

    public static PhysicaEntity createGrid(int size, float step) {
        List<Float> vertices = new ArrayList<>();
        float halfSize = (size * step) / 2.0f;
        for (int i = 0; i <= size; i++) {
            float pos = -halfSize + i * step;
            // Line along Z
            vertices.add(pos); vertices.add(0.0f); vertices.add(-halfSize);
            vertices.add(pos); vertices.add(0.0f); vertices.add(halfSize);
            // Line along X
            vertices.add(-halfSize); vertices.add(0.0f); vertices.add(pos);
            vertices.add(halfSize); vertices.add(0.0f); vertices.add(pos);
        }
        float[] arr = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) arr[i] = vertices.get(i);
        return new PhysicaEntity(arr);
    }
}
