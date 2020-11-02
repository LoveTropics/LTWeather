package weather2;

import com.lovetropics.minigames.common.minigames.weather.WeatherControllerManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DeferredWorkQueue;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import weather2.util.WeatherUtilSound;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Weather.MODID)
public class Weather
{
    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger();

    public static final String MODID = "weather2";

    public static boolean initProperNeededForWorld = true;

    public Weather() {
        // Register the setup method for modloading
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::setup);
        modBus.addListener(this::serverStop);
        modBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        MinecraftForge.EVENT_BUS.register(new EventHandlerForge());
    }

    private void setup(final FMLCommonSetupEvent event) {
        DeferredWorkQueue.runLater(WeatherNetworking::register);

        WeatherControllerManager.setFactory(ServerWeatherController::new);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        WeatherUtilSound.init();

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
}
}
