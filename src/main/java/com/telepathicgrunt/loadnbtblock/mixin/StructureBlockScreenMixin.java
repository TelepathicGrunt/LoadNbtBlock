package com.telepathicgrunt.loadnbtblock.mixin;

import net.minecraft.client.gui.screen.ingame.StructureBlockScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureBlockScreen.class)
public class StructureBlockScreenMixin {
	@ModifyConstant(method = "init", constant = @Constant(intValue = 64))
	private static int makeFileNameLonger(int old) {
		return Integer.MAX_VALUE-1;
	}
}
