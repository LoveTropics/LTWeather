package weather2.weathersystem;

import net.minecraft.world.World;
import weather2.weathersystem.wind.WindManager;

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
	
	public World getWorld() {
		return null;
	}
	
	public void tick() {
		World world = getWorld();
		if (world != null) {
			windMan.tick();
		}
	}

	public WindManager getWindManager() {
		return this.windMan;
	}
}
