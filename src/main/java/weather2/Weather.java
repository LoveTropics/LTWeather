package weather2;

import com.lovetropics.minigames.common.minigames.weather.WeatherControllerManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DeferredWorkQueue;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import weather2.config.ConfigMisc;
import weather2.util.WeatherUtilConfig;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Weather.MODID)
public class Weather
{
    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger();

    public static final String MODID = "weather2";

    public static ConfigMisc configMisc = null;

    public static CommonProxy proxy;

    public static boolean initProperNeededForWorld = true;

    public Weather() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverStop);

        MinecraftForge.EVENT_BUS.register(this);

        MinecraftForge.EVENT_BUS.register(new EventHandlerFML());
        MinecraftForge.EVENT_BUS.register(new EventHandlerForge());

        configMisc = new ConfigMisc();
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        // some preinit code

        proxy = DistExecutor.runForDist(() -> () -> new ClientProxy(), () -> () -> new CommonProxy());
        proxy.init();

        //CapabilityManager.INSTANCE.register(IChunkData.class, new ChunkDataStorage(), DefaultChunkCapData::new);
        //WorldPersistenceHooks.addHook(new EDGWorldPeristenceHook());

        DeferredWorkQueue.runLater(WeatherNetworking::register);

        //moved from common proxy
        //SoundRegistry.init();
        WeatherUtilConfig.processLists();
        //TODO: 1.14 need for LT? addMapping(EntityLightningBolt.class, "weather2_lightning_bolt", 2, 512, 5, true);

        WeatherControllerManager.setFactory(ServerWeatherController::new);
    }

    @SubscribeEvent
    public void serverStop(FMLServerStoppedEvent event) {
        resetStates();
        initProperNeededForWorld = true;
    }

    public static void resetStates() {
        ServerTickHandler.reset();
    }

    public static void dbg(Object obj) {
    if (ConfigMisc.consoleDebug) {
        LOGGER.info(obj);
    }
}
}
