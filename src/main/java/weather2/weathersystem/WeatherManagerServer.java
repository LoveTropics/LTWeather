package weather2.weathersystem;

import net.minecraft.world.World;
import weather2.util.WeatherUtil;

public class WeatherManagerServer extends WeatherManagerBase {
	public WeatherManagerServer(int parDim) {
		super(parDim);
	}

	@Override
	public World getWorld() {
		return WeatherUtil.getWorld(dim);
	}
}
