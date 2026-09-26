package mc.nala.servux.jei.recipesync;

import java.nio.channels.ClosedChannelException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;

import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * JEI decides whether the server synced recipes when it handles the first UpdateRecipesPacket.
 * A Fabric server sends fabric:recipe_sync before that packet (Fabric's PlayerListMixin). Paper sends
 * UpdateRecipesPacket before any plugin event, and the client only declares its play channels after the login packet.
 * This outbound handler holds the first UpdateRecipesPacket of a connection until the recipes are sent,
 * or at most {@link #TIMEOUT_MS} for clients that never declare fabric:recipe_sync.
 */
public final class RecipeSyncJoinOrder extends ChannelDuplexHandler {
	private static final String NAME = "nalaservux_recipe_order";
	private static final Key LISTENER_KEY = Key.key("nalaservux", "recipe_order");
	private static final long TIMEOUT_MS = 3000L;
	// Players who already got the recipes on this connection.
	private static final Set<UUID> SENT = ConcurrentHashMap.newKeySet();

	private ChannelHandlerContext ctx;
	private Object held;
	private ChannelPromise heldPromise;
	private ScheduledFuture<?> timeout;
	private boolean done;

	public static void install() {
		ChannelInitializeListenerHolder.addListener(LISTENER_KEY, channel -> {
			// A new server connection has "outbound_config" in the slot that later becomes "encoder".
			// After it, towards the tail: outbound packets reach this handler before encoding.
			String anchor = channel.pipeline().get(HandlerNames.OUTBOUND_CONFIG) != null ? HandlerNames.OUTBOUND_CONFIG : HandlerNames.ENCODER;

			if (channel.pipeline().get(anchor) != null) {
				channel.pipeline().addAfter(anchor, NAME, new RecipeSyncJoinOrder());
			}
		});
	}

	public static void onQuit(UUID uuid) {
		SENT.remove(uuid);
	}

	public static void uninstall() {
		ChannelInitializeListenerHolder.removeListener(LISTENER_KEY);
	}

	/**
	 * Main thread. Sends the recipes, then releases the held packet; the channel event loop keeps this order.
	 */
	public static void sendAndRelease(ServerPlayer player) {
		if (!SENT.add(player.getUUID())) {
			return;
		}

		RecipeSync.sendRecipes(player);
		Channel channel = player.connection.connection.channel;

		channel.eventLoop().execute(() -> {
			if (channel.pipeline().get(NAME) instanceof RecipeSyncJoinOrder order) {
				order.release();
			}
		});
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (this.done || !(msg instanceof ClientboundUpdateRecipesPacket)) {
			super.write(ctx, msg, promise);
			return;
		}

		if (this.held == null) {
			this.held = msg;
			this.heldPromise = promise;
			this.timeout = ctx.executor().schedule(this::release, TIMEOUT_MS, TimeUnit.MILLISECONDS);
			return;
		}

		// A second UpdateRecipesPacket (reload) while holding: keep the order and stop holding.
		this.release();
		super.write(ctx, msg, promise);
	}

	private void release() {
		if (this.done) {
			return;
		}

		this.done = true;

		if (this.timeout != null) {
			this.timeout.cancel(false);
		}

		if (this.held != null) {
			this.ctx.writeAndFlush(this.held, this.heldPromise);
			this.held = null;
			this.heldPromise = null;
		}

		this.ctx.pipeline().remove(this);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (!this.done) {
			this.done = true;

			if (this.timeout != null) {
				this.timeout.cancel(false);
			}

			if (this.heldPromise != null && !this.heldPromise.isVoid()) {
				this.heldPromise.tryFailure(new ClosedChannelException());
			}
		}

		super.channelInactive(ctx);
	}
}
