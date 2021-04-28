package com.telepathicgrunt.loadnbtblock.mixins;

import net.minecraft.resource.ResourceManager;
import net.minecraft.structure.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(StructureManager.class)
public interface StructureManagerAccessor {
    @Accessor("field_25189")
    ResourceManager lnbtb_getField_25189();
}
