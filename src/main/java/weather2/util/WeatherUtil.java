package weather2.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import javax.annotation.Nullable;

public class WeatherUtil {

    public static boolean isClientPaused() {
		return Minecraft.getInstance().isGamePaused();
	}

    @Nullable
    public static ServerWorld getWorldOrNull(DimensionType dimensionType) {
        return DimensionManager.getWorld(ServerLifecycleHooks.getCurrentServer(), dimensionType, false, false);
    }
}
