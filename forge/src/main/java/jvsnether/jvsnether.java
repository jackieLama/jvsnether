package jvsnether;

import jvsnether.worldgen.structure.LavaFloatingJigsawStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(Constants.MOD_ID)
public class jvsnether {

    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_TYPE, Constants.MOD_ID);

    public static final RegistryObject<StructureType<LavaFloatingJigsawStructure>> LAVA_FLOATING_JIGSAW =
        STRUCTURE_TYPES.register("lava_floating_jigsaw", () -> () -> LavaFloatingJigsawStructure.CODEC);

    public jvsnether() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        STRUCTURE_TYPES.register(modBus);
    }
}
