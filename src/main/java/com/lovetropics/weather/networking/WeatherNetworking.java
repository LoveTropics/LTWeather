package com.lovetropics.weather.networking;

import com.lovetropics.weather.ClientWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;

public class WeatherNetworking {

    public WeatherNetworking() {
        super();
    }

    public static void register(Object... args) {
        registerClientboundPacket(
                PacketWeatherStateFromServer.TYPE,
                PacketWeatherStateFromServer.STREAM_CODEC,
                PacketWeatherStateFromServer::handle,
                args
        );
    }

    public static <T extends PacketBase, B extends FriendlyByteBuf> void registerServerboundPacket(
            CustomPacketPayload.Type<T> type,
            StreamCodec<B, T> codec,
            BiConsumer<T, Player> handler,
            Object... args) {

        PayloadRegistrar registrar = (PayloadRegistrar) args[0];

        IPayloadHandler<T> serverHandler = (packet, ctx) -> {
            ctx.enqueueWork(() -> {
                handler.accept(packet, ctx.player());
            });
        };

        registrar.playToServer(type, (StreamCodec<RegistryFriendlyByteBuf, T>)codec, serverHandler);
    }

    public static <T extends PacketBase, B extends FriendlyByteBuf> void registerClientboundPacket(
            CustomPacketPayload.Type<T> type,
            StreamCodec<B, T> codec,
            BiConsumer<T, Player> handler,
            Object... args)
    {
        PayloadRegistrar registrar = (PayloadRegistrar) args[0];

        IPayloadHandler<T> clientHandler = (packet, ctx) -> {
            ctx.enqueueWork(() -> {
                handler.accept(packet, ClientWeather.getPlayer());
            });
        };

        registrar.playToClient(type, (StreamCodec<RegistryFriendlyByteBuf, T>)codec, clientHandler);
    }
}

