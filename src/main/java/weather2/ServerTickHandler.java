package weather2;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.IWorld;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import weather2.weathersystem.WeatherManagerServer;

import java.util.Map;

@Mod.EventBusSubscriber(modid = Weather.MODID)
public class ServerTickHandler {
	private static final Map<DimensionType, WeatherManagerServer> MANAGERS = new Reference2ObjectOpenHashMap<>();

	@SubscribeEvent
	public static void onWorldLoad(WorldEvent.Load event) {
		IWorld world = event.getWorld();
		if (!world.isRemote()) {
			DimensionType dimension = world.getDimension().getType();
			MANAGERS.put(dimension, new WeatherManagerServer((ServerWorld) world));
		}
	}

	@SubscribeEvent
	public static void onWorldUnload(WorldEvent.Unload event) {
		IWorld world = event.getWorld();
		if (!world.isRemote()) {
			MANAGERS.remove(world.getDimension().getType());
		}
	}

	@SubscribeEvent
	public static void tickServer(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			for (WeatherManagerServer manager : MANAGERS.values()) {
				manager.tick();
			}
		}
	}

	public static WeatherManagerServer getWeatherManagerFor(DimensionType dimension) {
		return MANAGERS.get(dimension);
	}
}
