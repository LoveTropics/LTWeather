package weather2.util;

import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import weather2.ClientTickHandler;
import weather2.ServerTickHandler;
import weather2.weathersystem.WeatherManagerBase;

public class WindReader {
	public static float getWindAngle(World world) {
		WeatherManagerBase weather = getWeatherManagerFor(world);
		return weather != null ? weather.windMan.getWindAngle() : 0;
	}
	
	public static float getWindSpeed(World world) {
		WeatherManagerBase weather = getWeatherManagerFor(world);
		return weather != null ? weather.windMan.getWindSpeed() : 0;
	}

	private static WeatherManagerBase getWeatherManagerFor(World world) {
		if (world.isRemote) {
			return getWeatherManagerClient();
		} else {
			return ServerTickHandler.lookupDimToWeatherMan.get(world.getDimension().getType().getId());
		}
	}

	@OnlyIn(Dist.CLIENT)
	private static WeatherManagerBase getWeatherManagerClient() {
		return ClientTickHandler.weatherManager;
	}
}
