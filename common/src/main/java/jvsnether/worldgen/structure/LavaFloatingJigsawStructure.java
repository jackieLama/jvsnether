package jvsnether.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jvsnether.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.Optional;

/**
 * A jigsaw structure variant that snaps to the actual lava surface before placing.
 * Used for piglin ships that should float on Nether lava lakes.
 *
 * The jigsaw connector is expected to be at a corner of the structure.
 * Since Minecraft picks a random rotation (0°, 90°, 180°, 270°), the ship
 * can extend in any of 4 directions from the connector. To guarantee 100%
 * lava coverage under the hull regardless of rotation, all 4 possible
 * footprint rectangles are verified before placement is accepted.
 *
 * JSON fields identical to minecraft:jigsaw, plus two required extras:
 *   "footprint_x" — structure width  (e.g. 11 for piglin_ship)
 *   "footprint_z" — structure length (e.g. 36 for piglin_ship)
 */
public class LavaFloatingJigsawStructure extends Structure {

    public static final Codec<LavaFloatingJigsawStructure> CODEC = RecordCodecBuilder.<LavaFloatingJigsawStructure>mapCodec(instance ->
        instance.group(
            Structure.settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
            Codec.intRange(0, 7).fieldOf("size").forGetter(s -> s.maxDepth),
            HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
            Codec.BOOL.optionalFieldOf("use_expansion_hack", false).forGetter(s -> s.useExpansionHack),
            Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
            Codec.intRange(1, 128).fieldOf("footprint_x").forGetter(s -> s.footprintX),
            Codec.intRange(1, 128).fieldOf("footprint_z").forGetter(s -> s.footprintZ),
            Codec.intRange(0, 16).optionalFieldOf("lava_y_offset", 2).forGetter(s -> s.lavaYOffset)
        ).apply(instance, LavaFloatingJigsawStructure::new)
    ).codec();

    // Vertical scan range — wide enough to catch lava pockets above and below sea level
    private static final int SCAN_TOP = 60;
    private static final int SCAN_BOTTOM = 10;

    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final int footprintX;
    private final int footprintZ;
    private final int lavaYOffset;

    public LavaFloatingJigsawStructure(
            Structure.StructureSettings settings,
            Holder<StructureTemplatePool> startPool,
            Optional<ResourceLocation> startJigsawName,
            int maxDepth,
            HeightProvider startHeight,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            int footprintX,
            int footprintZ,
            int lavaYOffset) {
        super(settings);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.footprintX = footprintX;
        this.footprintZ = footprintZ;
        this.lavaYOffset = lavaYOffset;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();

        int lavaY = findLavaSurfaceY(context, x, z);
        if (lavaY == -1) {
            return Optional.empty();
        }

        if (!hasLavaForAllRotations(context, x, z, lavaY - 1)) {
            return Optional.empty();
        }

        return JigsawPlacement.addPieces(
            context,
            this.startPool,
            this.startJigsawName,
            this.maxDepth,
            new BlockPos(x, lavaY + this.lavaYOffset, z),
            this.useExpansionHack,
            this.projectStartToHeightmap,
            this.maxDistanceFromCenter
        );
    }

    /**
     * Scans the noise column downward to find the top lava block.
     * Returns the Y of that lava block, or -1 if no lava found.
     */
    private int findLavaSurfaceY(Structure.GenerationContext context, int x, int z) {
        NoiseColumn column = context.chunkGenerator().getBaseColumn(
            x, z, context.heightAccessor(), context.randomState()
        );

        for (int y = SCAN_TOP; y >= SCAN_BOTTOM; y--) {
            if (column.getBlock(y).is(Blocks.LAVA)) {
                return y;
            }
        }

        return -1;
    }

    /**
     * Verifies that all 4 possible rotations of the ship footprint are 100% lava.
     *
     * Since the jigsaw connector is at a corner of the structure and Minecraft picks
     * a random rotation, the ship can extend in any of 4 directions. Each rotation
     * produces a different footprint rectangle from the connector point:
     *
     *   NONE:    (x, z)   → (x+W, z+L)
     *   CW_90:   (x, z-W) → (x+L, z)
     *   CW_180:  (x-W, z-L) → (x, z)
     *   CCW_90:  (x-L, z) → (x, z+W)
     *
     * All 4 must be clear lava so that whatever rotation is chosen, the hull sits
     * entirely over open lava.
     */
    private boolean hasLavaForAllRotations(Structure.GenerationContext context, int x, int z, int lavaLevel) {
        int w = this.footprintX;
        int l = this.footprintZ;

        return checkRectangle(context, x,     z,     x + w, z + l, lavaLevel)  // NONE
            && checkRectangle(context, x,     z - w, x + l, z,     lavaLevel)  // CW_90
            && checkRectangle(context, x - w, z - l, x,     z,     lavaLevel)  // CW_180
            && checkRectangle(context, x - l, z,     x,     z + w, lavaLevel); // CCW_90
    }

    /**
     * Returns true if every block at lavaLevel within the given XZ rectangle is lava.
     * Exits immediately on the first non-lava block found.
     */
    private boolean checkRectangle(
            Structure.GenerationContext context,
            int x1, int z1, int x2, int z2, int lavaLevel) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                NoiseColumn col = context.chunkGenerator().getBaseColumn(
                    x, z, context.heightAccessor(), context.randomState()
                );
                if (!col.getBlock(lavaLevel).is(Blocks.LAVA)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public StructureType<?> type() {
        return BuiltInRegistries.STRUCTURE_TYPE.get(new ResourceLocation(Constants.MOD_ID, "lava_floating_jigsaw"));
    }
}
