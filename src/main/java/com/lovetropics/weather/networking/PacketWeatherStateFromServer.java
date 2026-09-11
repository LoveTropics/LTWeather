package com.lovetropics.weather.networking;

import com.lovetropics.minigames.common.core.game.weather.WeatherState;
import com.lovetropics.weather.ClientWeather;
import com.lovetropics.weather.LTWeather;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public record PacketWeatherStateFromServer(WeatherState weatherState) implements PacketBase
{
    public static final Type<PacketWeatherStateFromServer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LTWeather.MODID, "weather_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWeatherStateFromServer> STREAM_CODEC = StreamCodec.ofMember(PacketWeatherStateFromServer::serialize, PacketWeatherStateFromServer::deserialize);

    private void serialize(RegistryFriendlyByteBuf buffer) {
        weatherState.serialize(buffer);
    }

    private static PacketWeatherStateFromServer deserialize(RegistryFriendlyByteBuf buffer) {
        WeatherState weather = new WeatherState();
        weather.deserialize(buffer);
        return new PacketWeatherStateFromServer(weather);
    }

    public void handle(Player player)
    {
        try {
            ClientWeather.get().onUpdateWeather(weatherState);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
