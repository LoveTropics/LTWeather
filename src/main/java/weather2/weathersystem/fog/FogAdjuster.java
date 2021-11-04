package weather2.weathersystem.fog;

import CoroUtil.util.CoroUtilMisc;
import com.lovetropics.minigames.common.core.game.weather.WeatherEventType;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import weather2.ClientTickHandler;
import weather2.ClientWeather;
import weather2.client.SceneEnhancer;
import weather2.util.WeatherUtilEntity;

public class FogAdjuster {

    private FogProfile fogHeatwave;
    private FogProfile fogSandstorm;
    private FogProfile fogSnowstorm;

    //initial values arent really used for this one, just used to store dynamically updated values for smooth transitions
    private FogProfile fogVanilla;

    private FogProfile activeProfile;
    private FogProfile prevProfile;

    private float lerpAmount = 0;

    //reinit fog values when changes
    private boolean useFarFog = false;

    /**
     *
     * should also tick the intensity value, smoothly, make sure to use partialTicks for everything
     *
     * uses profiles to know what to set to
     * current way uses math lerp to use our intensity value to lerp between vanilla setting and our setting
     * - how will this work if we ever need to go from 1 profile to another DIRECTLY?
     * -- upon detecting a state change need, take a snapshot and set that snapshot as the lerp from values?
     * - otherwise it should be easy, like it is already, lerp from vanilla far plane / colors to our stuff, then back to vanilla, then to whatever next profile is going to run
     */


    public FogAdjuster() {
        initProfiles(false);
        prevProfile = fogVanilla;
        activeProfile = fogVanilla;
    }

    public void initProfiles(boolean spectator) {

        float distAmp = 1F;
        if (spectator) {
            distAmp = 4F;
        }
        fogHeatwave = new FogProfile(new Vector3f(0.5F, 0.2F, 0.1F), 0, 75, GlStateManager.FogMode.LINEAR);
        fogSandstorm = new FogProfile(new Vector3f(0.7F, 0.5F, 0.2F), 0, 18 * distAmp, GlStateManager.FogMode.LINEAR);
        fogSnowstorm = new FogProfile(new Vector3f(0.7F, 0.7F, 0.7F), 0, 20 * distAmp, GlStateManager.FogMode.LINEAR);
        fogVanilla = new FogProfile(new Vector3f(-1F, -1F, -1F), -1, -1, GlStateManager.FogMode.LINEAR);
    }

    public void tickGame(ClientWeather weather) {
        updateWeatherState();

        //System.out.println("lerpAmount: " + lerpAmount);
        //System.out.println("isFogOverriding(): " + isFogOverriding());

        //only adjust when the active transition is complete
        if ((lerpAmount == 0 || lerpAmount == 1) && (SceneEnhancer.getWeatherState() == WeatherEventType.SANDSTORM || SceneEnhancer.getWeatherState() == WeatherEventType.SNOWSTORM)) {
            PlayerEntity player = Minecraft.getInstance().player;
            //use non cached version of isPlayerOutside to fix data mismatch that is timing crucial here
            boolean isPlayerOutside = WeatherUtilEntity.isEntityOutside(player);
            boolean playerOutside = isPlayerOutside || player.isInWater();
            boolean setFogFar = !playerOutside || player.isSpectator();
            /*System.out.println("set to far mode?: " + setFogFar);
            System.out.println("playerOutside: " + SceneEnhancer.isPlayerOutside);
            System.out.println("isInWater: " + player.isInWater());
            System.out.println("setFogFar: " + setFogFar);*/
            if (player != null) {
                if ((setFogFar && !useFarFog) || !setFogFar && useFarFog) {
                    initProfiles(setFogFar);
                }
                useFarFog = setFogFar;
            }
        }

        lerpAmount = CoroUtilMisc.adjVal(lerpAmount, 1F, 0.01F);

        //Weather.dbg("activeIntensity: " + activeIntensity);
        //Weather.dbg("lerpAmount: " + lerpAmount);
    }

    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        updateWeatherState();

        if (SceneEnhancer.isFogOverridding()) {
            //float intensity = SceneEnhancer.heatwaveIntensity;

            //keep semi dynamic vanilla settings up to date
            fogVanilla.getRgb().set(event.getRed(), event.getGreen(), event.getBlue());

            float red = MathHelper.lerp(lerpAmount, prevProfile.getRgb().getX(), activeProfile.getRgb().getX());
            float green = MathHelper.lerp(lerpAmount, prevProfile.getRgb().getY(), activeProfile.getRgb().getY());
            float blue = MathHelper.lerp(lerpAmount, prevProfile.getRgb().getZ(), activeProfile.getRgb().getZ());

            event.setRed(red);
            event.setGreen(green);
            event.setBlue(blue);
            RenderSystem.fogMode(GlStateManager.FogMode.LINEAR);
        }
    }

    public void onFogRender(EntityViewRenderEvent.RenderFogEvent event) {
        updateWeatherState();

        if (SceneEnhancer.isFogOverridding()) {
            //TODO: make use of this, density only works with EXP or EXP 2 mode
            RenderSystem.fogMode(GlStateManager.FogMode.LINEAR);

            if (event.getType() == FogRenderer.FogType.FOG_SKY) {
                //TODO: note, this value can be different depending on other contexts, we should try to grab GlStateManager.FOG.start, if we dont, itll glitch with bosses that cause fog and blindness effect, maybe more
                //value from FogRenderer.setupFog method
                fogVanilla.setFogStartSky(0);
                fogVanilla.setFogEndSky(event.getFarPlaneDistance());
                RenderSystem.fogStart(MathHelper.lerp(lerpAmount, prevProfile.getFogStartSky(), activeProfile.getFogStartSky()));
                RenderSystem.fogEnd(MathHelper.lerp(lerpAmount, prevProfile.getFogEndSky(), activeProfile.getFogEndSky()));
            } else {
                //value from FogRenderer.setupFog method
                fogVanilla.setFogStart(event.getFarPlaneDistance() * 0.75F);
                fogVanilla.setFogEnd(event.getFarPlaneDistance());
                //Weather.dbg("getFarPlaneDistance: " + event.getFarPlaneDistance());
                RenderSystem.fogStart(MathHelper.lerp(lerpAmount, prevProfile.getFogStart(), activeProfile.getFogStart()));
                RenderSystem.fogEnd(MathHelper.lerp(lerpAmount, prevProfile.getFogEnd(), activeProfile.getFogEnd()));
            }
        }
    }

    public void startHeatwave() {
        prevProfile = activeProfile;
        activeProfile = fogHeatwave;
        lerpAmount = 0;
    }

    public void startSandstorm() {
        prevProfile = activeProfile;
        activeProfile = fogSandstorm;
        lerpAmount = 0;
    }

    public void startSnowstorm() {
        prevProfile = activeProfile;
        activeProfile = fogSnowstorm;
        lerpAmount = 0;
    }

    public void restoreVanilla() {
        prevProfile = activeProfile;
        activeProfile = fogVanilla;
        lerpAmount = 0;
    }

    public float getLerpAmount() {
        return lerpAmount;
    }

    public boolean isFogOverriding() {
        ClientTickHandler.checkClientWeather();
        ClientWeather weather = ClientWeather.get();
        return (weather.isHeatwave() || weather.isSandstorm() || weather.isSnowstorm()) || lerpAmount != 1;
    }

    /**
     * In its own method so quick render update calls can force an update check to prevent old data use which causes flickers
     */
    public void updateWeatherState() {
        ClientTickHandler.checkClientWeather();
        ClientWeather weather = ClientWeather.get();
        if (weather.isSandstorm()) {
            if (activeProfile != fogSandstorm) {
                startSandstorm();
            }
        } else if (weather.isSnowstorm()) {
            if (activeProfile != fogSnowstorm) {
                startSnowstorm();
            }
        } else if (weather.isHeatwave()) {
            if (activeProfile != fogHeatwave) {
                startHeatwave();
            }
        } else if (activeProfile != null) {
            if (activeProfile != fogVanilla) {
                restoreVanilla();
            }
        }
    }
}
