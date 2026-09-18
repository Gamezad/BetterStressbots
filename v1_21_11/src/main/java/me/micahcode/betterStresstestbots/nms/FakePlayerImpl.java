package me.micahcode.betterStresstestbots.nms;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
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
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
     * Best-effort handle to Paper's keep-alive state on the bot's listener
     * (a {@code MultiThreadedQueue} of pending challenges, resolved via
     * reflection so this compiles against the plain NMS API only).
     */
    private Object keepAliveQueue;
    private Method keepAlivePeek;
    private Method keepAliveChallengeId;

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
        spoofRemoteAddress(connection);

        // v1_21_11: factory method cookie
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        nmsPlayer.connection = new ServerGamePacketListenerImpl(server, connection, nmsPlayer, cookie);

        try {
            server.getPlayerList().placeNewPlayer(connection, nmsPlayer, cookie);
        } catch (Exception e) {
            logger.warning("Failed to place bot " + name + ": " + e.getMessage());
            return;
        }

        resolveKeepAliveState();

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
     * Gives the embedded connection a real (fake) remote address. The vanilla
     * disconnect path casts the connection's address to
     * {@link InetSocketAddress}; the embedded channel's address is an
     * {@code EmbeddedSocketAddress}, so without this every bot removal logs a
     * ClassCastException from {@code Connection.handleDisconnection}.
     * Best-effort: if the field layout ever changes the CCE just stays.
     */
    private void spoofRemoteAddress(Connection connection) {
        InetSocketAddress fake;
        try {
            fake = new InetSocketAddress("127.0.0.1", 51234);
        } catch (RuntimeException e) {
            return;
        }
        for (String name : new String[] {"remoteAddress", "address"}) {
            try {
                Field f = Connection.class.getDeclaredField(name);
                if (SocketAddress.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    f.set(connection, fake);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Finds the keep-alive challenge queue on the bot's packet listener.
     * Paper 1.21.11 stores the state in a {@code io.papermc.paper.util.KeepAlive}
     * field on the common listener; the fallback layout keeps the
     * {@code MultiThreadedQueue} directly on the listener. Reflection keeps the
     * plugin decoupled from Paper's internal class layout.
     */
    private void resolveKeepAliveState() {
        if (nmsPlayer == null || nmsPlayer.connection == null) return;
        try {
            Object listener = nmsPlayer.connection;

            Object holder = listener;
            Field holderField = null;
            for (Class<?> c = listener.getClass(); c != null && holderField == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().getName().equals("io.papermc.paper.util.KeepAlive")) {
                        holderField = f;
                        break;
                    }
                }
            }
            if (holderField != null) {
                holderField.setAccessible(true);
                holder = holderField.get(listener);
            }

            Field queueField = null;
            for (Class<?> c = holder.getClass(); c != null && queueField == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().getName().equals("ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue")) {
                        f.setAccessible(true);
                        queueField = f;
                        break;
                    }
                }
            }
            keepAliveQueue = queueField != null ? queueField.get(holder) : null;
        } catch (Throwable t) {
            keepAliveQueue = null;
        }
    }

    /**
     * Answers the server's keep-alive challenge so the bot is not kicked with
     * "was kicked due to keepalive timeout". The embedded connection never
     * receives or sends real network bytes, so instead of a network round-trip
     * we peek the pending challenge and acknowledge it through the listener's
     * own packet handler (exactly what a decoded
     * {@link ServerboundKeepAlivePacket} would do). Runs on the bot's region
     * thread, the same thread that owns its connection.
     */
    private void acknowledgeKeepAlive() {
        if (keepAliveQueue == null || nmsPlayer == null || nmsPlayer.connection == null) return;
        try {
            if (keepAlivePeek == null) {
                keepAlivePeek = keepAliveQueue.getClass().getMethod("peek");
            }
            Object pending = keepAlivePeek.invoke(keepAliveQueue);
            if (pending == null) return;
            if (keepAliveChallengeId == null) {
                keepAliveChallengeId = pending.getClass().getMethod("challengeId");
            }
            long challenge = (Long) keepAliveChallengeId.invoke(pending);
            nmsPlayer.connection.handleKeepAlive(new ServerboundKeepAlivePacket(challenge));
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
            if (nmsPlayer != null && nmsPlayer.connection != null)
                nmsPlayer.connection.disconnect(Component.literal("Stress bot removed"));
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
