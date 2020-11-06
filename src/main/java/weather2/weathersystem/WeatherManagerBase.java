package weather2.weathersystem;

import net.minecraft.world.World;
import weather2.weathersystem.wind.WindManager;

import javax.annotation.Nullable;

public class WeatherManagerBase {

	//shared stuff, stormfront list
	
	public int dim;
	
	//wind
	public WindManager windMan;

	public WeatherManagerBase(int parDim) {
		dim = parDim;
		windMan = new WindManager(this);
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
			windMan.tick();
			return true;
		}
		return false;
	}

	public WindManager getWindManager() {
		return this.windMan;
	}
}
