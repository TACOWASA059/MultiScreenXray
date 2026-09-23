package com.github.tacowasa059.multiscreenxray.client.render;

import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Compiled block and entity selectors for one window profile. */
public final class ProfileMatcher {
    private final boolean allBlocks;
    private final boolean allEntities;
    private final Set<ResourceLocation> blockIds = new HashSet<>();
    private final Set<ResourceLocation> entityIds = new HashSet<>();
    private final List<TagKey<Block>> blockTags = new ArrayList<>();
    private final List<TagKey<EntityType<?>>> entityTags = new ArrayList<>();

    public ProfileMatcher(XrayProfile profile) {
        allBlocks = compileBlocks(profile.blocks);
        allEntities = compileEntities(profile.entities);
    }

    public boolean matchesBlock(BlockState state) {
        if (allBlocks) return !state.isAir();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockIds.contains(id)) return true;
        for (TagKey<Block> tag : blockTags) if (state.is(tag)) return true;
        return false;
    }

    public boolean matchesEntity(Entity entity) {
        if (allEntities) return true;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityIds.contains(id)) return true;
        for (TagKey<EntityType<?>> tag : entityTags) if (entity.getType().is(tag)) return true;
        return false;
    }

    private boolean compileBlocks(List<String> selectors) {
        boolean all = false;
        for (String selector : selectors) {
            if (selector.equals("*")) all = true;
            else if (selector.startsWith("#"))
                blockTags.add(TagKey.create(Registries.BLOCK, new ResourceLocation(selector.substring(1))));
            else blockIds.add(new ResourceLocation(selector));
        }
        return all;
    }

    private boolean compileEntities(List<String> selectors) {
        boolean all = false;
        for (String selector : selectors) {
            if (selector.equals("*")) all = true;
            else if (selector.startsWith("#"))
                entityTags.add(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(selector.substring(1))));
            else entityIds.add(new ResourceLocation(selector));
        }
        return all;
    }
}
