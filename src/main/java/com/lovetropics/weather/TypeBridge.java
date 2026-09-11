package com.lovetropics.weather;

import com.lovetropics.minigames.common.core.game.weather.WeatherController;
import com.lovetropics.minigames.common.core.game.weather.WeatherControllerManager;
import com.mojang.datafixers.util.Pair;

import net.minecraft.server.level.ServerLevel;

public class TypeBridge {

    public static int getPrecipitationTypeOrdinal(ClientWeather weather) {
        return weather.getPrecipitationType().ordinal();
    }

    public static float getWindSpeed(ServerLevel level) {
        WeatherController weatherController = WeatherControllerManager.forWorld(level);
        return weatherController.getWindSpeed();
    }

    public static Pair<Integer, Integer> getSandstormData(ServerLevel level) {
        WeatherController controller = WeatherControllerManager.forWorld(level);
        return controller != null && controller.getSandstorm() != null ? new Pair<>(controller.getSandstorm().buildupTickRate(), controller.getSandstorm().maxStackable()) : null;
    }

    public static Pair<Integer, Integer> getSnowstormData(ServerLevel level) {
        WeatherController controller = WeatherControllerManager.forWorld(level);
        return controller != null && controller.getSnowstorm() != null ? new Pair<>(controller.getSnowstorm().buildupTickRate(), controller.getSnowstorm().maxStackable()) : null;
    }

}
