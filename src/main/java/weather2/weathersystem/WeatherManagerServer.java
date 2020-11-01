package weather2.weathersystem;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;
import weather2.PacketNBTFromServer;
import weather2.WeatherNetworking;
import weather2.config.ConfigMisc;
import weather2.util.WeatherUtil;
import weather2.weathersystem.wind.WindManager;

import java.util.Random;

public class WeatherManagerServer extends WeatherManagerBase {
	//storm logic, syncing to client

	public WeatherManagerServer(int parDim) {
		super(parDim);
	}
	
	@Override
	public World getWorld() {
		return WeatherUtil.getWorld(dim);
	}
	
	@Override
	public void tick() {
		super.tick();
		
		World world = getWorld();
		tickWeatherCoverage();
		
		if (world != null) {
			//sync wind
			if (world.getGameTime() % 60 == 0) {
				syncWindUpdate(windMan);
			}
		}
	}

	public void tickWeatherCoverage() {
		World world = this.getWorld();
		if (world != null) {
			if (!ConfigMisc.overcastMode) {
				if (ConfigMisc.lockServerWeatherMode != -1) {
					world.getWorldInfo().setRaining(ConfigMisc.lockServerWeatherMode == 1);
					world.getWorldInfo().setThundering(ConfigMisc.lockServerWeatherMode == 1);
				}
			}

			if (world.getGameTime() % 40 == 0) {
				isVanillaRainActiveOnServer = getWorld().isRaining();
				isVanillaThunderActiveOnServer = getWorld().isThundering();
				vanillaRainTimeOnServer = getWorld().getWorldInfo().getRainTime();
				syncWeatherVanilla();
			}

			if (world.getGameTime() % 200 == 0) {
				Random rand = new Random();
				cloudIntensity += (float)((rand.nextDouble() * ConfigMisc.Cloud_Coverage_Random_Change_Amount) - (rand.nextDouble() * ConfigMisc.Cloud_Coverage_Random_Change_Amount));
				if (ConfigMisc.overcastMode && world.isRaining()) {
					cloudIntensity = 1;
				} else {
					if (cloudIntensity < ConfigMisc.Cloud_Coverage_Min_Percent / 100F) {
						cloudIntensity = (float) ConfigMisc.Cloud_Coverage_Min_Percent / 100F;
					} else if (cloudIntensity > ConfigMisc.Cloud_Coverage_Max_Percent / 100F) {
						cloudIntensity = (float) ConfigMisc.Cloud_Coverage_Max_Percent / 100F;
					}
				}
			}
		}
	}

	public void syncWindUpdate(WindManager parManager) {
		//packets
		CompoundNBT data = new CompoundNBT();
		data.putString("packetCommand", "WeatherData");
		data.putString("command", "syncWindUpdate");
		data.put("data", parManager.nbtSyncForClient());
		/*Weather.eventChannel.sendToDimension(PacketHelper.getNBTPacket(data, Weather.eventChannelName), getWorld().getDimension().getType().getId());
		FMLInterModComms.sendRuntimeMessage(Weather.instance, Weather.MODID, "weather.wind", data);*/

		WeatherNetworking.HANDLER.send(PacketDistributor.DIMENSION.with(() -> getWorld().getDimension().getType()), new PacketNBTFromServer(data));
	}

	public void syncWeatherVanilla() {
		CompoundNBT data = new CompoundNBT();
		data.putString("packetCommand", "WeatherData");
		data.putString("command", "syncWeatherUpdate");
		data.putBoolean("isVanillaRainActiveOnServer", isVanillaRainActiveOnServer);
		data.putBoolean("isVanillaThunderActiveOnServer", isVanillaThunderActiveOnServer);
		data.putInt("vanillaRainTimeOnServer", vanillaRainTimeOnServer);
		//Weather.eventChannel.sendToDimension(PacketHelper.getNBTPacket(data, Weather.eventChannelName), getWorld().getDimension().getType().getId());
		WeatherNetworking.HANDLER.send(PacketDistributor.DIMENSION.with(() -> getWorld().getDimension().getType()), new PacketNBTFromServer(data));
	}
}
