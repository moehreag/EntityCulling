package dev.tr7zw.entityculling;

import com.logisticscraft.occlusionculling.DataProvider;

import dev.tr7zw.entityculling.mixin.MinecraftAccessor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

public class Provider implements DataProvider {

    private final Minecraft client = MinecraftAccessor.getInstance();
    private World world = null;

    @Override
    public boolean prepareChunk(int chunkX, int chunkZ) {
        world = client.world;
        return world != null;
    }

    @Override
    public boolean isOpaqueFullCube(int x, int y, int z) {
        int blockId = world.getBlock(x, y, z);
        return Block.IS_OPAQUE[blockId] || (EntityCullingMod.instance.config.glassCulls && blockId == Block.GLASS.id);
    }

    @Override
    public void cleanup() {
        world = null;
    }

}
