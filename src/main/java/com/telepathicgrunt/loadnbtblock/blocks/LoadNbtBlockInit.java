package com.telepathicgrunt.loadnbtblock.blocks;

import com.telepathicgrunt.loadnbtblock.LoadNbtBlockMain;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class LoadNbtBlockInit {

    public static final Block LOAD_NBT_BLOCK = new com.telepathicgrunt.loadnbtblock.blocks.LoadNbtBlock();
    public static final Item LOAD_NBT_ITEM = new BlockItem(LOAD_NBT_BLOCK, new Item.Settings().group(ItemGroup.REDSTONE));

    public static void initLoadNbtBlock() {
        Registry.register(Registry.BLOCK, new Identifier(LoadNbtBlockMain.MODID, "load_nbt_block"), LOAD_NBT_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(LoadNbtBlockMain.MODID, "load_nbt_block"), LOAD_NBT_ITEM);
    }
}
