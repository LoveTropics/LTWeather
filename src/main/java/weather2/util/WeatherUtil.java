package weather2.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

public class WeatherUtil {

    public static boolean isPaused() {
		return Minecraft.getInstance().isGamePaused();
	}
    public static ServerWorld getWorld(int dimID) {
        return getWorld(DimensionType.getById(dimID));
    }

    public static ServerWorld getWorld(DimensionType dimensionType) {
        return DimensionManager.getWorld(ServerLifecycleHooks.getCurrentServer(), dimensionType, true, true);
    }

    public static Iterable<ServerWorld> getWorlds() {
        return ServerLifecycleHooks.getCurrentServer().getWorlds();
    }
}
