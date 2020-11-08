package weather2.weathersystem;

import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import weather2.weathersystem.wind.WindManager;

public abstract class WeatherManager {
	public final DimensionType dimension;
	public final WindManager wind = new WindManager(this);

	public WeatherManager(DimensionType dimension) {
		this.dimension = dimension;
	}

	public abstract World getWorld();

	public void tick() {
		wind.tick(getWorld());
	}

	public WindManager getWindManager() {
		return this.wind;
	}
}
