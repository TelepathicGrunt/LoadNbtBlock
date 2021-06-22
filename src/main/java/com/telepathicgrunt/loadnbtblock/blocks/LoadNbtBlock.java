package com.telepathicgrunt.loadnbtblock.blocks;

import com.telepathicgrunt.loadnbtblock.utils.StructureNbtDataFixer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.block.StructureBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.StructureBlockBlockEntity;
import net.minecraft.block.enums.StructureBlockMode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoadNbtBlock extends Block {

    public LoadNbtBlock() {
        super(Settings.of(Material.METAL, DyeColor.LIGHT_GRAY).requiresTool().strength(-1.0F, 3600000.0F).dropsNothing());
    }

    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world instanceof ServerWorld) || hand == Hand.OFF_HAND || (!player.getStackInHand(Hand.MAIN_HAND).isEmpty() && !player.getStackInHand(Hand.MAIN_HAND).isOf(LoadNbtBlockInit.LOAD_NBT_ITEM))) return ActionResult.PASS;

        String mainPath = FabricLoader.getInstance().getGameDir().getParent().getParent().toString();
        String resourcePath = mainPath + "\\src\\main\\resources\\data";

        player.sendMessage(new TranslatableText(" Working.... "), true);

        // Finds and gets all identifiers for pieces
        List<File> files = new ArrayList<>();
        List<Identifier> identifiers = new ArrayList<>();
        StructureNbtDataFixer.setAllNbtFilesToList(resourcePath, files);
        for (File file : files) {
            String modifiedFileName = file.getAbsolutePath().replace(resourcePath + "\\", "").replace("\\structures\\", ":").replace(".nbt", "").replace('\\', '/');
            identifiers.add(new Identifier(modifiedFileName));
        }

        // Size of area we will need
        int columnCount = 13;
        int rowCount = (int) Math.max(Math.ceil(identifiers.size()) / columnCount, 1);
        int spacing = 48;
        BlockPos bounds = new BlockPos(spacing * (rowCount + 2), spacing, spacing * columnCount);

        BlockState structureVoid = Blocks.STRUCTURE_VOID.getDefaultState();
        BlockState barrier = Blocks.BARRIER.getDefaultState();

        // Fill/clear area with structure void
        BlockPos.Mutable mutableChunk = new BlockPos.Mutable().set(pos.getX() >> 4, pos.getY(), pos.getZ() >> 4);
        mutableChunk.move(1, 0, 0);
        int endChunkX = (pos.getX() + bounds.getX()) >> 4;
        int endChunkZ = (pos.getZ() + bounds.getZ()) >> 4;

        int maxChunks = (endChunkX - mutableChunk.getX()) * (endChunkZ - mutableChunk.getZ());
        int currentChunkCount = 0;
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        BlockPos.Mutable temp = new BlockPos.Mutable();

        if(player.getStackInHand(Hand.MAIN_HAND).isEmpty()){
            List<Chunk> chunks = new ArrayList<>();
            for (; mutableChunk.getX() < endChunkX; mutableChunk.move(1, 0, 0)) {
                for (; mutableChunk.getZ() < endChunkZ; mutableChunk.move(0, 0, 1)) {
                    chunks.add(world.getChunk(mutableChunk.getX(), mutableChunk.getZ(), ChunkStatus.FULL, true));
                    currentChunkCount++;
                    player.sendMessage(new LiteralText("Working part 1: %" + Math.round(((float) currentChunkCount / maxChunks) * 10000f) / 100f), true);
                }
                mutableChunk.set(mutableChunk.getX(), pos.getY(), pos.getZ() >> 4); // Set back to start of row
            }

            mutableChunk = new BlockPos.Mutable().set(pos.getX() >> 4, pos.getY(), pos.getZ() >> 4);
            mutableChunk.move(1, 0, 0);
            currentChunkCount = 0;
            for (; mutableChunk.getX() < endChunkX; mutableChunk.move(1, 0, 0)) {
                for (; mutableChunk.getZ() < endChunkZ; mutableChunk.move(0, 0, 1)) {

                    Chunk chunk = chunks.get(currentChunkCount);
                    temp.set(mutableChunk.getX() << 4, 0, mutableChunk.getZ() << 4);
                    if (chunk == null) {
                        continue;
                    }
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 4; y < 52; y++) {
                                mutablePos.set(temp).move(x, y, z);
                                if (y == 4) {
                                    chunk.setBlockState(mutablePos, barrier, false);
                                } else {
                                    chunk.setBlockState(mutablePos, structureVoid, false);
                                }
                            }
                        }
                    }

                    currentChunkCount++;
                    if (chunk instanceof WorldChunk worldChunk) {
                        worldChunk.markDirty();

                        // Send changes to client to see
                        ((ServerChunkManager) world.getChunkManager()).threadedAnvilChunkStorage
                                .getPlayersWatchingChunk(chunk.getPos(), false)
                                .forEach(s -> s.networkHandler.sendPacket(new ChunkDataS2CPacket(worldChunk)));
                    }
                    player.sendMessage(new LiteralText("Working part 2: %" + Math.round(((float) currentChunkCount / maxChunks) * 10000f) / 100f), true);
                }
                mutableChunk.set(mutableChunk.getX(), pos.getY(), pos.getZ() >> 4); // Set back to start of row
            }
        }

        if(player.getStackInHand(Hand.MAIN_HAND).isOf(LoadNbtBlockInit.LOAD_NBT_ITEM)){
            generateStructurePieces(world, pos, player, identifiers, columnCount, spacing, mutableChunk);
        }
        return ActionResult.FAIL;
    }


    private void generateStructurePieces(World world, BlockPos pos, PlayerEntity player, List<Identifier> identifiers, int columnCount, int spacing, BlockPos.Mutable mutableChunk) {
        mutableChunk.set(((pos.getX() >> 4) + 1) << 4, pos.getY(), (pos.getZ() >> 4) << 4);

        for(int pieceIndex = 1; pieceIndex <= identifiers.size(); pieceIndex++){
            player.sendMessage(new TranslatableText(" Working making structure: "+ identifiers.get(pieceIndex-1)), true);

            world.setBlockState(mutableChunk, Blocks.STRUCTURE_BLOCK.getDefaultState().with(StructureBlock.MODE, StructureBlockMode.LOAD), 3);
            BlockEntity be = world.getBlockEntity(mutableChunk);
            if(be instanceof StructureBlockBlockEntity structureBlockBlockEntity){
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
                mutableChunk.move(spacing,0, (-spacing * columnCount));
            }
        }
    }
}
