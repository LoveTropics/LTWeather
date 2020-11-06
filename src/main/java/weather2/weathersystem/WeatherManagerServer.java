package weather2.weathersystem;

import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import weather2.util.WeatherUtil;

public class WeatherManagerServer extends WeatherManager {
	public WeatherManagerServer(DimensionType dimension) {
		super(dimension);
	}

	@Override
	public World getWorld() {
		return WeatherUtil.getWorldOrNull(dimension);
	}
}
