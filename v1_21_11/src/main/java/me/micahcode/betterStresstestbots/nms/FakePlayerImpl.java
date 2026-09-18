package me.micahcode.betterStresstestbots.nms;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import io.papermc.paper.util.KeepAlive;
import me.micahcode.betterStresstestbots.BotManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

public class FakePlayerImpl implements IFakePlayer {

    private final ServerPlayer nmsPlayer;
    private final double spawnX, spawnY, spawnZ;
    private double targetX, targetY, targetZ;
    private double lastX, lastY, lastZ;
    private boolean op = true;
    private final Random random = new Random();

    /** The embedded channel of the bot's fake connection (server→bot side). */
    private final EmbeddedChannel channel;

    /**
     * The keep-alive challenge queue of the bot's connection
     * (a {@code MultiThreadedQueue} of pending challenges, resolved once via
     * reflection — the field is private; the class, queue and accessor APIs
     * are all compile-checked). Null when it could not be resolved.
     */
    private MultiThreadedQueue<KeepAlive.PendingKeepAlive> keepAliveQueue;

    private double speed  = 0.1;
    private double radius = 500.0;

    private BotManager.GroundMode mode = BotManager.GroundMode.NONE;

    /** When non-null the bot navigates to a fixed point instead of wandering. */
    private Location gotoTarget = null;

    public FakePlayerImpl(String name, Location spawn, Logger logger) {
        this.spawnX = spawn.getX();
        this.spawnY = spawn.getY();
        this.spawnZ = spawn.getZ();

        MinecraftServer server = ((CraftServer) org.bukkit.Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) spawn.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);

        nmsPlayer = new ServerPlayer(server, level, profile, ClientInformation.createDefault());

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        channel = new EmbeddedChannel(connection);
        // The vanilla disconnect path casts the connection's address to
        // InetSocketAddress; the embedded channel's address is an
        // EmbeddedSocketAddress, so without a fake remote address each bot
        // removal logs a ClassCastException from Connection.handleDisconnection.
        // (Public field; re-applied in remove() in case it is overwritten.)
        connection.address = new InetSocketAddress("127.0.0.1", 51234);

        // v1_21_11: factory method cookie
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        nmsPlayer.connection = new ServerGamePacketListenerImpl(server, connection, nmsPlayer, cookie);

        try {
            server.getPlayerList().placeNewPlayer(connection, nmsPlayer, cookie);
        } catch (Exception e) {
            logger.warning("Failed to place bot " + name + ": " + e.getMessage());
            return;
        }

        if (!resolveKeepAliveQueue()) {
            // Without it the bot is kicked after ~30s (keep-alive timeout), so
            // this must be visible in the console to diagnose layout changes.
            logger.warning("Keep-alive queue could not be resolved for " + name
                    + " — it will be kicked after ~30s unless the server keep-alive is disabled.");
        }

        nmsPlayer.setGameMode(GameType.CREATIVE);
        nmsPlayer.setNoGravity(true);
        nmsPlayer.snapTo(spawnX, spawnY, spawnZ); nmsPlayer.setYRot(0f); nmsPlayer.setXRot(0f); // v1_21_11: snapTo
        nmsPlayer.getBukkitEntity().setOp(true); // default OP so /rtp etc. work
        lastX = nmsPlayer.getX();
        lastY = nmsPlayer.getY();
        lastZ = nmsPlayer.getZ();
        pickNewTarget();
    }

    /**
     * Resolves the keep-alive challenge queue of the bot's connection.
     * Paper 1.21.11 holds it as the private
     * {@code KeepAlive keepAlive} field of
     * {@link ServerCommonPacketListenerImpl} (the {@code pendingKeepAlives}
     * member of that holder). Only those two private fields need reflection —
     * the classes, the queue type and the accessor methods are all
     * compile-checked, so a layout change fails loudly at compile time.
     */
    /** @return true when the keep-alive queue was resolved. */
    private boolean resolveKeepAliveQueue() {
        if (nmsPlayer == null || nmsPlayer.connection == null) return false;
        try {
            Field kaField = ServerCommonPacketListenerImpl.class.getDeclaredField("keepAlive");
            kaField.setAccessible(true);
            KeepAlive keepAlive = (KeepAlive) kaField.get(nmsPlayer.connection);
            if (keepAlive == null) return false;
            Field queueField = KeepAlive.class.getDeclaredField("pendingKeepAlives");
            queueField.setAccessible(true);
            keepAliveQueue = (MultiThreadedQueue<KeepAlive.PendingKeepAlive>) queueField.get(keepAlive);
            return keepAliveQueue != null;
        } catch (Throwable t) {
            keepAliveQueue = null;
            return false;
        }
    }

    /**
     * Answers the server's keep-alive challenge so the bot is not kicked with
     * "was kicked due to keepalive timeout". The embedded connection never
     * performs a network round-trip, so the pending challenge is peeked and
     * acknowledged through the listener's own public packet handler — exactly
     * what a decoded {@link ServerboundKeepAlivePacket} would do. Runs on the
     * bot's region thread, the same thread that owns its connection.
     */
    private void acknowledgeKeepAlive() {
        if (keepAliveQueue == null || nmsPlayer == null || nmsPlayer.connection == null) return;
        try {
            KeepAlive.PendingKeepAlive pending = keepAliveQueue.peek();
            if (pending == null) return;
            nmsPlayer.connection.handleKeepAlive(new ServerboundKeepAlivePacket(pending.challengeId()));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Discards server→bot packets (chat broadcasts, block updates, the keep
     * alive challenge itself, ...) that accumulate in the embedded channel's
     * outbound buffer. Nothing reads them, so without draining they grow
     * without bound on long stress-test runs.
     */
    private void drainOutboundPackets() {
        try {
            Object msg;
            while ((msg = channel.readOutbound()) != null) {
                if (msg instanceof ReferenceCounted rc) {
                    rc.release();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void pickNewTarget() {
        double originX = nmsPlayer == null ? spawnX : nmsPlayer.getX();
        double originY = nmsPlayer == null ? spawnY : nmsPlayer.getY();
        double originZ = nmsPlayer == null ? spawnZ : nmsPlayer.getZ();

        double angle = random.nextDouble() * Math.PI * 2;
        double dist  = random.nextDouble() * radius;
        targetX = originX + Math.cos(angle) * dist;
        targetZ = originZ + Math.sin(angle) * dist;
        targetY = (mode == BotManager.GroundMode.WALK)
                ? getSurfaceY(targetX, targetZ)
                : Math.max(64, Math.min(250, originY + random.nextDouble() * 100 + 20));
    }

    @Override
    public void tick() {
        if (nmsPlayer == null || !nmsPlayer.isAlive()) return;

        acknowledgeKeepAlive();
        drainOutboundPackets();

        // Detect an external teleport (e.g. another plugin ran /rtp on this bot).
        double jumpX = nmsPlayer.getX() - lastX;
        double jumpZ = nmsPlayer.getZ() - lastZ;
        double jumpY = nmsPlayer.getY() - lastY;
        double jumpDist = Math.sqrt(jumpX * jumpX + jumpY * jumpY + jumpZ * jumpZ);
        if (jumpDist > speed * 4.0 + 1.0) {
            gotoTarget = null;
            pickNewTarget();
        }
        lastX = nmsPlayer.getX();
        lastY = nmsPlayer.getY();
        lastZ = nmsPlayer.getZ();

        if (mode == BotManager.GroundMode.NONE && gotoTarget == null) return;

        boolean useGoto = (gotoTarget != null);
        double effX = useGoto ? gotoTarget.getX() : targetX;
        double effY = useGoto ? gotoTarget.getY() : targetY;
        double effZ = useGoto ? gotoTarget.getZ() : targetZ;

        double dx = effX - nmsPlayer.getX();
        double dz = effZ - nmsPlayer.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        if (horizDist < 2.0) {
            if (useGoto) gotoTarget = null;
            else pickNewTarget();
            return;
        }

        double nx = dx / horizDist * speed;
        double nz = dz / horizDist * speed;
        double newX = nmsPlayer.getX() + nx;
        double newZ = nmsPlayer.getZ() + nz;
        double newY;

        boolean walkOnGround = (mode == BotManager.GroundMode.WALK)
                || (useGoto && mode == BotManager.GroundMode.NONE);

        if (walkOnGround) {
            newY = getSurfaceY(newX, newZ);
        } else {
            double dy = effY - nmsPlayer.getY();
            double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            newY = nmsPlayer.getY() + (dy / totalDist * speed);
        }

        nmsPlayer.snapTo(newX, newY, newZ); nmsPlayer.setYRot(nmsPlayer.getYRot()); nmsPlayer.setXRot(nmsPlayer.getXRot());
    }


    private double getSurfaceY(double x, double z) {
        try {
            int y = ((ServerLevel) nmsPlayer.level()).getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
            return Math.max(y, 60);
        } catch (Exception e) {
            return spawnY;
        }
    }

    @Override
    public void navigateTo(Location target) {
        this.gotoTarget = target.clone();
    }

    @Override
    public void sendChat(String message) {
        if (nmsPlayer == null || !nmsPlayer.isAlive()) return;
        if (message.startsWith("/")) {
            executeCommand(message);
            return;
        }
        try { nmsPlayer.getBukkitEntity().chat(message); } catch (Exception ignored) {}
    }

    @Override
    public void executeCommand(String command) {
        if (nmsPlayer == null || !nmsPlayer.isAlive()) return;
        try {
            // chat() routes a leading '/' through the real command handler, which fires
            // PlayerCommandPreprocessEvent and makes /rtp etc. execute on the bot.
            nmsPlayer.getBukkitEntity().chat(command);
        } catch (Exception ignored) {}
    }

    @Override
    public void teleportTo(Location loc) {
        if (nmsPlayer == null) return;
        gotoTarget = null;
        double y = (mode == BotManager.GroundMode.WALK) ? getSurfaceY(loc.getX(), loc.getZ()) : loc.getY();
        nmsPlayer.snapTo(loc.getX(), y, loc.getZ()); nmsPlayer.setYRot(loc.getYaw()); nmsPlayer.setXRot(loc.getPitch());
        lastX = nmsPlayer.getX();
        lastY = nmsPlayer.getY();
        lastZ = nmsPlayer.getZ();
    }

    @Override
    public void remove() {
        try {
            if (nmsPlayer != null && nmsPlayer.connection != null) {
                // Re-apply the fake address in case the server overwrote it
                // after join (keeps the disconnect log clean).
                nmsPlayer.connection.address = new InetSocketAddress("127.0.0.1", 51234);
                nmsPlayer.connection.disconnect(Component.literal("Stress bot removed"));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void setSpeed(double s) { this.speed = s; }

    @Override
    public void setRadius(double r) {
        this.radius = r;
        pickNewTarget();
    }

    @Override
    public void setMode(BotManager.GroundMode mode) {
        this.mode = mode;
        gotoTarget = null;
        pickNewTarget();
    }

    @Override
    public void setGroundMode(boolean g) {
        setMode(g ? BotManager.GroundMode.WALK : BotManager.GroundMode.FLY);
    }

    @Override
    public void setOp(boolean op) {
        if (nmsPlayer == null || !nmsPlayer.isAlive()) return;
        this.op = op;
        nmsPlayer.getBukkitEntity().setOp(op);
    }

    @Override
    public boolean isOp() {
        return op;
    }

    @Override
    public boolean isAlive() { return nmsPlayer != null && nmsPlayer.isAlive(); }

    @Override
    public String getName() {
        return nmsPlayer != null ? nmsPlayer.getGameProfile().name() : "unknown"; // v1_21_11: name()
    }

    @Override
    public org.bukkit.entity.Player getBukkitEntity() {
        return nmsPlayer != null ? (org.bukkit.entity.Player) nmsPlayer.getBukkitEntity() : null;
    }
}
