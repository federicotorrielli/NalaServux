package mc.nala.servux.util.game;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.StairsShape;

import mc.nala.servux.dataproviders.LitematicsDataProvider;

/** The chest/stairs/rail mixin fixes, called by the schematic code. */
public class BlockTransforms
{
    public static BlockState mirror(BlockState state, Mirror mirror)
    {
        LitematicsDataProvider provider = LitematicsDataProvider.INSTANCE;

        if (state.getBlock() instanceof ChestBlock && provider.isEnabled() && provider.fixChestMirror.getValue())
        {
            ChestType type = state.getValue(ChestBlock.TYPE);

            if (type != ChestType.SINGLE)
            {
                return BlockUtils.fixMirrorDoubleChest(state, mirror, type);
            }
        }
        else if (state.getBlock() instanceof StairBlock && provider.isEnabled() && provider.fixStairMirror.getValue())
        {
            BlockState fixed = fixStairsMirror(state, mirror);

            if (fixed != null)
            {
                return fixed;
            }
        }

        return state.mirror(mirror);
    }

    public static BlockState rotate(BlockState state, Rotation rotation)
    {
        LitematicsDataProvider provider = LitematicsDataProvider.INSTANCE;

        if (rotation == Rotation.CLOCKWISE_180 && provider.isEnabled() && provider.fixRaiLRotations.getValue())
        {
            RailShape shape = null;

            if (state.getBlock() instanceof RailBlock)
            {
                shape = state.getValue(RailBlock.SHAPE);
            }
            else if (state.getBlock() instanceof DetectorRailBlock)
            {
                shape = state.getValue(DetectorRailBlock.SHAPE);
            }
            else if (state.getBlock() instanceof PoweredRailBlock)
            {
                shape = state.getValue(PoweredRailBlock.SHAPE);
            }

            if (shape == RailShape.EAST_WEST || shape == RailShape.NORTH_SOUTH)
            {
                return state;
            }
        }

        return state.rotate(rotation);
    }

    private static BlockState fixStairsMirror(BlockState state, Mirror mirror)
    {
        Direction direction = state.getValue(StairBlock.FACING);
        StairsShape stairShape = state.getValue(StairBlock.SHAPE);

        // Fixes X Axis for FRONT_BACK being inverted for INNER_LEFT / INNER_RIGHT
        if (direction.getAxis() == Direction.Axis.X && mirror == Mirror.FRONT_BACK)
        {
            return switch (stairShape)
            {
                case INNER_LEFT  -> state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.INNER_RIGHT);
                case INNER_RIGHT -> state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT);
                case OUTER_LEFT  -> state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT);
                case OUTER_RIGHT -> state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT);
                default          -> state.rotate(Rotation.CLOCKWISE_180);
            };
        }
        // Fixes missing Axis STAIR_SHAPE flips
        else if ((direction.getAxis() == Direction.Axis.X && mirror == Mirror.LEFT_RIGHT) ||
                 (direction.getAxis() == Direction.Axis.Z && mirror == Mirror.FRONT_BACK))
        {
            return switch (stairShape)
            {
                case INNER_LEFT  -> state.setValue(StairBlock.SHAPE, StairsShape.INNER_RIGHT);
                case INNER_RIGHT -> state.setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT);
                case OUTER_LEFT  -> state.setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT);
                case OUTER_RIGHT -> state.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT);
                default          -> state;
            };
        }

        return null;
    }
}
