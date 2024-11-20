package com.lovetropics.weather;

import com.lovetropics.minigames.common.core.game.weather.WeatherControllerManager;
import com.lovetropics.weather.networking.WeatherNetworking;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(LTWeather.MODID)
public class LTWeather
{
    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger();

    public static final String MODID = "ltweather";

    public static boolean initProperNeededForWorld = true;

    public LTWeather(ModContainer modContainer) {

        new WeatherNetworking();

        // Register the setup method for modloading
        modContainer.getEventBus().addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(this::serverStop);
        modContainer.getEventBus().addListener(this::clientSetup);
        modContainer.getEventBus().addListener(this::registerPackets);

        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(ClientWeather.class);
        }

        NeoForge.EVENT_BUS.register(this);
    }

    public void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1.0.0");
        WeatherNetworking.register(registrar);
    }

    private void setup(final FMLCommonSetupEvent event) {
        //DeferredWorkQueue.runLater(WeatherNetworking::register);
        //LTWeatherNetworking.register();

        WeatherControllerManager.setFactory(ServerWeatherController::new);
    }

    private void clientSetup(FMLClientSetupEvent event) {

    }

    @SubscribeEvent
    public void serverStop(ServerStoppedEvent event) {
        initProperNeededForWorld = true;
    }

    public static void dbg(Object obj) {
        System.out.println(obj);
    }
}
