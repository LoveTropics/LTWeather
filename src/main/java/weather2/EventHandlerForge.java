package weather2;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer.FogType;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityViewRenderEvent.FogColors;
import net.minecraftforge.client.event.EntityViewRenderEvent.RenderFogEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import weather2.api.WeatherUtilData;
import weather2.client.SceneEnhancer;
import weather2.config.ConfigMisc;
import weather2.weathersystem.wind.WindManager;

@Mod.EventBusSubscriber(modid = Weather.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EventHandlerForge {

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
    public void worldRender(RenderWorldLastEvent event)
    {
		if (ConfigMisc.Client_PotatoPC_Mode) return;

		ClientTickHandler.checkClientWeather();
    }

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
    public void onFogColors(FogColors event) {

		if (ConfigMisc.Client_PotatoPC_Mode) return;

		boolean ltOverride = true;

		World world = Minecraft.getInstance().world;
		
		// TODO minigames
        if (SceneEnhancer.isFogOverridding() && (!ltOverride || (world == null || world.getDimension().getType().getRegistryName().getNamespace().equals("ltminigames")))) {
			//backup original fog colors that are actively being adjusted based on time of day
			SceneEnhancer.stormFogRedOrig = event.getRed();
			SceneEnhancer.stormFogGreenOrig = event.getGreen();
			SceneEnhancer.stormFogBlueOrig = event.getBlue();
        	event.setRed(SceneEnhancer.stormFogRed);
        	event.setGreen(SceneEnhancer.stormFogGreen);
        	event.setBlue(SceneEnhancer.stormFogBlue);
        	RenderSystem.fogMode(GlStateManager.FogMode.LINEAR);
        }
		
	}
	
	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void onFogRender(RenderFogEvent event) {

		if (ConfigMisc.Client_PotatoPC_Mode) return;

		boolean ltOverride = true;

		World world = Minecraft.getInstance().world;

		// TODO minigames
		if (SceneEnhancer.isFogOverridding() && (!ltOverride || (world == null || world.getDimension().getType().getRegistryName().getNamespace().equals("ltminigames")))) {
        	//event.setCanceled(true);
        	//event.setDensity(SceneEnhancer.stormFogDensity);

			//TODO: make use of this, density only works with EXP or EXP 2 mode
			RenderSystem.fogMode(GlStateManager.FogMode.LINEAR);
        	/*GlStateManager.setFog(GlStateManager.FogMode.EXP2);
			GlStateManager.setFogDensity(SceneEnhancer.stormFogDensity);*/
			
			if (event.getType() == FogType.FOG_SKY) {
				RenderSystem.fogStart(SceneEnhancer.stormFogStartClouds);
				RenderSystem.fogEnd(SceneEnhancer.stormFogEndClouds);
			} else {
				RenderSystem.fogStart(SceneEnhancer.stormFogStart);
				RenderSystem.fogEnd(SceneEnhancer.stormFogEnd);
			}
        }
	}
	
	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		SceneEnhancer.renderTick(event);
	}

	@SubscribeEvent
	public void onEntityLivingUpdate(LivingEvent.LivingUpdateEvent event) {
		Entity ent = event.getEntity();
		if (!ent.world.isRemote) {
			if (WeatherUtilData.isWindAffected(ent)) {
				WindManager windMan = ServerTickHandler.getWeatherSystemForDim(ent.world.getDimension().getType().getId()).windMan;
				windMan.applyWindForceNew(ent, 1F / 20F, 0.5F);
			}
		}

	}
}
