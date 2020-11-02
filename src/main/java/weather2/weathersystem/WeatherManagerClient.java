package weather2.weathersystem;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WeatherManagerClient extends WeatherManagerBase {
	public WeatherManagerClient(int parDim) {
		super(parDim);
	}
	
	@Override
	public World getWorld() {
		return Minecraft.getInstance().world;
	}
}
