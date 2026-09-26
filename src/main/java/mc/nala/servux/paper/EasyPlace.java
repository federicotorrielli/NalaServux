package mc.nala.servux.paper;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import mc.nala.servux.Servux;
import mc.nala.servux.dataproviders.ServuxConfigProvider;
import mc.nala.servux.util.PlacementHandler;

/** Easy Place V3: netty fixes the encoded hit, {@link BlockPlaceEvent} applies the state (upstream: two mixins). */
public class EasyPlace implements Listener
{
    private record Pending(BlockHitResult encodedHit, InteractionHand hand, long time) {}

    private static final long STALE_MS = 5000L;
    private static final Map<UUID, Map<Integer, Pending>> PENDING = new ConcurrentHashMap<>();
    private static final VarHandle ACK_UP_TO;

    static
    {
        try
        {
            ACK_UP_TO = MethodHandles.privateLookupIn(ServerGamePacketListenerImpl.class, MethodHandles.lookup())
                                     .findVarHandle(ServerGamePacketListenerImpl.class, "ackBlockChangesUpTo", int.class);
        }
        catch (ReflectiveOperationException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Netty thread: returns the packet vanilla should handle. */
    public static ServerboundUseItemOnPacket onUseItemOn(UUID player, ServerboundUseItemOnPacket packet)
    {
        BlockHitResult hit = packet.getHitResult();
        Vec3 location = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        double dx = location.x - pos.getX();

        // Same decoding as applyPlacementProtocolV3: normal clicks give a negative protocol value.
        if (!Double.isFinite(dx) || (int) dx - 2 < 0)
        {
            return packet;
        }

        long now = System.currentTimeMillis();
        Map<Integer, Pending> map = PENDING.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        map.values().removeIf(p -> now - p.time() > STALE_MS);
        map.put(packet.getSequence(), new Pending(hit, packet.getHand(), now));

        Vec3 rewritten = new Vec3(pos.getX() + (dx - (int) dx), location.y, location.z);
        BlockHitResult newHit = new BlockHitResult(rewritten, hit.getDirection(), pos, hit.isInside(), hit.isWorldBorderHit());
        ServerboundUseItemOnPacket copy = new ServerboundUseItemOnPacket(packet.getHand(), newHit, packet.getSequence());
        copy.timestamp = packet.timestamp;
        return copy;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event)
    {
        ServerPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
        Map<Integer, Pending> map = PENDING.get(player.getUUID());

        if (map == null || player.connection == null)
        {
            return;
        }

        Pending pending = map.remove((int) ACK_UP_TO.get(player.connection));

        if (pending == null || !ServuxConfigProvider.INSTANCE.hasPermission_EasyPlace(player))
        {
            return;
        }

        ServerLevel level = player.level();
        BlockPos pos = new BlockPos(event.getBlockPlaced().getX(), event.getBlockPlaced().getY(), event.getBlockPlaced().getZ());
        ItemStack stack = CraftItemStack.asNMSCopy(event.getItemInHand());
        BlockHitResult hit = pending.encodedHit();

        // The block is already placed, so pass its position explicitly (upstream: ctx.getClickedPos()).
        BlockPlaceContext ctx = new BlockPlaceContext(level, player, pending.hand(), stack, hit);
        PlacementHandler.UseContext useContext = new PlacementHandler.UseContext(level, pos, hit.getDirection(), hit.getLocation(),
                                                                                 player, pending.hand(), ctx);
        net.minecraft.world.level.block.state.BlockState placed = level.getBlockState(pos);
        net.minecraft.world.level.block.state.BlockState state = PlacementHandler.applyPlacementProtocolV3(placed, useContext);

        Servux.debugLog("EasyPlace: {} at {}: placed {}, protocol result {}", event.getEventName(), pos.toShortString(), placed, state);

        if (state == null)
        {
            // Upstream returns null from getPlacementState, so nothing is placed.
            event.setCancelled(true);
            return;
        }

        if (state == placed)
        {
            return;
        }

        if (event instanceof BlockMultiPlaceEvent multi)
        {
            // Two-part blocks: undo the other part, setPlacedBy places it again.
            for (BlockState replaced : multi.getReplacedBlockStates())
            {
                if (replaced.getX() != pos.getX() || replaced.getY() != pos.getY() || replaced.getZ() != pos.getZ())
                {
                    CraftBlockState craft = (CraftBlockState) replaced;
                    level.setBlock(craft.getPosition(), craft.getHandle(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                }
            }

            level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE);
            state.getBlock().setPlacedBy(level, pos, state, player, stack);
        }
        else
        {
            level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        PENDING.remove(event.getPlayer().getUniqueId());
    }
}
