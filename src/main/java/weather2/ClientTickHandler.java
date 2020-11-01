package weather2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.CloudOption;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import weather2.client.SceneEnhancer;
import weather2.config.ConfigMisc;
import weather2.util.WindReader;
import weather2.weathersystem.WeatherManagerClient;

public class ClientTickHandler
{
	public static World lastWorld;
	
	public static WeatherManagerClient weatherManager;
	public static SceneEnhancer sceneEnhancer;

	public static ClientConfigData clientConfigData;
	
	public float smoothAngle = 0;

	public float smoothAngleRotationalVelAccel = 0;

	public float smoothAngleAdj = 0.1F;

	public int prevDir = 0;

	public ClientTickHandler() {
		//this constructor gets called multiple times when created from proxy, this prevents multiple inits
		if (sceneEnhancer == null) {
			sceneEnhancer = new SceneEnhancer();
			(new Thread(sceneEnhancer, "Weather2 Scene Enhancer")).start();
		}
		clientConfigData = new ClientConfigData();
	}

    public void onTickInGame()
    {
		if (ConfigMisc.Client_PotatoPC_Mode) return;

        Minecraft mc = Minecraft.getInstance();
        World world = mc.world;

		if (world != null) {
			checkClientWeather();

			weatherManager.tick();

			if (!clientConfigData.Aesthetic_Only_Mode && ConfigMisc.Misc_ForceVanillaCloudsOff && world.getDimension().getType().getId() == 0) {
				mc.gameSettings.cloudOption = CloudOption.OFF;
			}

			ClientWeather weather = ClientWeather.get();

			//TODO: split logic up a bit better for this, if this is set to false mid sandstorm, fog is stuck on,
			// with sandstorms and other things it might not represent the EZ config option
			// Make sure we're in STT, TODO make this more efficient
			if (weather.hasWeather()) {
				sceneEnhancer.tickClient();
			}

			//TODO: evaluate if best here
			float windDir = WindReader.getWindAngle(world, null);
			float windSpeed = WindReader.getWindSpeed(world, null);

			//windDir = 0;

			float diff = Math.abs(windDir - smoothAngle)/* - 180*/;

			if (true && diff > 10/* && (smoothAngle > windDir - give || smoothAngle < windDir + give)*/) {

				if (smoothAngle > 180) smoothAngle -= 360;
				if (smoothAngle < -180) smoothAngle += 360;

				float bestMove = MathHelper.wrapDegrees(windDir - smoothAngle);

				smoothAngleAdj = windSpeed;//0.2F;

				if (Math.abs(bestMove) < 180/* - (angleAdjust * 2)*/) {
					float realAdj = smoothAngleAdj;//Math.max(smoothAngleAdj, Math.abs(bestMove));

					if (realAdj * 2 > windSpeed) {
						if (bestMove > 0) {
							smoothAngleRotationalVelAccel -= realAdj;
							if (prevDir < 0) {
								smoothAngleRotationalVelAccel = 0;
							}
							prevDir = 1;
						} else if (bestMove < 0) {
							smoothAngleRotationalVelAccel += realAdj;
							if (prevDir > 0) {
								smoothAngleRotationalVelAccel = 0;
							}
							prevDir = -1;
						}
					}

					if (smoothAngleRotationalVelAccel > 0.3 || smoothAngleRotationalVelAccel < -0.3) {
						smoothAngle += smoothAngleRotationalVelAccel * 0.3F;
					} else {
						//smoothAngleRotationalVelAccel *= 0.9F;
					}

					smoothAngleRotationalVelAccel *= 0.80F;
				}
			}
		} else {
			resetClientWeather();
		}

    }

    public static void resetClientWeather() {
		if (weatherManager != null) {
			Weather.dbg("Weather2: Detected old WeatherManagerClient with unloaded world, clearing its data");
			weatherManager.reset();
			weatherManager = null;
		}

		ClientWeather.reset();
	}
	
    public static void checkClientWeather() {

    	try {
			World world = Minecraft.getInstance().world;
    		if (weatherManager == null || world != lastWorld) {
    			init(world);
        	}
    	} catch (Exception ex) {
    		Weather.dbg("Weather2: Warning, client received packet before it was ready to use, and failed to init client weather due to null world");
    	}
    }
    
    public static void init(World world) {
		//this is generally triggered when they teleport to another dimension
		if (weatherManager != null) {
			Weather.dbg("Weather2: Detected old WeatherManagerClient with active world, clearing its data");
			weatherManager.reset();
		}

		Weather.dbg("Weather2: Initializing WeatherManagerClient for client world and requesting full sync");

    	lastWorld = world;
    	weatherManager = new WeatherManagerClient(world.getDimension().getType().getId());

		//request a full sync from server
		CompoundNBT data = new CompoundNBT();
		data.putString("command", "syncFull");
		data.putString("packetCommand", "WeatherData");
		//Weather.eventChannel.sendToServer(PacketHelper.getNBTPacket(data, Weather.eventChannelName));
		WeatherNetworking.HANDLER.sendToServer(new PacketNBTFromClient(data));
    }
}
