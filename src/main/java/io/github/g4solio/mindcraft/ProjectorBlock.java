package io.github.g4solio.mindcraft;

import io.github.g4solio.mindcraft.MindRuntime.Mood;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The projector: a contact point with Uno at a fixed place of the base.
 * The player who puts it down registers a place. A click opens the screen of Uno, the same screen as the tablet.
 * The hologram above it shows only a general state, to each player who sees it. It shows no data of a job.
 * <p>
 * The hologram is part of the block model. The block has one model for each state, thus no client code is necessary.
 */
public class ProjectorBlock extends Block {
    /** The block state names of the three moods. */
    public enum Look implements StringRepresentable {
        IDLE, WORKING, ATTENTION;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Look> MOOD = EnumProperty.create("mood", Look.class);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 3, 15);
    private static final int UPDATE_TICKS = 20;

    public ProjectorBlock() {
        super(BlockBehaviour.Properties.of().strength(1.5f).noOcclusion().lightLevel(state -> 8));
        registerDefaultState(stateDefinition.any().setValue(MOOD, Look.IDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOOD);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The player who puts the projector down owns the place. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player) {
            MindRuntime.get(level.getServer()).addPlace(player.getUUID(), level, pos);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (!oldState.is(this)) level.scheduleTick(pos, this, UPDATE_TICKS);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !newState.is(this)) MindRuntime.get(level.getServer()).removePlace(level, pos);
        super.onRemove(state, level, pos, newState, moving);
    }

    /** One time for each second the server sets the mood from the jobs of the owner. Each client gets the new block state. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Mood mood = MindRuntime.get(level.getServer()).mood(level, pos);
        Look look = Look.valueOf(mood.name());
        if (state.getValue(MOOD) != look) level.setBlock(pos, state.setValue(MOOD, look), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, UPDATE_TICKS);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) Net.open(serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A few sparks in the hologram. This method runs only on a client. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.3 + random.nextDouble() * 0.4,
            pos.getY() + 0.5 + random.nextDouble() * 0.7, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
    }
}
