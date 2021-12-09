package com.telepathicgrunt.loadnbtblock;

import com.telepathicgrunt.loadnbtblock.blocks.LoadNbtBlockInit;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoadNbtBlockMain implements ModInitializer {
    public static final String MODID = "load_nbt_block";
    public static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        LoadNbtBlockInit.initLoadNbtBlock();
    }
}
