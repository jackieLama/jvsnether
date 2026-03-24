package jvsnether.worldgen.structure;

import jvsnether.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;

public class ModStructureTypes {

    public static StructureType<LavaFloatingJigsawStructure> LAVA_FLOATING_JIGSAW;

    public static void register() {
        LAVA_FLOATING_JIGSAW = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            new ResourceLocation(Constants.MOD_ID, "lava_floating_jigsaw"),
            () -> LavaFloatingJigsawStructure.CODEC
        );
    }
}
