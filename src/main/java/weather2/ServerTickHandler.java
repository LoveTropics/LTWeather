package weather2;

import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import weather2.util.WeatherUtil;
import weather2.weathersystem.WeatherManagerBase;
import weather2.weathersystem.WeatherManagerServer;

import java.util.ArrayList;
import java.util.HashMap;

@Mod.EventBusSubscriber(modid = Weather.MODID)
public class ServerTickHandler
{   
	//Used for easy iteration, could be replaced
    public static ArrayList<WeatherManagerServer> listWeatherMans;
    
    //Main lookup method for dim to weather systems
    public static HashMap<Integer, WeatherManagerServer> lookupDimToWeatherMan;
    
	public static World lastWorld;

    static {
    	
    	listWeatherMans = new ArrayList<>();
    	lookupDimToWeatherMan = new HashMap<>();
    	
    }

	@SubscribeEvent
	public static void tickServer(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			if (ServerLifecycleHooks.getCurrentServer() == null) {
				return;
			}

			World world = WeatherUtil.getWorld(0);

			if (world != null && lastWorld != world) {
				lastWorld = world;
			}

			Iterable<ServerWorld> worlds = WeatherUtil.getWorlds();

			for (ServerWorld worldEntry : worlds) {
				if (!lookupDimToWeatherMan.containsKey(worldEntry.getDimension().getType().getId())) {
					if (true) {// TODO minigames worldEntry.getDimension().getType() == TropicraftWorldUtils.SURVIVE_THE_TIDE_DIMENSION/*WeatherUtilConfig.listDimensionsWeather.contains(worldEntry.getDimension().getType().getId())*/) {
						addWorldToWeather(worldEntry.getDimension().getType().getId());
					}
				}

				//tick it
				WeatherManagerServer wms = lookupDimToWeatherMan.get(worldEntry.getDimension().getType().getId());
				if (wms != null) {
					lookupDimToWeatherMan.get(worldEntry.getDimension().getType().getId()).tick();
				}
			}
		}
    }
    
    //must only be used when world is active, soonest allowed is TickType.WORLDLOAD
    public static void addWorldToWeather(int dim) {
    	Weather.dbg("Registering Weather2 manager for dim: " + dim);
    	WeatherManagerServer wm = new WeatherManagerServer(dim);
    	
    	listWeatherMans.add(wm);
    	lookupDimToWeatherMan.put(dim, wm);
    }
    
    public static void removeWorldFromWeather(int dim) {
    	Weather.dbg("Weather2: Unregistering manager for dim: " + dim);
    	WeatherManagerServer wm = lookupDimToWeatherMan.get(dim);
    	
    	if (wm != null) {
	    	listWeatherMans.remove(wm);
	    	lookupDimToWeatherMan.remove(dim);
    	}
    }

    public static void reset() {
		Weather.dbg("Weather2: ServerTickHandler resetting");
		for (WeatherManagerBase wm : listWeatherMans) {
			int dim = wm.dim;
			if (lookupDimToWeatherMan.containsKey(dim)) {
				removeWorldFromWeather(dim);
			}
		}

    	//should never happen
    	if (listWeatherMans.size() > 0 || lookupDimToWeatherMan.size() > 0) {
    		Weather.dbg("Weather2: reset state failed to manually clear lists, listWeatherMans.size(): " + listWeatherMans.size() + " - lookupDimToWeatherMan.size(): " + lookupDimToWeatherMan.size() + " - forcing a full clear of lists");
    		listWeatherMans.clear();
    		lookupDimToWeatherMan.clear();
    	}
    }
    
    public static WeatherManagerServer getWeatherSystemForDim(int dimID) {
    	return lookupDimToWeatherMan.get(dimID);
    }
}
