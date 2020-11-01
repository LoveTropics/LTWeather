package weather2.weathersystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class WeatherManagerClient extends WeatherManagerBase {

	//data for client, stormfronts synced from server
	
	//new for 1.10.2, replaces world.weatherEffects use
	public List<Particle> listWeatherEffectedParticles = new ArrayList<Particle>();

	public WeatherManagerClient(int parDim) {
		super(parDim);
	}
	
	@Override
	public World getWorld() {
		return Minecraft.getInstance().world;
	}

	public void nbtSyncFromServer(CompoundNBT parNBT) {
		String command = parNBT.getString("command");
		
		if (command.equals("syncWindUpdate")) {
			//Weather.dbg("updating client side wind");
			
			CompoundNBT nbt = parNBT.getCompound("data");
			
			windMan.nbtSyncFromServer(nbt);
		} else if (command.equals("syncWeatherUpdate")) {
			//Weather.dbg("updating client side wind");
			
			//NBTTagCompound nbt = parNBT.getCompound("data");
			isVanillaRainActiveOnServer = parNBT.getBoolean("isVanillaRainActiveOnServer");
			isVanillaThunderActiveOnServer = parNBT.getBoolean("isVanillaThunderActiveOnServer");
			vanillaRainTimeOnServer = parNBT.getInt("vanillaRainTimeOnServer");
			//windMan.nbtSyncFromServer(nbt);
		}
	}

	@Override
	public void reset() {
		super.reset();
		listWeatherEffectedParticles.clear();
	}
}
