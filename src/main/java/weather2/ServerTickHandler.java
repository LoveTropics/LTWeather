package weather2;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.IWorld;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import weather2.weathersystem.WeatherManagerServer;

import java.util.Iterator;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Weather.MODID)
public class ServerTickHandler {
	private static final Map<DimensionType, WeatherManagerServer> MANAGERS = new Reference2ObjectOpenHashMap<>();

	@SubscribeEvent
	public static void onWorldLoad(WorldEvent.Load event) {
		IWorld world = event.getWorld();
		if (!world.isRemote()) {
			DimensionType dimension = world.getDimension().getType();
			MANAGERS.put(dimension, new WeatherManagerServer(dimension));
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
			Iterator<WeatherManagerServer> iterator = MANAGERS.values().iterator();
			while (iterator.hasNext()) {
				WeatherManagerServer manager = iterator.next();
				if (!manager.tick()) {
					iterator.remove();
				}
			}
		}
	}

	public static void reset() {
		MANAGERS.clear();
	}

	public static WeatherManagerServer getWeatherSystemForDim(DimensionType dimension) {
		return MANAGERS.get(dimension);
	}
}
