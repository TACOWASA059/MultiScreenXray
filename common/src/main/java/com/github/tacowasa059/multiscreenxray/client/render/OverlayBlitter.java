package com.github.tacowasa059.multiscreenxray.client.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/** Draws the transparent vanilla entity, hand, and HUD texture over an X-ray window. */
public final class OverlayBlitter implements AutoCloseable {
    private final int program;
    private final int array;
    private final int buffer;

    public OverlayBlitter() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, "#version 150\n"
                + "in vec2 aPosition; in vec2 aTexCoord; out vec2 vTexCoord;\n"
                + "void main() { vTexCoord = aTexCoord; gl_Position = vec4(aPosition, 0.0, 1.0); }\n");
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, "#version 150\n"
                + "in vec2 vTexCoord; uniform sampler2D uTexture; out vec4 fragColor;\n"
                + "void main() { fragColor = texture(uTexture, vTexCoord); }\n");
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glBindAttribLocation(program, 0, "aPosition");
        GL20.glBindAttribLocation(program, 1, "aTexCoord");
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("X-ray overlay shader link failed: " + GL20.glGetProgramInfoLog(program));
        }
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uTexture"), 0);
        GL20.glUseProgram(0);
        array = GL30.glGenVertexArrays();
        buffer = GL15.glGenBuffers();
        FloatBuffer vertices = BufferUtils.createFloatBuffer(16);
        vertices.put(new float[] {
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                -1f,  1f, 0f, 1f,
                 1f,  1f, 1f, 1f
        }).flip();
        GL30.glBindVertexArray(array);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void draw(int textureId) {
        if (textureId == 0) return;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glUseProgram(program);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL30.glBindVertexArray(array);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GL30.glBindVertexArray(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL20.glUseProgram(0);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void close() {
        GL15.glDeleteBuffers(buffer);
        GL30.glDeleteVertexArrays(array);
        GL20.glDeleteProgram(program);
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("X-ray overlay shader compilation failed: " + log);
        }
        return shader;
    }
}

