package weather2.weathersystem;

import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import weather2.weathersystem.wind.WindManager;

import javax.annotation.Nullable;

public abstract class WeatherManager {
	public final DimensionType dimension;
	public final WindManager windMan = new WindManager(this);

	public WeatherManager(DimensionType dimension) {
		this.dimension = dimension;
	}

	public void reset() {
		windMan.reset();
	}

	@Nullable
	public World getWorld() {
		return null;
	}
	
	public boolean tick() {
		World world = getWorld();
		if (world != null) {
			windMan.tick(world);
			return true;
		}
		return false;
	}

	public WindManager getWindManager() {
		return this.windMan;
	}
}
