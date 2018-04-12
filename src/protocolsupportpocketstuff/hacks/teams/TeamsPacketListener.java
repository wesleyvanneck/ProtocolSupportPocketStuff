package protocolsupportpocketstuff.hacks.teams;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.server.v1_12_R1.PacketPlayOutScoreboardTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import protocolsupport.api.Connection;
import protocolsupport.api.ProtocolSupportAPI;
import protocolsupport.api.ProtocolVersion;
import protocolsupport.protocol.serializer.MiscSerializer;
import protocolsupport.protocol.serializer.StringSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupport.protocol.typeremapper.pe.PEPacketIDs;
import protocolsupport.protocol.utils.datawatcher.DataWatcherObject;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectString;
import protocolsupport.utils.CollectionsUtils;
import protocolsupportpocketstuff.ProtocolSupportPocketStuff;
import protocolsupportpocketstuff.api.util.PocketCon;
import protocolsupportpocketstuff.packet.play.EntityDataPacket;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TeamsPacketListener extends Connection.PacketListener {
	private Connection con;
	private ConcurrentHashMap<String, Integer> cachedUsers = new ConcurrentHashMap<>();
	private Cache<String, CachedTeam> cachedTeams = CacheBuilder.newBuilder().softValues().build();
	private static Field TEAM_NAME = null;
	private static Field UPDATE_MODE = null;
	private static Field TEAM_PREFIX = null;
	private static Field TEAM_SUFFIX = null;
	private static Field TEAM_ENTITIES = null;

	private static final String META_KEY = "__PSPS_TEAMSPACKETLISTENER";

	public TeamsPacketListener(ProtocolSupportPocketStuff plugin, Connection con) {
		this.con = con;
		con.addMetadata(META_KEY, this);
	}

	public static TeamsPacketListener get(Player p) {
		return (TeamsPacketListener) ProtocolSupportAPI.getConnection(p).getMetadata(META_KEY);
	}

	public static class UpdateExecutor implements Listener {

		private final ProtocolSupportPocketStuff plugin;

		public UpdateExecutor(ProtocolSupportPocketStuff plugin) {
			this.plugin = plugin;
		}

		@EventHandler(priority = EventPriority.LOW)
		public void handle(PlayerJoinEvent event) {
			Connection conn = ProtocolSupportAPI.getConnection(event.getPlayer());
			if (!(conn.getVersion() == ProtocolVersion.MINECRAFT_PE)) {
				return;
			}
			if (conn.getMetadata("_PE_TRANSFER_") == null) {
				return;
			}
			Bukkit.getScheduler().runTaskLater(plugin, () -> get(event.getPlayer()).updateAll(), 20);
		}
	}

	static {
		try {
			TEAM_NAME = PacketPlayOutScoreboardTeam.class.getDeclaredField("a");
			UPDATE_MODE = PacketPlayOutScoreboardTeam.class.getDeclaredField("i");
			TEAM_PREFIX = PacketPlayOutScoreboardTeam.class.getDeclaredField("c");
			TEAM_SUFFIX = PacketPlayOutScoreboardTeam.class.getDeclaredField("d");
			TEAM_ENTITIES = PacketPlayOutScoreboardTeam.class.getDeclaredField("h");

			TEAM_NAME.setAccessible(true);
			UPDATE_MODE.setAccessible(true);
			TEAM_PREFIX.setAccessible(true);
			TEAM_SUFFIX.setAccessible(true);
			TEAM_ENTITIES.setAccessible(true);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
	}

	public void updateAll() {
		cachedTeams.asMap().values().forEach(cachedTeam -> cachedTeam.updatePlayers(this));
	}

	@SuppressWarnings({ "unchecked", "unlikely-arg-type" })
	@Override
	public void onPacketSending(PacketEvent event) {
		super.onPacketSending(event);

		if (!(event.getPacket() instanceof PacketPlayOutScoreboardTeam))
			return;

		PacketPlayOutScoreboardTeam packet = (PacketPlayOutScoreboardTeam) event.getPacket();

		String teamName = (String) get(TEAM_NAME, packet);
		int mode = getInt(UPDATE_MODE, packet);

		if (mode == 0) { // create
			String prefix = (String) get(TEAM_PREFIX, packet);
			String suffix = (String) get(TEAM_SUFFIX, packet);
			Collection<String> entities = (Collection<String>) get(TEAM_ENTITIES, packet);

			CachedTeam team = new CachedTeam(prefix, suffix);

			for (String entity : entities) {
				team.getPlayers().add(entity);
			}

			cachedTeams.put(teamName, team);
			team.updatePlayers(this);
			return;
		}
		if (mode == 1) { // delete
			if (cachedTeams.asMap().containsKey(teamName)) {
				CachedTeam team = cachedTeams.asMap().get(teamName);
				team.removePlayers(team.players, this);
				cachedTeams.asMap().remove(teamName);
			}
		}
		if (mode == 2) { // update
			if (cachedTeams.asMap().containsKey(teamName)) {
				CachedTeam team = cachedTeams.asMap().get(teamName);
				String prefix = (String) get(TEAM_PREFIX, packet);
				String suffix = (String) get(TEAM_SUFFIX, packet);
				team.setPrefix(prefix);
				team.setSuffix(suffix);
				team.updatePlayers(this);
			}
		}
		if (mode == 3) { // add players
			if (cachedTeams.asMap().containsKey(teamName)) {
				Collection<String> entities = (Collection<String>) get(TEAM_ENTITIES, packet);
				CachedTeam team = cachedTeams.asMap().get(teamName);

				for (String entity : entities) {
					team.getPlayers().add(entity);
				}
				team.updatePlayers(this);
			}
		}
		if (mode == 4) { // remove players
			if (cachedTeams.asMap().containsKey(teamName)) {
				Collection<String> entities = (Collection<String>) get(TEAM_ENTITIES, packet);
				CachedTeam team = cachedTeams.asMap().get(teamName);
				team.removePlayers(entities, this);
			}
		}
	}

	@Override
	public void onRawPacketSending(RawPacketEvent event) {
		super.onRawPacketSending(event);

		ByteBuf data = event.getData();
		int packetId = VarNumberSerializer.readVarInt(data);

		if (packetId != PEPacketIDs.SPAWN_PLAYER)
			return;

		data.readByte();
		data.readByte();

		MiscSerializer.readUUID(data);
		String name = StringSerializer.readString(data, con.getVersion());
		long entityId = VarNumberSerializer.readSVarLong(data);

		cachedUsers.put(name, (int) entityId);
	}

	public static Object get(Field field, Object source) {
		try {
			return field.get(source);
		} catch (Exception e) {
			e.printStackTrace();
		}
		throw new RuntimeException("Error while getting field \"" + field.getName() + "\" from " + source + "!");
	}

	public static int getInt(Field field, Object source) {
		try {
			return field.getInt(source);
		} catch (Exception e) {
			e.printStackTrace();
		}
		throw new RuntimeException("Error while getting field \"" + field.getName() + "\" from " + source + "!");
	}

	static class CachedTeam {
		private String prefix;
		private String suffix;
		private Set<String> players = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

		public CachedTeam(String prefix, String suffix) {
			this.prefix = prefix;
			this.suffix = suffix;
		}

		public void updatePlayers(TeamsPacketListener listener) {
			for (String player : players) {
				if (listener.cachedUsers.containsKey(player)) {
					Integer entityId = listener.cachedUsers.get(player);
					sendNameUpdate(listener.con, entityId, getPrefix() + player + getSuffix());
				}
			}
		}

		public void removePlayers(Collection<String> players, TeamsPacketListener listener) {
			for (String player : players) {
				this.players.remove(player);

				if (listener.cachedUsers.containsKey(player)) {
					Integer entityId = listener.cachedUsers.get(player);
					sendNameUpdate(listener.con, entityId, player);
				}
			}
		}

		public void sendNameUpdate(Connection connection, int entityId, String name) {
			CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata = new CollectionsUtils.ArrayMap<>(76);
			metadata.put(4, new DataWatcherObjectString(name));
			EntityDataPacket packet = new EntityDataPacket(entityId, metadata);
			PocketCon.sendPocketPacket(connection, packet);
		}

		public String getPrefix() {
			return prefix;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}

		public String getSuffix() {
			return suffix;
		}

		public void setSuffix(String suffix) {
			this.suffix = suffix;
		}

		public Set<String> getPlayers() {
			return players;
		}
	}
}
