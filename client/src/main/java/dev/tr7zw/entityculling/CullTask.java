package dev.tr7zw.entityculling;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map.Entry;

import com.logisticscraft.occlusionculling.OcclusionCullingInstance;
import com.logisticscraft.occlusionculling.util.Vec3d;

import dev.tr7zw.entityculling.access.Cullable;
import dev.tr7zw.entityculling.mixin.MinecraftAccessor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.InputPlayerEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.WorldChunk;

@SuppressWarnings("unchecked")
public class CullTask implements Runnable {

    public boolean requestCull = false;

    private final OcclusionCullingInstance culling;
    private final Minecraft client = MinecraftAccessor.getInstance();
    private final int sleepDelay = 10; //EntityCullingModBase.instance.config.sleepDelay;
    private final int hitboxLimit =50; // EntityCullingModBase.instance.config.hitboxLimit;
    //private final Set<BlockEntityType<?>> blockEntityWhitelist;
    //private final Set<EntityType<?>> entityWhistelist;
    public long lastTime = 0;

    // reused preallocated vars
    private final Vec3d lastPos = new Vec3d(0, 0, 0);
    private final Vec3d aabbMin = new Vec3d(0, 0, 0);
    private final Vec3d aabbMax = new Vec3d(0, 0, 0);

    public CullTask(OcclusionCullingInstance culling) {
        this.culling = culling;
        //this.blockEntityWhitelist = blockEntityWhitelist;
        //this.entityWhistelist = entityWhistelist;
    }

    @Override
    public void run() {
        System.out.println("We in the cull task!!");
        while (client.running) { // client.isRunning() returns false at the start?!?
            try {
                Thread.sleep(sleepDelay);
                if (EntityCullingMod.enabled && client.world != null && client.player != null
                        && client.player.time > 10) {
                    net.minecraft.util.math.Vec3d cameraMC = /*false *//*EntityCullingModBase.instance.config.debugMode*//*
                            ? client.player.
                            : client.worldRenderer.field_1795.getPosition()*/ real(client.player);

                    if (requestCull
                            || !(cameraMC.x == lastPos.x && cameraMC.y == lastPos.y && cameraMC.z == lastPos.z)) {
                        long start = System.currentTimeMillis();
                        requestCull = false;
                        lastPos.set(cameraMC.x, cameraMC.y, cameraMC.z);
                        Vec3d camera = lastPos;
                        culling.resetCache();
                        if (!EntityCullingMod.instance.config.disableBlockEntityCulling) cullBlockEntities(cameraMC, camera);
                        if (!EntityCullingMod.instance.config.disableEntityCulling) cullEntities(cameraMC, camera);
                        lastTime = (System.currentTimeMillis() - start);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("Shutting down culling task!");
    }

    private void cullEntities(net.minecraft.util.math.Vec3d cameraMC, Vec3d camera) {
        Entity entity;
        Iterator<?> iterable = client.world.getEntities().iterator();
        while (iterable.hasNext()) {
            try {
                entity = (Entity) iterable.next();
            } catch (NullPointerException | ConcurrentModificationException ex) {
                break; // We are not synced to the main thread, so NPE's/CME are allowed here and way
                       // less
                       // overhead probably than trying to sync stuff up for no really good reason
            }
            if (!(entity instanceof Cullable)) {
                continue; // Not sure how this could happen outside from mixin screwing up the inject into
                          // Entity
            }
            Cullable cullable = (Cullable) entity;
            if (EntityCullingMod.instance.isDynamicWhitelisted(entity)) {
                continue;
            }
            if (!cullable.isForcedVisible()) {
                if (!isInRange(getPos(entity), cameraMC, 128 /*EntityCullingModBase.instance.config.tracingDistance*/)) {
                    cullable.setCulled(false); // If your entity view distance is larger than tracingDistance just
                                               // render it
                    continue;
                }
                Box boundingBox = entity.shape;
                idk = false;
                idk(camera, cullable, boundingBox);
            }
        }
    }

    private void cullBlockEntities(net.minecraft.util.math.Vec3d cameraMC, Vec3d camera) {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                WorldChunk chunk = client.world.getChunkAt(client.player.chunkX + x, client.player.chunkZ + z);
                Iterator<?> iterator = chunk.blockEntities.entrySet().iterator();
                Entry<Vec3i, BlockEntity> entry;
                while (iterator.hasNext()) {
                    try {
                        entry = (Entry<Vec3i, BlockEntity>) iterator.next();
                    } catch (NullPointerException | ConcurrentModificationException ex) {
                        break; // We are not synced to the main thread, so NPE's/CME are allowed here and way
                               // less
                        // overhead probably than trying to sync stuff up for no really good reason
                    }
                    /*if (blockEntityWhitelist.contains(entry.getValue().getType())) {
                        continue;
                    }*/
                    if (EntityCullingMod.instance.isDynamicWhitelisted(entry.getValue())) {
                        continue;
                    }
                    Cullable cullable = (Cullable) entry.getValue();
                    if (!cullable.isForcedVisible()) {
                        Vec3i key = entry.getKey();
                        BlockPos pos = new BlockPos(key.x, key.y, key.z);

                        if (!BlockEntityRenderDispatcher.INSTANCE.hasRenderer(entry.getValue())) continue;
                        //if (idk) System.out.println("sup");
                        if (closerThan(pos, cameraMC, 64)) { // 64 is the fixed max tile view distance
                            //if (idk) System.out.println("I am going insane");
                            Box boundingBox = EntityCullingMod.instance.setupBox(entry.getValue(), pos);
                            idk(camera, cullable, boundingBox);
                        }
                    }
                }

            }
        }
    }

    boolean idk = false;

    private void idk(Vec3d camera, Cullable cullable, Box boundingBox) {
        if (Math.abs(boundingBox.maxX - boundingBox.minX) > hitboxLimit || Math.abs(boundingBox.maxY - boundingBox.minY) > hitboxLimit
                || Math.abs(boundingBox.maxZ - boundingBox.minZ) > hitboxLimit) {
            //if (idk) System.out.println("too big");
            cullable.setCulled(false); // To big to bother to cull
            return;
        }
        aabbMin.set(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
        aabbMax.set(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
        //System.out.println(boundingBox);
        boolean visible = culling.isAABBVisible(aabbMin, aabbMax, camera);
        //if (!visible && idk) System.out.println("not visible!!!!");
        cullable.setCulled(!visible);
    }

    private net.minecraft.util.math.Vec3d real(InputPlayerEntity player) {
        float f = ((MinecraftAccessor) MinecraftAccessor.getInstance()).getTimer().f_1387123;
        double d = player.prevTickX + (player.x - player.prevTickX) * (double)f;
        double d2 = player.prevTickY + (player.y - player.prevTickY) * (double)f;
        double d3 = player.prevTickZ + (player.z - player.prevTickZ) * (double)f;
        return net.minecraft.util.math.Vec3d.of(d, d2, d3);
    }

    public static net.minecraft.util.math.Vec3d getPos(Entity entity) {
        return net.minecraft.util.math.Vec3d.of(entity.x, entity.y, entity.z);
    }

    public static boolean isInRange(net.minecraft.util.math.Vec3d vec3d, net.minecraft.util.math.Vec3d pos, int maxDist) {
        return vec3d.squaredDistanceTo(pos) < maxDist*maxDist*maxDist;
    }

    // Vec3i forward compatibility functions
    @SuppressWarnings("SameParameterValue")
    private static boolean closerThan(BlockPos blockPos, net.minecraft.util.math.Vec3d position, double d) {
        return distSqr(blockPos, position.x, position.y, position.z, true) < d * d*d;
    }

    @SuppressWarnings("SameParameterValue")
    private static double distSqr(BlockPos blockPos, double d, double e, double f, boolean bl) {
        double g = bl ? 0.5D : 0.0D;
        double h = (double) blockPos.x + g - d;
        double i = (double) blockPos.y + g - e;
        double j = (double) blockPos.z + g - f;
        return h * h + i * i + j * j;
    }

    private static net.minecraft.util.math.Vec3d lookVector(LivingEntity entity) {
        float f2 = MathHelper.cos(-entity.yaw * ((float)Math.PI / 180) - (float)Math.PI);
        float f3 = MathHelper.sin(-entity.yaw * ((float)Math.PI / 180) - (float)Math.PI);
        float f4 = -MathHelper.cos(-entity.pitch * ((float)Math.PI / 180));
        float f5 = MathHelper.sin(-entity.pitch * ((float)Math.PI / 180));
        return net.minecraft.util.math.Vec3d.of(f3 * f4, f5, f2 * f4);
    }
}
