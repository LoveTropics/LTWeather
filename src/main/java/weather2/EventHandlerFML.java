package weather2;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EventHandlerFML {
	@SubscribeEvent
	public void tickServer(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			ServerTickHandler.onTickInGame();
		}
	}

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent
	public void tickClient(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			ClientProxy.clientTickHandler.onTickInGame();
		}
	}

	@SubscribeEvent
	public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getPlayer() instanceof ServerPlayerEntity) {
			ServerTickHandler.syncServerConfigToClientPlayer((ServerPlayerEntity) event.getPlayer());
		}
	}
}
