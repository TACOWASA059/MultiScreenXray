package com.github.tacowasa059.multiscreenxray.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.Optional;

/** Composites the transparent vanilla entity, hand, and HUD texture over an X-ray target. */
public final class OverlayBlitter implements AutoCloseable {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("multiscreenxray", "pipeline/overlay_blit"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private final GpuBuffer vertices;
    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("MultiScreen X-ray overlay");

    public OverlayBlitter() {
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(
                DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 6)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            builder.addVertex(-1f, -1f, 0f).setUv(0f, 0f).setColor(-1);
            builder.addVertex(1f, -1f, 0f).setUv(1f, 0f).setColor(-1);
            builder.addVertex(1f, 1f, 0f).setUv(1f, 1f).setColor(-1);
            builder.addVertex(-1f, -1f, 0f).setUv(0f, 0f).setColor(-1);
            builder.addVertex(1f, 1f, 0f).setUv(1f, 1f).setColor(-1);
            builder.addVertex(-1f, 1f, 0f).setUv(0f, 1f).setColor(-1);
            try (MeshData mesh = builder.buildOrThrow()) {
                vertices = RenderSystem.getDevice().createBuffer(
                        () -> "MultiScreen X-ray overlay vertices", GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
            }
        }
    }

    public void draw(RenderTarget target, GpuTextureView texture) {
        if (texture == null || target.getColorTextureView() == null) return;
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f());
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "MultiScreen X-ray overlay composite",
                target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", projection.getBuffer(new Matrix4f()));
            pass.setUniform("DynamicTransforms", transform);
            pass.bindTexture("Sampler0", texture,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setVertexBuffer(0, vertices.slice());
            pass.draw(6, 1, 0, 0);
        }
    }

    @Override
    public void close() {
        vertices.close();
        projection.close();
    }
}
