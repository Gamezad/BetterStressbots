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
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

public class FakePlayerImpl implements IFakePlayer {

    private final ServerPlayer nmsPlayer;
    private final double spawnX, spawnY, spawnZ;
    private double targetX, targetY, targetZ;
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
        pickNewTarget();
    }

    private void pickNewTarget() {
        double angle = random.nextDouble() * Math.PI * 2;
        double dist  = random.nextDouble() * radius;
        targetX = spawnX + Math.cos(angle) * dist;
        targetZ = spawnZ + Math.sin(angle) * dist;
        targetY = (mode == BotManager.GroundMode.WALK)
                ? spawnY
                : Math.max(64, Math.min(250, spawnY + random.nextDouble() * 100 + 20));
    }

    @Override
    public void tick() {
        if (nmsPlayer == null || !nmsPlayer.isAlive()) return;

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

        nmsPlayer.snapTo(newX, newY, newZ, nmsPlayer.getYRot(), nmsPlayer.getXRot()); // v1_21_11: snapTo
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
        try { nmsPlayer.getBukkitEntity().chat(message); } catch (Exception ignored) {}
    }

    @Override
    public void teleportTo(Location loc) {
        if (nmsPlayer == null) return;
        gotoTarget = null;
        double y = (mode == BotManager.GroundMode.WALK) ? getSurfaceY(loc.getX(), loc.getZ()) : loc.getY();
        nmsPlayer.snapTo(loc.getX(), y, loc.getZ(), loc.getYaw(), loc.getPitch()); // v1_21_11: snapTo
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
    public boolean isAlive() { return nmsPlayer != null && nmsPlayer.isAlive(); }

    @Override
    public String getName() {
        return nmsPlayer != null ? nmsPlayer.getGameProfile().name() : "unknown"; // v1_21_11: name()
    }
}