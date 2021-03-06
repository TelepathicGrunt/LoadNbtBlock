package com.telepathicgrunt.loadnbtblock.blocks;

import com.telepathicgrunt.loadnbtblock.utils.StructureNbtDataFixer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.StructureBlockBlockEntity;
import net.minecraft.block.enums.StructureBlockMode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadNbtBlock extends Block {

    public LoadNbtBlock() {
        super(Settings.of(Material.METAL, MaterialColor.LIGHT_GRAY).requiresTool().strength(-1.0F, 3600000.0F).dropsNothing());
    }

    // source: https://github.com/williambl/explosivessquared/blob/master/src/main/kotlin/com/williambl/explosivessquared/util/actions/MassBlockActionManager.kt
    @FunctionalInterface
    interface task<One, Two, Three> {
        void apply(One one, Two two, Three three);
    }
    private final Map<Long, Pair<Integer, task<Chunk, World, Integer>>> chunkJobs = new HashMap<>();

    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if(!(world instanceof ServerWorld) || hand == Hand.MAIN_HAND) return ActionResult.PASS;

        String mainPath = FabricLoader.getInstance().getGameDir().getParent().getParent().toString();
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
        int columnCount = 13;
        int rowCount = (int) Math.max(Math.ceil(identifiers.size()) / columnCount, 1);
        int spacing = 48;
        BlockPos bounds = new BlockPos(spacing * (rowCount+2), spacing, spacing * columnCount);

        BlockState structureVoid = Blocks.STRUCTURE_VOID.getDefaultState();
        BlockState barrier = Blocks.BARRIER.getDefaultState();
        short nonAir = 255;
        short zero = 0;

        // Fill/clear area with structure void
        BlockPos.Mutable mutableChunk = new BlockPos.Mutable().set(pos.getX() >> 4, pos.getY(), pos.getZ() >> 4);
        mutableChunk.move(1,0,0);
        int endChunkX = (pos.getX() + bounds.getX()) >> 4;
        int endChunkZ = (pos.getZ() + bounds.getZ()) >> 4;

        int maxChunks = (endChunkX - mutableChunk.getX()) * (endChunkZ - mutableChunk.getZ());
        int currentSection = 0;
        for(; mutableChunk.getX() < endChunkX; mutableChunk.move(1,0,0)) {
            for (; mutableChunk.getZ() < endChunkZ; mutableChunk.move(0, 0, 1)) {

                WorldChunk chunk = world.getChunk(mutableChunk.getX(), mutableChunk.getZ());
                ChunkSection[] sections = chunk.getSectionArray();
                sections[1] = new ChunkSection(16, nonAir, zero, zero);
                sections[2] = new ChunkSection(32, nonAir, zero, zero);
                PalettedContainer<BlockState> bottomSection = sections[0].getContainer();
                PalettedContainer<BlockState> middleSection = sections[1].getContainer();
                PalettedContainer<BlockState> topSection = sections[2].getContainer();
                for(int x = 0; x < 16; x++){
                    for(int z = 0; z < 16; z++){
                        for(int y = 4; y < 16; y++){
                            if(y == 4){
                                bottomSection.set(x, y, z, barrier);
                            }
                            else{
                                bottomSection.set(x, y, z, structureVoid);
                            }
                        }
                        for(int y = 0; y < 16; y++){
                            middleSection.set(x, y, z, structureVoid);
                            topSection.set(x, y, z, structureVoid);
                        }
                    }
                }

                currentSection++;
                chunk.markDirty();

                // Send changes to client to see
                ((ServerChunkManager) world.getChunkManager()).threadedAnvilChunkStorage
                        .getPlayersWatchingChunk(chunk.getPos(), false)
                        .forEach(s -> s.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, 65535)));

                player.sendMessage(new LiteralText("Working: %" +  Math.round(((float)currentSection / maxChunks) * 10000f) / 100f), true);
            }
            mutableChunk.set(mutableChunk.getX(), pos.getY(), pos.getZ() >> 4); // Set back to start of row
        }

        generateStructurePieces(world, pos, player, identifiers, columnCount, spacing, mutableChunk);
        return ActionResult.SUCCESS;
    }


    private void generateStructurePieces(World world, BlockPos pos, PlayerEntity player, List<Identifier> identifiers, int columnCount, int spacing, BlockPos.Mutable mutableChunk) {
        mutableChunk.set(((pos.getX() >> 4) + 1) << 4, pos.getY(), (pos.getZ() >> 4) << 4);

        for(int pieceIndex = 1; pieceIndex <= identifiers.size(); pieceIndex++){
            player.sendMessage(new TranslatableText(" Working making structure: "+ identifiers.get(pieceIndex-1)), true);

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
                mutableChunk.move(spacing,0, (-spacing * columnCount));
            }
        }
    }
}
