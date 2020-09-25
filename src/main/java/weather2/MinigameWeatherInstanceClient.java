package weather2;

import com.lovetropics.minigames.common.minigames.IMinigameInstance;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public class MinigameWeatherInstanceClient extends MinigameWeatherInstance {


    //public float rainfallAmount = 0;

    //0 - 1
    public float curOvercastStr = 0F;
    public float curOvercastStrTarget = 0F;

    public MinigameWeatherInstanceClient() {

    }

    @Override
    public void tick(IMinigameInstance minigame) {
        super.tick(minigame);

        World world = Minecraft.getInstance().world;
        if (world == null) return;

        if (heavyRainfallActive() || acidRainActive()) {
            curOvercastStrTarget = 1F;
        } else {
            curOvercastStrTarget = 0F;
        }

        float rateChange = 0.005F;

        if (curOvercastStr > curOvercastStrTarget) {
            if (heatwaveActive()) {
                curOvercastStr -= rateChange * 5F;
            } else {
                curOvercastStr -= rateChange * 2F;
            }
        } else if (curOvercastStr < curOvercastStrTarget) {
            curOvercastStr += rateChange;
        }

        if (world.getGameTime() % 60 == 0) {
            //LOGGER.info(curOvercastStr);
        }

        world.getPlayers().forEach(player -> tickPlayer(player));
    }

    @Override
    public void tickPlayer(PlayerEntity player) {
//        if (heatwaveActive() && !player.isCreative() && !player.isSpectator()) {
//            if (player.world.getHeight(Heightmap.Type.MOTION_BLOCKING, player.getPosition()).getY() <= player.getPosition().getY()) {
//                System.out.println("slowing player");
//                player.setMotionMultiplier(player.getBlockState(), new Vec3d(0.95D, 1D, 0.95D));
//                Vec3d v = player.getMotion();
//                player.setMotion(v.x * heatwaveMovementMultiplierClient, v.y, v.z * heatwaveMovementMultiplierClient);
//            }
//        }
    }

    public float getParticleRainfallAmount() {
        return curOvercastStr;
    }

    public float getVanillaRainRenderAmount() {
        //have vanilla rain get to max saturation by the time curOvercastStr hits 0.33, rest of range is used for extra particle effects elsewhere
        return Math.min(curOvercastStr * 3F, 1F);
    }
}
