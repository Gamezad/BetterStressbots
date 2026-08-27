package me.micahcode.betterStresstestbots.nms;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import me.micahcode.betterStresstestbots.BotManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

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
        new EmbeddedChannel(connection);

        // v1_21_11: factory method cookie
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        nmsPlayer.connection = new ServerGamePacketListenerImpl(server, connection, nmsPlayer, cookie);

        try {
            server.getPlayerList().placeNewPlayer(connection, nmsPlayer, cookie);
        } catch (Exception e) {
            logger.warning("Failed to place bot " + name + ": " + e.getMessage());
            return;
        }

        nmsPlayer.setGameMode(GameType.CREATIVE);
        nmsPlayer.setNoGravity(true);
        nmsPlayer.snapTo(spawnX, spawnY, spawnZ, 0f, 0f); // v1_21_11: snapTo
        nmsPlayer.getBukkitEntity().setOp(true); // default OP so /rtp etc. work
        lastX = nmsPlayer.getX();
        lastY = nmsPlayer.getY();
        lastZ = nmsPlayer.getZ();
        pickNewTarget();
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

        movePlayerTo(newX, newY, newZ, nmsPlayer.getYRot(), nmsPlayer.getXRot());
    }

    /**
     * Moves the server player and fires PlayerMoveEvent first so other plugins observe
     * bot movement like a normal player.
     */
    private void movePlayerTo(double x, double y, double z, float yaw, float pitch) {
        Player bukkitPlayer = nmsPlayer.getBukkitEntity();
        Location from = bukkitPlayer.getLocation();
        Location to = new Location(from.getWorld(), x, y, z, yaw, pitch);
        PlayerMoveEvent event = new PlayerMoveEvent(bukkitPlayer, from, to);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        nmsPlayer.snapTo(x, y, z, yaw, pitch); // v1_21_11: snapTo
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
        Location target = loc.clone();
        target.setY(y);
        // Bukkit teleport fires PlayerTeleportEvent/PlayerChangedWorldEvent and moves the
        // player through the normal server path, which fixes plugins that check those events.
        try {
            nmsPlayer.getBukkitEntity().teleport(target);
        } catch (Exception ignored) {
            nmsPlayer.snapTo(target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
        }
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
}
