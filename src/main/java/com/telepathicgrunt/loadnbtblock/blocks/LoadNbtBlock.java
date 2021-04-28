package com.telepathicgrunt.loadnbtblock.blocks;

import com.telepathicgrunt.loadnbtblock.mixins.MinecraftServerAccessor;
import com.telepathicgrunt.loadnbtblock.mixins.StructureManagerAccessor;
import com.telepathicgrunt.loadnbtblock.utils.StructureNbtDataFixer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.StructureBlockBlockEntity;
import net.minecraft.block.enums.StructureBlockMode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
        chunkJobs.clear();

        Identifier id = new Identifier(com.telepathicgrunt.loadnbtblock.LoadNbtBlock.MODID, "sections/section1.nbt");
        Identifier id2 = new Identifier(com.telepathicgrunt.loadnbtblock.LoadNbtBlock.MODID, "sections/section2.nbt");
        CompoundTag compoundTag;
        CompoundTag compoundTag2;
        try {
            ResourceManager resourceManager = ((StructureManagerAccessor)((ServerWorld) world).getStructureManager()).lnbtb_getField_25189();
            Resource resource = resourceManager.getResource(id);
            compoundTag = NbtIo.readCompressed(resource.getInputStream());

            resource = resourceManager.getResource(id2);
            compoundTag2 = NbtIo.readCompressed(resource.getInputStream());
        }
        catch (Exception e) {
            e.printStackTrace();
            return ActionResult.FAIL;
        }
        ListTag palette1 = compoundTag.getList("Palette", 10);
        long[] blockStates1 = compoundTag.getLongArray("BlockStates");
        ListTag palette2 = compoundTag2.getList("Palette", 10);
        long[] blockStates2 = compoundTag2.getLongArray("BlockStates");

        short zero = 0;
        short nonEmptyBlockCount = 256;

        // Fill/clear area with structure void
        task<Chunk, World, Integer> taskToRun = (chunkIn, worldIn, yPos) -> {
            ChunkSection[] sections = chunkIn.getSectionArray();

            ChunkSection newChunkSection = new ChunkSection(0, nonEmptyBlockCount, zero, zero);
            newChunkSection.getContainer().read(palette1, blockStates1);
            sections[0] = newChunkSection;

            newChunkSection = new ChunkSection(1, nonEmptyBlockCount, zero, zero);
            newChunkSection.getContainer().read(palette2, blockStates2);
            sections[1] = newChunkSection;

            newChunkSection = new ChunkSection(2, nonEmptyBlockCount, zero, zero);
            newChunkSection.getContainer().read(palette2, blockStates2);
            sections[2] = newChunkSection;
        };

        BlockPos.Mutable mutableChunk = new BlockPos.Mutable().set(pos.getX() >> 4, pos.getY(), pos.getZ() >> 4);
        mutableChunk.move(1,0,0);
        int endChunkX = (pos.getX() + bounds.getX()) >> 4;
        int endChunkZ = (pos.getZ() + bounds.getZ()) >> 4;

        for(; mutableChunk.getX() < endChunkX; mutableChunk.move(1,0,0)) {
            for (; mutableChunk.getZ() < endChunkZ; mutableChunk.move(0, 0, 1)) {
                chunkJobs.computeIfAbsent(
                        ChunkPos.toLong(mutableChunk.getX(), mutableChunk.getZ()), // Chunk to clear
                        (chunkLong) -> Pair.of(mutableChunk.getY(), taskToRun) // task to run at y pos
                );
            }
            mutableChunk.set(mutableChunk.getX(), mutableChunk.getY(), pos.getZ() >> 4); // Set back to start of row
        }

        MinecraftServer executor = world.getServer();
        AtomicInteger completedSections = new AtomicInteger();

        chunkJobs.forEach((key, chunkJobCollection) ->
            executor.execute(new ServerTask(((MinecraftServerAccessor)executor).lnbtb_getTicks(), () -> {
                int chunkX = ChunkPos.getPackedX(key);
                int chunkZ = ChunkPos.getPackedZ(key);
                WorldChunk chunkToClear = world.getChunk(chunkX, chunkZ);

                // Run the task to clear the chunk
                chunkJobCollection.getRight().apply(chunkToClear, world, chunkJobCollection.getLeft());

                // Send changes to client to see
                chunkToClear.markDirty();
                ((ServerChunkManager) world.getChunkManager()).threadedAnvilChunkStorage
                        .getPlayersWatchingChunk(chunkToClear.getPos(), false)
                        .forEach(s -> s.networkHandler.sendPacket(new ChunkDataS2CPacket(chunkToClear, 65535)));

                // Tell player progress so they know it is working
                int currentSection = completedSections.get() + 1;
                completedSections.set(currentSection);
                player.sendMessage(new TranslatableText("Working: %" + (((float)currentSection / chunkJobs.size()) * 100)), true);

                // Places structure blocks and loads pieces when last task is completed
                // TODO: make this be threaded as well.
                if(currentSection == chunkJobs.size()){
                    generateStructurePieces(world, pos, player, identifiers, columnCount, spacing, mutableChunk);
                }
            }))
        );

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
