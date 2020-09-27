package weather2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.settings.CloudOption;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import weather2.client.SceneEnhancer;
import weather2.config.ConfigFoliage;
import weather2.config.ConfigMisc;
import weather2.util.WindReader;
import weather2.weathersystem.EntityRendererProxyWeather2Mini;
import weather2.weathersystem.WeatherManagerClient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ClientTickHandler
{
	
	public static World lastWorld;
	
	public static WeatherManagerClient weatherManager;
	public static MinigameWeatherInstanceClient minigameWeatherInstance;
	public static SceneEnhancer sceneEnhancer;
	//TODO: 1.14 uncomment
	//public static FoliageEnhancerShader foliageEnhancer;

	public static ClientConfigData clientConfigData;
	
	public boolean hasOpenedConfig = false;
	
	public Button configButton;

	//storing old reference to help retain any modifications done by other mods (dynamic surroundings asm)
	public GameRenderer oldRenderer;

	public float smoothAngle = 0;

	public float smoothAngleRotationalVelAccel = 0;

	public float smoothAngleAdj = 0.1F;

	public int prevDir = 0;

	public boolean extraGrassLast = ConfigFoliage.extraGrass;
	
	public ClientTickHandler() {
		//this constructor gets called multiple times when created from proxy, this prevents multiple inits
		if (sceneEnhancer == null) {
			sceneEnhancer = new SceneEnhancer();
			(new Thread(sceneEnhancer, "Weather2 Scene Enhancer")).start();
		}
		//TODO: 1.14 uncomment
		/*if (foliageEnhancer == null) {
			foliageEnhancer = new FoliageEnhancerShader();
			(new Thread(foliageEnhancer, "Weather2 Foliage Enhancer")).start();
		}*/

		clientConfigData = new ClientConfigData();
	}

	//TODO: 1.14 rewrite gui
    public void onRenderScreenTick()
    {
    	/*
    	Minecraft mc = Minecraft.getInstance();
    	if (mc.currentScreen instanceof IngameMenuScreen) {
    		ScaledResolution scaledresolution = new ScaledResolution(mc);
            int i = scaledresolution.getScaledWidth();
            int j = scaledresolution.getScaledHeight();
    		int k = Mouse.getX() * i / mc.displayWidth;
            int l = j - Mouse.getY() * j / mc.displayHeight - 1;
    		configButton = new Button(0, (i/2)-100, 0, 200, 20, "Weather2 EZ Config");
    		configButton.drawButton(mc, k, l, 1F);
    		
    		if (k >= configButton.x && l >= configButton.y && k < configButton.x + 200 && l < configButton.y + 20) {
    			if (Mouse.isButtonDown(0)) {
    				mc.displayGuiScreen(new GuiEZConfig());
    			}
    		}
    	}
    	*/
    }

    public void onTickInGUI(Screen guiscreen)
    {
        //onTickInGame();
    }
    
    public void onTickInGame()
    {
// TODO crawl
//    	if (ConfigLT.GENERAL.UseCrouch.get()) {
//			tickCrawl();
//		}

		if (ConfigMisc.Client_PotatoPC_Mode) return;

        Minecraft mc = Minecraft.getInstance();
        World world = mc.world;
        
        // TODO GameRenderer override
//        if (ConfigMisc.Misc_proxyRenderOverrideEnabled) {
//        	if (!(mc.gameRenderer instanceof EntityRendererProxyWeather2Mini)) {
//				oldRenderer = mc.gameRenderer;
//        		EntityRendererProxyWeather2Mini temp = new EntityRendererProxyWeather2Mini(mc, mc.getResourceManager());
//		        mc.gameRenderer = temp;
//        	}
//    	} else {
//    		if ((mc.gameRenderer instanceof EntityRendererProxyWeather2Mini)) {
//    			if (oldRenderer != null) {
//    				mc.gameRenderer = oldRenderer;
//				} else {
//					mc.gameRenderer = new GameRenderer(mc, mc.getResourceManager(), mc.getRenderTypeBuffers());
//				}
//
//    		}
//    	}

		if (world != null) {
			checkClientWeather();

			weatherManager.tick();
			minigameWeatherInstance.tick(null);

			if (!clientConfigData.Aesthetic_Only_Mode && ConfigMisc.Misc_ForceVanillaCloudsOff && world.getDimension().getType().getId() == 0) {
				mc.gameSettings.cloudOption = CloudOption.OFF;
			}

			//TODO: split logic up a bit better for this, if this is set to false mid sandstorm, fog is stuck on,
			// with sandstorms and other things it might not represent the EZ config option
			// Make sure we're in STT, TODO make this more efficient
			if (minigameWeatherInstance.isMinigameActive()) {
				//weatherManager.tick();

				sceneEnhancer.tickClient();
			}

			//TODO: replace with proper client side command?
			//TODO: 1.14 uncomment
			/*if (mc.ingameGUI.getChatGUI().getSentMessages().size() > 0) {
				String msg = (String) mc.ingameGUI.getChatGUI().getSentMessages().get(mc.ingameGUI.getChatGUI().getSentMessages().size()-1);

				if (msg.equals("/weather2 config")) {
					mc.ingameGUI.getChatGUI().getSentMessages().remove(mc.ingameGUI.getChatGUI().getSentMessages().size()-1);
					mc.displayGuiScreen(new GuiEZConfig());
				}
			}*/

			//TODO: evaluate if best here
			float windDir = WindReader.getWindAngle(world, null);
			float windSpeed = WindReader.getWindSpeed(world, null);

			//windDir = 0;

			float give = 30;

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

						/*if (bestMove > 0) {
							if (prevDir < 0) {
								smoothAngleRotationalVelAccel = 0;
							}
							prevDir = 1;
						} else if (bestMove < 0) {
							if (prevDir > 0) {
								smoothAngleRotationalVelAccel = 0;
							}
							prevDir = -1;
						}*/

					}

					if (smoothAngleRotationalVelAccel > 0.3 || smoothAngleRotationalVelAccel < -0.3) {
						smoothAngle += smoothAngleRotationalVelAccel * 0.3F;
					} else {
						//smoothAngleRotationalVelAccel *= 0.9F;
					}

					smoothAngleRotationalVelAccel *= 0.80F;
				}
			}

			//TODO: 1.14 uncomment
			/*if (!Minecraft.getInstance().isGamePaused()) {

				ExtendedRenderer.foliageRenderer.windDir = smoothAngle;

				float rate = 0.005F;

				if (ExtendedRenderer.foliageRenderer.windSpeedSmooth != windSpeed) {
					if (ExtendedRenderer.foliageRenderer.windSpeedSmooth < windSpeed) {
						if (ExtendedRenderer.foliageRenderer.windSpeedSmooth + rate > windSpeed) {
							ExtendedRenderer.foliageRenderer.windSpeedSmooth = windSpeed;
						} else {
							ExtendedRenderer.foliageRenderer.windSpeedSmooth += rate;
						}
					} else {
						if (ExtendedRenderer.foliageRenderer.windSpeedSmooth - rate < windSpeed) {
							ExtendedRenderer.foliageRenderer.windSpeedSmooth = windSpeed;
						} else {
							ExtendedRenderer.foliageRenderer.windSpeedSmooth -= rate;
						}
					}
				}

				float baseTimeChangeRate = 60F;


				ExtendedRenderer.foliageRenderer.windTime += 0 + (baseTimeChangeRate * ExtendedRenderer.foliageRenderer.windSpeedSmooth);
			}*/



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

		if (minigameWeatherInstance != null) {
			minigameWeatherInstance.reset();
		}
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
    	// TODO minigames
		minigameWeatherInstance = new MinigameWeatherInstanceClient();

		//request a full sync from server
		CompoundNBT data = new CompoundNBT();
		data.putString("command", "syncFull");
		data.putString("packetCommand", "WeatherData");
		//Weather.eventChannel.sendToServer(PacketHelper.getNBTPacket(data, Weather.eventChannelName));
		WeatherNetworking.HANDLER.sendToServer(new PacketNBTFromClient(data));
    }

    static void getField(Field field, Object newValue) throws Exception
    {
        field.setAccessible(true);
        // remove final modifier from field
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

    /*public static void tickPlayerFromEvent(PlayerEntity player) {

	}*/

    public static boolean isMainClientPlayer(PlayerEntity player) {
    	if (Minecraft.getInstance().player == null) return false;
    	return player.getUniqueID() == Minecraft.getInstance().player.getUniqueID();
	}

	public static void tickCrawl() {
		try {
			PlayerEntity player = Minecraft.getInstance().player;

			if (player != null) {
				KeyBinding keySneak = Minecraft.getInstance().gameSettings.keyBindSneak;
				KeyBinding keySprint = Minecraft.getInstance().gameSettings.keyBindSprint;

				//tap sprint while sneaking to lock crawling on, requires actively holding down sneak
				//letting go of sneak stops crawl

				if (keySneak.isKeyDown()) {
					if (keySprint.isKeyDown()) {
						if (!isCurrentlyCrawling()) {
							//System.out.println("set crawling true");
							setCrawling(true);
							sendCrawlPacketToServer(true);
						}
					}
				} else {
					//send packet only on client state change
					if (isCurrentlyCrawling()) {
						sendCrawlPacketToServer(false);
					}
					setCrawling(false);
				}
			}
		} catch (Exception ex) {
			//shhh
		}
	}

	public static boolean isCurrentlyCrawling() {
		return EventHandlerForge.playerCrawlingClient;
	}

	public static void setCrawling(boolean crawling) {
		EventHandlerForge.playerCrawlingClient = crawling;
	}

	public static void sendCrawlPacketToServer(boolean isCrawling) {
		//System.out.println("sendCrawlPacketToServer: " + isCrawling);
		CompoundNBT data = new CompoundNBT();
		data.putString("packetCommand", WeatherNetworking.NBT_PACKET_COMMAND_CRAWL);
		data.putBoolean(WeatherNetworking.NBT_PACKET_DATA_CRAWL, isCrawling);
		WeatherNetworking.HANDLER.sendToServer(new PacketNBTFromClient(data));
	}
}
