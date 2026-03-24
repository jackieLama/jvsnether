package jvsnether;

import jvsnether.worldgen.structure.ModStructureTypes;
import net.fabricmc.api.ModInitializer;

public class jvsnether implements ModInitializer {

    @Override
    public void onInitialize() {
        ModStructureTypes.register();
    }
}
