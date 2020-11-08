package weather2;

import com.lovetropics.minigames.common.minigames.weather.RainType;
import com.lovetropics.minigames.common.minigames.weather.WeatherController;
import com.lovetropics.minigames.common.minigames.weather.WeatherState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;

// TODO: Consolidate with WeatherManager
@Mod.EventBusSubscriber(modid = Weather.MODID)
public final class ServerWeatherController implements WeatherController {
	public static final int UPDATE_INTERVAL = 20;

	private final PacketDistributor.PacketTarget packetTarget;

	private int ticks;
	private boolean dirty;

	private final WeatherState state = new WeatherState();

	ServerWeatherController(ServerWorld world) {
		DimensionType dimension = world.getDimension().getType();
		this.packetTarget = PacketDistributor.DIMENSION.with(() -> dimension);
	}

	@Override
	public void onPlayerJoin(ServerPlayerEntity player) {
		WeatherNetworking.HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new UpdateWeatherPacket(this.state));
	}

	@Override
	public void tick() {
		if (this.dirty && this.ticks++ % UPDATE_INTERVAL == 0) {
			this.dirty = false;
			WeatherNetworking.HANDLER.send(this.packetTarget, new UpdateWeatherPacket(this.state));
		}
	}

	@Override
	public void setRain(float amount, RainType type) {
		if (amount != this.state.rainAmount || type != this.state.rainType) {
			this.state.rainAmount = amount;
			this.state.rainType = type;
			this.dirty = true;
		}
	}

	@Override
	public void setWind(float speed) {
		if (speed != this.state.windSpeed) {
			this.state.windSpeed = speed;
			this.dirty = true;
		}
	}

	@Override
	public void setHeatwave(boolean heatwave) {
		if (heatwave != this.state.heatwave) {
			this.state.heatwave = heatwave;
			this.dirty = true;
		}
	}

	@Override
	public float getRainAmount() {
		return this.state.rainAmount;
	}

	@Override
	public RainType getRainType() {
		return this.state.rainType;
	}

	@Override
	public float getWindSpeed() {
		return this.state.windSpeed;
	}

	@Override
	public boolean isHeatwave() {
		return this.state.heatwave;
	}
}
