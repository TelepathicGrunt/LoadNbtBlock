package com.telepathicgrunt.loadnbtblock.blocks;

import com.telepathicgrunt.loadnbtblock.utils.StructureNbtDataFixer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.StructureBlockBlockEntity;
import net.minecraft.block.enums.StructureBlockMode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoadNbtBlock extends Block {
    public LoadNbtBlock() {
        super(Settings.of(Material.METAL, MaterialColor.LIGHT_GRAY).requiresTool().strength(-1.0F, 3600000.0F).dropsNothing());
    }

    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if(!(world instanceof ServerWorld) || hand == Hand.MAIN_HAND) return ActionResult.PASS;

        String mainPath = FabricLoader.getInstance().getGameDir().toString().replace("\\run\\.", "");
        String resourcePath = mainPath+"\\src\\main\\resources\\data";

        player.sendMessage(new TranslatableText(" Working.... "), true);

        // Finds and gets all identifiers for pieces
        List<File> files = new ArrayList<>();
        List<Identifier> identifiers = new ArrayList<>();
        StructureNbtDataFixer.setAllNbtFilesToList(resourcePath, files);
        for(File file : files){
            String modifiedFileName = file.getAbsolutePath().replace(resourcePath+"\\","").replace("\\structures\\",":").replace(".nbt","").replace('\\','/');
            identifiers.add(new Identifier(modifiedFileName));
        }

        // Size of area we will need
        int columnCount = 7;
        int rowCount = (int) Math.max(Math.ceil(identifiers.size()) / columnCount, 1);
        int spacing = 48;
        BlockPos bounds = new BlockPos(spacing * (rowCount+2), spacing, spacing * columnCount);


        // Fill/clear area with structure void
        BlockPos.Mutable mutableChunk = new BlockPos.Mutable().set(pos.getX() >> 4, pos.getY(), pos.getZ() >> 4);
        BlockPos.Mutable mutableInChunk = new BlockPos.Mutable();
        mutableChunk.move(1,0,0);

        for(; mutableChunk.getX() < (pos.getX() + bounds.getX()) >> 4; mutableChunk.move(1,0,0)) {
            for (; mutableChunk.getZ() < (pos.getZ() + bounds.getZ()) >> 4; mutableChunk.move(0, 0, 1)) {
                WorldChunk chunk = world.getChunk(mutableChunk.getX(), mutableChunk.getZ());
                mutableInChunk.set(0, mutableChunk.getY(), 0);

                for(; mutableInChunk.getX() < 16; mutableInChunk.move(1,0,0)) {
                    for (; mutableInChunk.getY() < mutableChunk.getY() + bounds.getY(); mutableInChunk.move(0, 1, 0)) {
                        for (; mutableInChunk.getZ() < 16; mutableInChunk.move(0, 0, 1)) {

                            if(mutableInChunk.getY() == mutableChunk.getY())
                                chunk.setBlockState(mutableInChunk, Blocks.STONE_SLAB.getDefaultState(), false);
                            else
                                chunk.setBlockState(mutableInChunk, Blocks.STRUCTURE_VOID.getDefaultState(), false);
                        }
                        mutableInChunk.set(mutableInChunk.getX(), mutableInChunk.getY(), 0);
                    }
                    mutableInChunk.set(mutableInChunk.getX(), mutableChunk.getY(), mutableInChunk.getZ());
                }
                chunk.markDirty();
                ((ServerChunkManager) world.getChunkManager()).threadedAnvilChunkStorage.getPlayersWatchingChunk(chunk.getPos(), false).forEach(s -> s.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, 65535)));

                player.sendMessage(new TranslatableText(" Working at " + (mutableChunk.getX() << 4) + ", " + mutableChunk.getY() + ", " + (mutableChunk.getZ() << 4)), true);
            }
            mutableChunk.set(mutableChunk.getX(), mutableChunk.getY(), pos.getZ() >> 4);
        }

        // Places structure blocks and loads pieces
        mutableChunk.set((pos.getX() + 16) ^ 15, pos.getY(), pos.getZ());

        for(int pieceIndex = 1; pieceIndex <= identifiers.size(); pieceIndex++){

            player.sendMessage(new TranslatableText(" Working making structure: "+identifiers.get(pieceIndex-1)), true);

            world.setBlockState(mutableChunk, Blocks.STRUCTURE_BLOCK.getDefaultState().with(StructureBlock.MODE, StructureBlockMode.LOAD), 3);
            BlockEntity be = world.getBlockEntity(mutableChunk);
            if(be instanceof StructureBlockBlockEntity){
                StructureBlockBlockEntity structureBlockBlockEntity = (StructureBlockBlockEntity)be;
                structureBlockBlockEntity.setStructureName(identifiers.get(pieceIndex-1)); // set identifier

                structureBlockBlockEntity.setMode(StructureBlockMode.LOAD);
                structureBlockBlockEntity.loadStructure((ServerWorld) world,false); // load structure

                structureBlockBlockEntity.setMode(StructureBlockMode.SAVE);
                //structureBlockBlockEntity.saveStructure(true); //save structure
                //structureBlockBlockEntity.setShowAir(true);
                structureBlockBlockEntity.setIgnoreEntities(false);
            }

            mutableChunk.move(0,0, spacing);


            // Move back to start of row
            if(pieceIndex % columnCount == 0){
                mutableChunk.move(spacing,0, (-spacing*(columnCount)));
            }
        }

        return ActionResult.SUCCESS;
    }
}
