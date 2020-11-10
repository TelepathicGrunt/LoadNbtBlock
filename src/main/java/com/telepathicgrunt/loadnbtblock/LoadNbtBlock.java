package com.telepathicgrunt.loadnbtblock;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoadNbtBlock implements ModInitializer
{
    public static final String MODID = "loadnbtblock";
    public static final Logger LOGGER = LogManager.getLogger();

    public static final Block LOAD_NBT_BLOCK = new com.telepathicgrunt.loadnbtblock.blocks.LoadNbtBlock();
    public static final Item LOAD_NBT_ITEM = new BlockItem(LOAD_NBT_BLOCK, new Item.Settings().group(ItemGroup.REDSTONE));

    @Override
    public void onInitialize() {
        Registry.register(Registry.BLOCK, new Identifier(MODID, "load_nbt_block"), LOAD_NBT_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(MODID, "load_nbt_block"), LOAD_NBT_ITEM);
    }
}
