package weather2.weathersystem;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WeatherManagerClient extends WeatherManager {
	public WeatherManagerClient(DimensionType dimension) {
		super(dimension);
	}
	
	@Override
	public World getWorld() {
		return Minecraft.getInstance().world;
	}
}
