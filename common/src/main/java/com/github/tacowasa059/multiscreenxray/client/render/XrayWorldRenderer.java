package com.github.tacowasa059.multiscreenxray.client.render;

import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector3fc;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/** Draws client-known ore blocks through terrain in the player's camera. */
public final class XrayWorldRenderer implements AutoCloseable {
    private static final RenderPipeline COLOR_LINES = colorPipeline("xray_lines", PrimitiveTopology.DEBUG_LINES);
    private static final RenderPipeline COLOR_TRIANGLES = colorPipeline("xray_triangles", PrimitiveTopology.TRIANGLES);
    private static final RenderPipeline ORE_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("multiscreenxray", "pipeline/xray_ores"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(true)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();
    private static final int OUTLINE_RADIUS = 32;
    private static final int ORE_BORDER_RADIUS = 32;
    private static final int MAX_ORES = 15000;
    private static final long REFRESH_NANOS = 30_000_000_000L;
    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    // One bit per cube edge. Adjacent exposed faces share an edge, so draw it once.
    private static final int[] FACE_EDGE_MASKS = {
            0x00F, 0x0F0, 0x311, 0xC44, 0x588, 0xA22
    };

    private GpuBuffer vertexBuffer;
    private GpuBuffer outlineBuffer;
    private GpuBuffer oreBorderBuffer;
    private GpuBuffer fluidBuffer;
    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("MultiScreen X-ray");
    private final ArrayDeque<ChunkPosition> pending = new ArrayDeque<>();
    private final List<OrePosition> ores = new ArrayList<>();
    private final Map<Block, Integer> classifications = new IdentityHashMap<>();
    private final XrayProfile profile;
    private final ProfileMatcher matcher;
    private final int chunkRadius;
    private final int verticalRadius;
    private ClientLevel level;
    private int scanChunkX = Integer.MIN_VALUE;
    private int scanChunkZ;
    private int scanBlockY;
    private int originX;
    private int originY;
    private int originZ;
    private int vertexCount;
    private int visibleOreCount;
    private int outlineVertexCount;
    private int oreBorderVertexCount;
    private int fluidVertexCount;
    private int outlineX = Integer.MIN_VALUE;
    private int outlineY;
    private int outlineZ;
    private int scannedSinceUpload;
    private boolean snapshotReady;
    private long scanStartedAt;
    private Minecraft minecraft;

    public XrayWorldRenderer(XrayProfile profile, int chunkRadius, int verticalRadius) {
        this.profile = profile.copy();
        this.matcher = new ProfileMatcher(this.profile);
        this.chunkRadius = chunkRadius;
        this.verticalRadius = verticalRadius;
    }

    public int oreCount() {
        return visibleOreCount;
    }

    public int remainingChunks() {
        return pending.size();
    }

    public boolean initialScanInProgress() {
        return !snapshotReady && !pending.isEmpty();
    }

    public void render(Minecraft minecraft, RenderTarget target, float fieldOfView) {
        this.minecraft = minecraft;
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (minecraft.level == null || !camera.isInitialized()) {
            return;
        }
        Vec3 position = camera.position();
        int chunkX = SectionPos.blockToSectionCoord(position.x);
        int chunkZ = SectionPos.blockToSectionCoord(position.z);
        int blockY = (int) Math.floor(position.y);
        if (level != minecraft.level || pending.isEmpty()
                && (chunkX != scanChunkX || chunkZ != scanChunkZ
                    || Math.abs(blockY - scanBlockY) > 24
                    || System.nanoTime() - scanStartedAt > REFRESH_NANOS)) {
            beginScan(minecraft.level, position, chunkX, chunkZ, blockY);
        }
        scanBatch();
        int blockX = (int) Math.floor(position.x);
        int blockZ = (int) Math.floor(position.z);
        if (snapshotReady && (Math.abs(blockX - outlineX) >= 8 || Math.abs(blockY - outlineY) >= 8
                || Math.abs(blockZ - outlineZ) >= 8)) {
            rebuildOutlines(blockX, blockY, blockZ);
        }

        if (target.getColorTextureView() == null || target.getDepthTextureView() == null) return;
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), new Vector4f(0.075f, 0.09f, 0.13f, 1.0f),
                target.getDepthTexture(), 0.0);
        Matrix4f projectionMatrix = new Matrix4f()
                .perspective((float) Math.toRadians(fieldOfView), (float) target.width / target.height,
                        256f, 0.05f, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        Matrix4f viewMatrix = new Matrix4f()
                .rotateX((float) Math.toRadians(camera.xRot()))
                .rotateY((float) Math.toRadians(camera.yRot() + 180f))
                .translate(-(float) (position.x - originX),
                        -(float) (position.y - originY),
                        -(float) (position.z - originZ));
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(viewMatrix);
        GpuBufferSlice oreTransform = RenderSystem.getDynamicUniforms().writeTransform(
                viewMatrix, new Vector4f(profile.brightness, profile.brightness, profile.brightness, 1.0f));
        GpuBufferSlice projectionBuffer = projection.getBuffer(projectionMatrix);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "MultiScreen X-ray world", target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            if (profile.showOutlines && outlineBuffer != null && outlineVertexCount > 0) {
                bindCommon(pass, COLOR_LINES, transform, projectionBuffer);
                pass.setVertexBuffer(0, outlineBuffer.slice());
                pass.draw(outlineVertexCount, 1, 0, 0);
            }
            if (vertexBuffer != null && vertexCount > 0) {
                bindCommon(pass, ORE_PIPELINE, oreTransform, projectionBuffer);
                var atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
                pass.setUniform("Sampler0", atlas.getTextureView(), atlas.getSampler());
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.draw(vertexCount, 1, 0, 0);
            }
            if (oreBorderBuffer != null && oreBorderVertexCount > 0) {
                bindCommon(pass, COLOR_LINES, transform, projectionBuffer);
                pass.setVertexBuffer(0, oreBorderBuffer.slice());
                pass.draw(oreBorderVertexCount, 1, 0, 0);
            }
            if (profile.showFluids && fluidBuffer != null && fluidVertexCount > 0) {
                bindCommon(pass, COLOR_TRIANGLES, transform, projectionBuffer);
                pass.setVertexBuffer(0, fluidBuffer.slice());
                pass.draw(fluidVertexCount, 1, 0, 0);
            }
        }
    }

    private void beginScan(ClientLevel nextLevel, Vec3 position, int chunkX, int chunkZ, int blockY) {
        boolean changedLevel = level != nextLevel;
        level = nextLevel;
        scanChunkX = chunkX;
        scanChunkZ = chunkZ;
        scanBlockY = blockY;
        ores.clear();
        if (changedLevel) {
            snapshotReady = false;
            originX = chunkX * 16;
            originY = blockY;
            originZ = chunkZ * 16;
            outlineX = Integer.MIN_VALUE;
            outlineVertexCount = 0;
            fluidVertexCount = 0;
            upload();
        }
        scanStartedAt = System.nanoTime();
        pending.clear();
        List<ChunkPosition> candidates = new ArrayList<>();
        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                if (dx * dx + dz * dz <= chunkRadius * chunkRadius) {
                    candidates.add(new ChunkPosition(chunkX + dx, chunkZ + dz,
                            dx * dx + dz * dz));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(ChunkPosition::distanceSquared));
        pending.addAll(candidates);
        scannedSinceUpload = 0;
    }

    private void scanBatch() {
        for (int i = 0; i < 3 && !pending.isEmpty() && ores.size() < MAX_ORES; i++) {
            ChunkPosition chunkPosition = pending.removeFirst();
            LevelChunk chunk = level.getChunkSource().getChunk(
                    chunkPosition.x(), chunkPosition.z(), ChunkStatus.FULL, false);
            if (chunk != null) {
                scanChunk(chunk);
            }
            scannedSinceUpload++;
        }
        if (ores.size() >= MAX_ORES) {
            pending.clear();
        }
        if (scannedSinceUpload > 0 && pending.isEmpty()) {
            upload();
            scannedSinceUpload = 0;
            if (pending.isEmpty()) {
                snapshotReady = true;
            }
        }
    }

    private void scanChunk(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length && ores.size() < MAX_ORES; index++) {
            int sectionBaseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            if (sectionBaseY > scanBlockY + verticalRadius
                    || sectionBaseY + 15 < scanBlockY - verticalRadius) {
                continue;
            }
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(this::isOre)) {
                continue;
            }
            for (int y = 0; y < 16 && ores.size() < MAX_ORES; y++) {
                for (int z = 0; z < 16 && ores.size() < MAX_ORES; z++) {
                    for (int x = 0; x < 16 && ores.size() < MAX_ORES; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isOre(state)) {
                            ores.add(new OrePosition((chunk.getPos().x() << 4) + x,
                                    sectionBaseY + y, (chunk.getPos().z() << 4) + z, state));
                        }
                    }
                }
            }
        }
    }

    private boolean isOre(BlockState state) {
        return matcher.matchesBlock(state);
    }

    private int classify(Block block) {
        return classifications.computeIfAbsent(block, candidate -> {
            String path = BuiltInRegistries.BLOCK.getKey(candidate).getPath();
            if (path.contains("diamond_ore")) return 0x40EDFF;
            if (path.contains("emerald_ore")) return 0x43FF70;
            if (path.contains("redstone_ore")) return 0xFF4242;
            if (path.contains("lapis_ore")) return 0x526FFF;
            if (path.contains("gold_ore")) return 0xFFCC3D;
            if (path.contains("iron_ore")) return 0xFFAE6A;
            if (path.contains("copper_ore")) return 0xE67E50;
            if (path.contains("coal_ore")) return 0xC7D2DC;
            if (path.contains("quartz_ore")) return 0xFAEDDE;
            if (path.equals("ancient_debris")) return 0xC69475;
            return path.endsWith("_ore") ? 0xC36BFF : 0x77DDFF;
        });
    }

    private void upload() {
        FloatCollector vertices = new FloatCollector();
        FloatCollector borders = new FloatCollector();
        for (OrePosition ore : ores) {
            appendModel(vertices, ore);
            int dx = ore.x() - originX - 8;
            int dy = ore.y() - originY;
            int dz = ore.z() - originZ - 8;
            if (dx * dx + dy * dy + dz * dz <= ORE_BORDER_RADIUS * ORE_BORDER_RADIUS) {
                appendOreBorder(borders, ore);
            }
        }
        vertexCount = vertices.size / 5;
        visibleOreCount = ores.size();
        vertexBuffer = buildOreBuffer(vertices, vertexBuffer);
        oreBorderVertexCount = borders.size / 7;
        oreBorderBuffer = buildColorBuffer(borders, oreBorderBuffer,
                "MultiScreen X-ray ore borders");
    }

    private void appendOreBorder(FloatCollector vertices, OrePosition ore) {
        int color = classify(ore.state().getBlock());
        float red = ((color >>> 16) & 255) / 255f;
        float green = ((color >>> 8) & 255) / 255f;
        float blue = (color & 255) / 255f;
        int x = ore.x() - originX;
        int y = ore.y() - originY;
        int z = ore.z() - originZ;
        for (int[] edge : EDGES) {
            appendBorderVertex(vertices, x, y, z, edge[0], red, green, blue);
            appendBorderVertex(vertices, x, y, z, edge[1], red, green, blue);
        }
    }

    private static void appendBorderVertex(FloatCollector vertices, int x, int y, int z, int corner,
                                           float red, float green, float blue) {
        vertices.add(x + (corner & 1));
        vertices.add(y + ((corner >>> 1) & 1));
        vertices.add(z + ((corner >>> 2) & 1));
        vertices.add(red * 0.75f);
        vertices.add(green * 0.75f);
        vertices.add(blue * 0.75f);
        vertices.add(0.55f);
    }

    private void appendModel(FloatCollector vertices, OrePosition ore) {
        BlockPos position = new BlockPos(ore.x(), ore.y(), ore.z());
        BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(ore.state());
        RandomSource random = RandomSource.create();
        long seed = ore.state().getSeed(position);
        random.setSeed(seed);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (BlockStateModelPart part : parts) {
            appendQuads(vertices, part.getQuads(null), ore);
            for (Direction direction : Direction.values()) {
                appendQuads(vertices, part.getQuads(direction), ore);
            }
        }
    }

    private void appendQuads(FloatCollector vertices, Iterable<BakedQuad> quads, OrePosition ore) {
        for (BakedQuad quad : quads) {
            appendOreVertex(vertices, quad, 0, ore);
            appendOreVertex(vertices, quad, 1, ore);
            appendOreVertex(vertices, quad, 2, ore);
            appendOreVertex(vertices, quad, 0, ore);
            appendOreVertex(vertices, quad, 2, ore);
            appendOreVertex(vertices, quad, 3, ore);
        }
    }

    private void appendOreVertex(FloatCollector vertices, BakedQuad quad, int vertex, OrePosition ore) {
        Vector3fc position = quad.position(vertex);
        long packedUv = quad.packedUV(vertex);
        vertices.add(position.x() + ore.x() - originX);
        vertices.add(position.y() + ore.y() - originY);
        vertices.add(position.z() + ore.z() - originZ);
        vertices.add(UVPair.unpackU(packedUv));
        vertices.add(UVPair.unpackV(packedUv));
    }

    private void rebuildOutlines(int cameraX, int cameraY, int cameraZ) {
        outlineX = cameraX;
        outlineY = cameraY;
        outlineZ = cameraZ;
        FloatCollector vertices = new FloatCollector();
        FloatCollector fluidVertices = new FloatCollector();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (int y = cameraY - OUTLINE_RADIUS; y <= cameraY + OUTLINE_RADIUS; y++) {
            for (int z = cameraZ - OUTLINE_RADIUS; z <= cameraZ + OUTLINE_RADIUS; z++) {
                for (int x = cameraX - OUTLINE_RADIUS; x <= cameraX + OUTLINE_RADIUS; x++) {
                    int dx = x - cameraX;
                    int dy = y - cameraY;
                    int dz = z - cameraZ;
                    if (dx * dx + dy * dy + dz * dz > OUTLINE_RADIUS * OUTLINE_RADIUS) {
                        continue;
                    }
                    position.set(x, y, z);
                    if (!level.hasChunkAt(position)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(position);
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        appendFluid(fluidVertices, position, neighbor, fluid);
                        continue;
                    }
                    if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || isOre(state)) {
                        continue;
                    }
                    if (state.isSolidRender()) {
                        appendExposedOutline(vertices, position, neighbor,
                                x - originX, y - originY, z - originZ);
                    }
                }
            }
        }
        outlineVertexCount = vertices.size / 7;
        outlineBuffer = buildColorBuffer(vertices, outlineBuffer,
                "MultiScreen X-ray outlines");
        fluidVertexCount = fluidVertices.size / 7;
        fluidBuffer = buildColorBuffer(fluidVertices, fluidBuffer,
                "MultiScreen X-ray fluids");
    }

    private void appendFluid(FloatCollector vertices, BlockPos position, BlockPos.MutableBlockPos neighbor,
                             FluidState fluid) {
        boolean water = fluid.is(FluidTags.WATER);
        boolean lava = fluid.is(FluidTags.LAVA);
        if (!water && !lava) {
            return;
        }
        float x = position.getX() - originX;
        float y = position.getY() - originY;
        float z = position.getZ() - originZ;
        float top = y + Math.max(0.15f, fluid.getHeight(level, position));
        float red = water ? 0.25f : 1.0f;
        float green = water ? 0.72f : 0.52f;
        float blue = water ? 1.0f : 0.12f;
        float alpha = water ? 0.24f : 0.50f;
        neighbor.setWithOffset(position, Direction.UP);
        if (level.getFluidState(neighbor).getType() != fluid.getType()) {
            appendColoredQuad(vertices, x, top, z, x + 1, top, z,
                    x + 1, top, z + 1, x, top, z + 1, red, green, blue, alpha);
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            neighbor.setWithOffset(position, direction);
            if (level.getFluidState(neighbor).getType() == fluid.getType()) {
                continue;
            }
            switch (direction) {
                case NORTH -> appendColoredQuad(vertices, x, y, z, x + 1, y, z,
                        x + 1, top, z, x, top, z, red, green, blue, alpha);
                case SOUTH -> appendColoredQuad(vertices, x, y, z + 1, x + 1, y, z + 1,
                        x + 1, top, z + 1, x, top, z + 1, red, green, blue, alpha);
                case WEST -> appendColoredQuad(vertices, x, y, z, x, y, z + 1,
                        x, top, z + 1, x, top, z, red, green, blue, alpha);
                case EAST -> appendColoredQuad(vertices, x + 1, y, z, x + 1, y, z + 1,
                        x + 1, top, z + 1, x + 1, top, z, red, green, blue, alpha);
                default -> { }
            }
        }
    }

    private static void appendColoredQuad(FloatCollector vertices,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1,
                                          float x2, float y2, float z2,
                                          float x3, float y3, float z3,
                                          float red, float green, float blue, float alpha) {
        appendColoredVertex(vertices, x0, y0, z0, red, green, blue, alpha);
        appendColoredVertex(vertices, x1, y1, z1, red, green, blue, alpha);
        appendColoredVertex(vertices, x2, y2, z2, red, green, blue, alpha);
        appendColoredVertex(vertices, x0, y0, z0, red, green, blue, alpha);
        appendColoredVertex(vertices, x2, y2, z2, red, green, blue, alpha);
        appendColoredVertex(vertices, x3, y3, z3, red, green, blue, alpha);
    }

    private static void appendColoredVertex(FloatCollector vertices, float x, float y, float z,
                                            float red, float green, float blue, float alpha) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(red);
        vertices.add(green);
        vertices.add(blue);
        vertices.add(alpha);
    }

    private void appendExposedOutline(FloatCollector vertices, BlockPos position,
                                  BlockPos.MutableBlockPos neighbor, int x, int y, int z) {
        int openFaces = 0;
        for (Direction direction : Direction.values()) {
            neighbor.setWithOffset(position, direction);
            if (!level.hasChunkAt(neighbor)) {
                openFaces |= 1 << direction.ordinal();
            } else {
                BlockState adjacent = level.getBlockState(neighbor);
                if (adjacent.isAir() || !adjacent.getFluidState().isEmpty() || isOre(adjacent)) {
                    openFaces |= 1 << direction.ordinal();
                }
            }
        }
        if (openFaces == 0) {
            return;
        }
        int edges = 0;
        for (Direction direction : Direction.values()) {
            if ((openFaces & 1 << direction.ordinal()) != 0) {
                edges |= FACE_EDGE_MASKS[direction.ordinal()];
            }
        }
        for (int edge = 0; edge < EDGES.length; edge++) {
            if ((edges & 1 << edge) != 0) {
                appendOutlineVertex(vertices, x, y, z, EDGES[edge][0]);
                appendOutlineVertex(vertices, x, y, z, EDGES[edge][1]);
            }
        }
    }

    private void appendOutlineVertex(FloatCollector vertices, int x, int y, int z, int corner) {
        vertices.add(x + (corner & 1));
        vertices.add(y + ((corner >>> 1) & 1));
        vertices.add(z + ((corner >>> 2) & 1));
        vertices.add(0.78f);
        vertices.add(0.9f);
        vertices.add(1.0f);
        vertices.add(profile.outlineAlpha);
    }

    private static RenderPipeline colorPipeline(String name, PrimitiveTopology topology) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("multiscreenxray", "pipeline/" + name))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(topology)
                .build();
    }

    private void bindCommon(RenderPass pass, RenderPipeline pipeline, GpuBufferSlice transform,
                            GpuBufferSlice projectionBuffer) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("Projection", projectionBuffer);
        pass.setUniform("DynamicTransforms", transform);
    }

    private static GpuBuffer buildOreBuffer(FloatCollector data, GpuBuffer previous) {
        closeBuffer(previous);
        if (data.size == 0) return null;
        int count = data.size / 5;
        ByteBuffer bytes = BufferUtils.createByteBuffer(
                count * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
        for (int i = 0; i < data.size; i += 5) {
            bytes.putFloat(data.values[i]).putFloat(data.values[i + 1]).putFloat(data.values[i + 2]);
            bytes.putFloat(data.values[i + 3]).putFloat(data.values[i + 4]);
            bytes.putInt(-1);
        }
        bytes.flip();
        return RenderSystem.getDevice().createBuffer(() -> "MultiScreen X-ray ores",
                GpuBuffer.USAGE_VERTEX, bytes);
    }

    private static GpuBuffer buildColorBuffer(FloatCollector data, GpuBuffer previous, String label) {
        closeBuffer(previous);
        if (data.size == 0) return null;
        int count = data.size / 7;
        ByteBuffer bytes = BufferUtils.createByteBuffer(
                count * DefaultVertexFormat.POSITION_COLOR.getVertexSize());
        for (int i = 0; i < data.size; i += 7) {
            bytes.putFloat(data.values[i]).putFloat(data.values[i + 1]).putFloat(data.values[i + 2]);
            bytes.put(colorByte(data.values[i + 3]));
            bytes.put(colorByte(data.values[i + 4]));
            bytes.put(colorByte(data.values[i + 5]));
            bytes.put(colorByte(data.values[i + 6]));
        }
        bytes.flip();
        return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, bytes);
    }

    private static byte colorByte(float value) {
        return (byte) Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    private static void closeBuffer(GpuBuffer buffer) {
        if (buffer != null) buffer.close();
    }

    @Override
    public void close() {
        closeBuffer(vertexBuffer);
        closeBuffer(outlineBuffer);
        closeBuffer(oreBorderBuffer);
        closeBuffer(fluidBuffer);
        projection.close();
    }

    private record ChunkPosition(int x, int z, int distanceSquared) { }
    private record OrePosition(int x, int y, int z, BlockState state) { }

    private static final class FloatCollector {
        private float[] values = new float[8192];
        private int size;

        private void add(float value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }
    }
}
