package weather2.weathersystem;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.lovetropics.minigames.common.core.game.weather.WeatherController;
import com.lovetropics.minigames.common.core.game.weather.WeatherControllerManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.PacketDistributor;
import weather2.*;
import weather2.config.ConfigSand;
import weather2.util.CachedNBTTagCompound;
import weather2.util.WeatherUtilBlock;
import weather2.weathersystem.storm.WeatherObject;
import weather2.weathersystem.storm.WeatherObjectSandstorm;
import weather2.weathersystem.wind.WindManager;

import javax.annotation.Nullable;
import java.util.*;

public class WeatherManagerServer extends WeatherManager {
	private final ServerWorld world;

	public WeatherManagerServer(ServerWorld world) {
		super(world.getDimensionKey());
		this.world = world;
	}

	@Override
	public World getWorld() {
		return world;
	}

	@Override
	public void tick() {
		super.tick();

		WeatherController controller = WeatherControllerManager.forWorld(world);
		if (controller != null) {
			if (controller.isSnowstorm()) {
				tickSnowstorm();
			} else if (controller.isSandstorm()) {
				tickSandstorm();
			}
		}

		if (world != null) {
			WindManager windMan = getWindManager();

			//sync wind
			if (world.getGameTime() % 60 == 0) {
				syncWindUpdate(windMan);
			}
		}
	}

	public void tickSnowstorm() {

		World world = getWorld();
		WindManager windMan = getWindManager();
		Random rand = world.rand;
		WeatherController controller = WeatherControllerManager.forWorld((ServerWorld) world);
		if (controller == null) return;

		float angle = windMan.getWindAngle();

		int delay = controller.getConfig().getSnowstormBuildupTickRate();

		if (world.getGameTime() % delay == 0) {
			//TODO: switch to AT when not using borked dev env
			Long2ObjectLinkedOpenHashMap<ChunkHolder> loadedChunksImmutable = ObfuscationReflectionHelper.getPrivateValue(ChunkManager.class, ((ServerWorld) world).getChunkProvider().chunkManager, "immutableLoadedChunks");

			//List<ChunkHolder> list = Lists.newArrayList(((ServerWorld)world).getChunkProvider().chunkManager.getLoadedChunksIterable());
			List<ChunkHolder> list = Lists.newArrayList(Iterables.unmodifiableIterable(loadedChunksImmutable.values()));
			Collections.shuffle(list);
			list.forEach((p_241099_7_) -> {
				Optional<Chunk> optional = p_241099_7_.getTickingFuture().getNow(ChunkHolder.UNLOADED_CHUNK).left();
				if (optional.isPresent()) {
					for (int i = 0; i < 10; i++) {
						BlockPos blockPos = new BlockPos((optional.get().getPos().x * 16) + rand.nextInt(16), 0, (optional.get().getPos().z * 16) + rand.nextInt(16));
						int y = WeatherUtilBlock.getPrecipitationHeightSafe(world, blockPos).getY();
						Vector3d pos = new Vector3d(blockPos.getX(), y, blockPos.getZ());
						int maxBlockStackingAllowed = controller.getConfig().getSnowstormMaxStackable();
						WeatherUtilBlock.fillAgainstWallSmoothly(world, pos, angle, 15, 2, Blocks.SNOW, maxBlockStackingAllowed);
					}
				}
			});
		}
	}

	public void tickSandstorm() {

		World world = getWorld();
		WindManager windMan = getWindManager();
		Random rand = world.rand;
		WeatherController controller = WeatherControllerManager.forWorld((ServerWorld) world);
		if (controller == null) return;

		float angle = windMan.getWindAngle();

		int delay = controller.getConfig().getSandstormBuildupTickRate();

		if (world.getGameTime() % delay == 0) {
			//TODO: switch to AT when not using borked dev env
			Long2ObjectLinkedOpenHashMap<ChunkHolder> loadedChunksImmutable = ObfuscationReflectionHelper.getPrivateValue(ChunkManager.class, ((ServerWorld) world).getChunkProvider().chunkManager, "immutableLoadedChunks");

			//List<ChunkHolder> list = Lists.newArrayList(((ServerWorld)world).getChunkProvider().chunkManager.getLoadedChunksIterable());
			List<ChunkHolder> list = Lists.newArrayList(Iterables.unmodifiableIterable(loadedChunksImmutable.values()));
			Collections.shuffle(list);
			list.forEach((p_241099_7_) -> {
				Optional<Chunk> optional = p_241099_7_.getTickingFuture().getNow(ChunkHolder.UNLOADED_CHUNK).left();
				if (optional.isPresent()) {
					for (int i = 0; i < 10; i++) {
						BlockPos blockPos = new BlockPos((optional.get().getPos().x * 16) + rand.nextInt(16), 0, (optional.get().getPos().z * 16) + rand.nextInt(16));
						int y = WeatherUtilBlock.getPrecipitationHeightSafe(world, blockPos).getY();
						Vector3d pos = new Vector3d(blockPos.getX(), y, blockPos.getZ());
						int maxBlockStackingAllowed = controller.getConfig().getSandstormMaxStackable();
						WeatherUtilBlock.fillAgainstWallSmoothly(world, pos, angle, 15, 2, WeatherBlocks.blockSandLayer, maxBlockStackingAllowed);
					}
				}
			});
		}
	}

	public void syncStormRemove(WeatherObject parStorm) {
		//packets
		CompoundNBT data = new CompoundNBT();
		data.putString("packetCommand", "WeatherData");
		data.putString("command", "syncStormRemove");
		parStorm.nbtSyncForClient();
		data.put("data", parStorm.getNbtCache().getNewNBT());
		//data.put("data", parStorm.nbtSyncForClient(new NBTTagCompound()));
		//fix for client having broken states
		data.getCompound("data").putBoolean("removed", true);
		//Weather.eventChannel.sendToDimension(PacketHelper.getNBTPacket(data, Weather.eventChannelName), getWorld().getDimension().getType().getId());
		WeatherNetworking.HANDLER.send(PacketDistributor.DIMENSION.with(() -> getWorld().getDimensionKey()), new PacketNBTFromServer(data));
	}

	public void syncWindUpdate(WindManager parManager) {
		//packets
		CompoundNBT data = new CompoundNBT();
		data.putString("packetCommand", "WeatherData");
		data.putString("command", "syncWindUpdate");
		data.put("data", parManager.nbtSyncForClient());
		WeatherNetworking.HANDLER.send(PacketDistributor.DIMENSION.with(() -> getWorld().getDimensionKey()), new PacketNBTFromServer(data));
	}
}
