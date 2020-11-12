package weather2.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import com.lovetropics.minigames.common.minigames.weather.RainType;

import CoroUtil.util.ChunkCoordinatesBlock;
import CoroUtil.util.CoroUtilBlock;
import CoroUtil.util.CoroUtilEntOrParticle;
import CoroUtil.util.CoroUtilMisc;
import extendedrenderer.particle.ParticleRegistry;
import extendedrenderer.particle.entity.EntityRotFX;
import extendedrenderer.particle.entity.ParticleTexExtraRender;
import extendedrenderer.particle.entity.ParticleTexFX;
import extendedrenderer.particle.entity.ParticleTexLeafColor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.UnderwaterParticle;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.settings.ParticleStatus;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import weather2.ClientTickHandler;
import weather2.ClientWeather;
import weather2.SoundRegistry;
import weather2.Weather;
import weather2.util.WeatherUtilBlock;
import weather2.util.WeatherUtilDim;
import weather2.util.WeatherUtilEntity;
import weather2.util.WeatherUtilParticle;
import weather2.util.WeatherUtilSound;
import weather2.util.WindReader;
import weather2.weathersystem.WeatherManagerClient;
import weather2.weathersystem.wind.WindManager;

@OnlyIn(Dist.CLIENT)
public class SceneEnhancer implements Runnable {

	private static final double PRECIPITATION_PARTICLE_EFFECT_RATE = 0.7;

	//this is for the thread we make
	public World lastWorldDetected = null;

	public static List<Particle> spawnQueueNormal = new ArrayList<>();
    public static List<Particle> spawnQueue = new ArrayList<>();
    
    public static long threadLastWorldTickTime;
    public static int lastTickFoundBlocks;
    public static long lastTickAmbient;
    public static long lastTickAmbientThreaded;
    
    public static ArrayList<ChunkCoordinatesBlock> soundLocations = new ArrayList<>();
    public static HashMap<ChunkCoordinatesBlock, Long> soundTimeLocations = new HashMap<>();
    
    public static List<Block> LEAVES_BLOCKS = new ArrayList<>();

	private static final List<BlockPos> listPosRandom = new ArrayList<>();

	public static float heatwaveIntensity;
	public static float heatwaveIntensityTarget;

	public static final ResourceLocation RAIN_TEXTURES_GREEN = new ResourceLocation(Weather.MODID, "textures/environment/rain_green.png");
	public static final ResourceLocation RAIN_TEXTURES = new ResourceLocation("textures/environment/rain.png");

	public SceneEnhancer() {
		listPosRandom.clear();
		listPosRandom.add(new BlockPos(0, -1, 0));
		listPosRandom.add(new BlockPos(1, 0, 0));
		listPosRandom.add(new BlockPos(-1, 0, 0));
		listPosRandom.add(new BlockPos(0, 0, 1));
		listPosRandom.add(new BlockPos(0, 0, -1));

		Collections.addAll(LEAVES_BLOCKS, Blocks.ACACIA_LEAVES, Blocks.BIRCH_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES);
	}
	
	@Override
	public void run() {
		while (true) {
			try {
				tickClientThreaded();
				Thread.sleep(400);
			} catch (Throwable throwable) {
                throwable.printStackTrace();
            }
		}
	}

	//run from client side _client_ thread
	public void tickClient() {
		if (!Minecraft.getInstance().isGamePaused()) {
			Minecraft client = Minecraft.getInstance();

			if (client.world != null && lastWorldDetected != client.world) {
				lastWorldDetected = client.world;
				reset();
			}

			ClientTickHandler.checkClientWeather();
			ClientWeather weather = ClientWeather.get();

			if (weather.hasWeather()) {
				tryParticleSpawning();
				tickParticlePrecipitation();
				trySoundPlaying();
				tryWind(client.world);
			}

			tickMisc();

			tickHeatwave(weather);
		}
	}
	
	//run from our newly created thread
	public void tickClientThreaded() {
		Minecraft client = Minecraft.getInstance();

		if (client != null && client.world != null && client.player != null && ClientWeather.get().hasWeather()) {
			profileSurroundings();
			tryAmbientSounds();
		}
	}
	
	public synchronized void trySoundPlaying()
    {
		try {
			if (lastTickAmbient < System.currentTimeMillis()) {
	    		lastTickAmbient = System.currentTimeMillis() + 500;
	    		
	    		Minecraft client = Minecraft.getInstance();
	        	
	        	World worldRef = client.world;
	        	PlayerEntity player = client.player;
	        	
	        	int size = 32;
	            int hsize = size / 2;
	            BlockPos cur = player.getPosition();
	            
	            Random rand = new Random();
	            
	            //trim out distant sound locations, also tick last time played
	            for (int i = 0; i < soundLocations.size(); i++) {
	            	
	            	ChunkCoordinatesBlock cCor = soundLocations.get(i);
	            	
	            	if (Math.sqrt(cCor.distanceSq(cur)) > size) {
	            		soundLocations.remove(i--);
	            		soundTimeLocations.remove(cCor);
	            		//System.out.println("trim out soundlocation");
	            	} else {
	
	                    Block block = getBlock(worldRef, cCor.getX(), cCor.getY(), cCor.getZ());//Block.blocksList[id];
	                    
	                    if (block == null || (block.getMaterial(block.getDefaultState()) != Material.WATER && block.getMaterial(block.getDefaultState()) != Material.LEAVES)) {
	                    	soundLocations.remove(i);
	                		soundTimeLocations.remove(cCor);
	                    } else {
	                    	
		            		long lastPlayTime = 0;
		            		
		            		
		            		
		            		if (soundTimeLocations.containsKey(cCor)) {
		            			lastPlayTime = soundTimeLocations.get(cCor);
		            		}
		            		
		            		//System.out.println(Math.sqrt(cCor.getDistanceSquared(curX, curY, curZ)));
							if (lastPlayTime < System.currentTimeMillis()) {
								if (LEAVES_BLOCKS.contains(cCor.block)) {
									float windSpeed = WindReader.getWindSpeed(client.world);
									if (windSpeed > 0.2F) {
										soundTimeLocations.put(cCor, System.currentTimeMillis() + 12000 + rand.nextInt(50));
										//client.getSoundHandler().playSound(Weather.modID + ":wind_calmfade", cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), (float)(windSpeed * 4F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F));
										//client.world.playSound(cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), Weather.modID + ":env.wind_calmfade", (float)(windSpeed * 4F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F), false);
										client.world.playSound(cCor, SoundRegistry.get("env.wind_calmfade"), SoundCategory.AMBIENT, (float)(windSpeed * 2F), 0.70F + (rand.nextFloat() * 0.1F), false);
										//System.out.println("play leaves sound at: " + cCor.getPosX() + " - " + cCor.getPosY() + " - " + cCor.getPosZ() + " - windSpeed: " + windSpeed);
									} else {
										windSpeed = WindReader.getWindSpeed(client.world);
										//if (windSpeed > 0.3F) {
										if (client.world.rand.nextInt(15) == 0) {
											soundTimeLocations.put(cCor, System.currentTimeMillis() + 12000 + rand.nextInt(50));
											//client.getSoundHandler().playSound(Weather.modID + ":wind_calmfade", cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), (float)(windSpeed * 2F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F));
											//client.world.playSound(cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), Weather.modID + ":env.wind_calmfade", (float)(windSpeed * 2F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F), false);
											client.world.playSound(cCor, SoundRegistry.get("env.wind_calmfade"), SoundCategory.AMBIENT, windSpeed, 0.70F + (rand.nextFloat() * 0.1F), false);
										}
											//System.out.println("play leaves sound at: " + cCor.getPosX() + " - " + cCor.getPosY() + " - " + cCor.getPosZ() + " - windSpeed: " + windSpeed);
										//}
									}
										
									
								}
							}
						}
	            	}
	            }
			}
		} catch (Exception ex) {
    		System.out.println("Weather2: Error handling sound play queue: ");
    		ex.printStackTrace();
    	}
    }
	
	//Threaded function
    @OnlyIn(Dist.CLIENT)
    public static void tryAmbientSounds()
    {
    	Minecraft client = Minecraft.getInstance();
    	
    	World worldRef = client.world;
    	PlayerEntity player = client.player;
    	
    	if (lastTickAmbientThreaded < System.currentTimeMillis()) {
    		lastTickAmbientThreaded = System.currentTimeMillis() + 500;
    		
    		int size = 32;
            int hsize = size / 2;
            int curX = (int)player.getPosX();
            int curY = (int)player.getPosY();
            int curZ = (int)player.getPosZ();
            
            //soundLocations.clear();
            
            
    		
    		for (int xx = curX - hsize; xx < curX + hsize; xx++)
            {
                for (int yy = curY - (hsize / 2); yy < curY + hsize; yy++)
                {
                    for (int zz = curZ - hsize; zz < curZ + hsize; zz++)
                    {
                        Block block = getBlock(worldRef, xx, yy, zz);
                        
                        if (block != null) {
                        	if (((block.getMaterial(block.getDefaultState()) == Material.LEAVES))) {
                            	boolean proxFail = false;
								for (ChunkCoordinatesBlock soundLocation : soundLocations) {
									if (Math.sqrt(soundLocation.distanceSq(new Vec3i(xx, yy, zz))) < 15) {
										proxFail = true;
										break;
									}
								}
                				
                				if (!proxFail) {
                					soundLocations.add(new ChunkCoordinatesBlock(xx, yy, zz, block));
                				}
                            }
                        }
                    }
                }
            }
    	}
    }

	public void reset() {
		//reset particle data, discard dead ones as that was a bug from weather1
		
		/*if (ExtendedRenderer.rotEffRenderer != null) {
			ExtendedRenderer.rotEffRenderer.clear();
        }*/

		((ClientWorld)lastWorldDetected).globalEntities.clear();
		
		if (WeatherUtilParticle.fxLayers == null) {
			WeatherUtilParticle.getFXLayers();
		}
		//WeatherUtilSound.getSoundSystem();
	}

	private static void tickHeatwave(ClientWeather weather) {
		Minecraft client = Minecraft.getInstance();

		if (weather.isHeatwave()) {
			heatwaveIntensityTarget = 0.7F;
		} else {
			heatwaveIntensityTarget = 0.0F;
		}

		heatwaveIntensity = CoroUtilMisc.adjVal(heatwaveIntensity, heatwaveIntensityTarget, 0.01F);

		if (heatwaveIntensity > 0) {
			if (heatwaveIntensity < 0.33F) {
				tryPlayPlayerLockedSound(WeatherUtilSound.snd_sandstorm_low, 5, client.player, 1F);
			} else if (heatwaveIntensity < 0.66F) {
				tryPlayPlayerLockedSound(WeatherUtilSound.snd_sandstorm_med, 4, client.player, 1F);
			} else {
				tryPlayPlayerLockedSound(WeatherUtilSound.snd_sandstorm_high, 3, client.player, 1F);
			}
		}
	}

	public static boolean tryPlayPlayerLockedSound(String[] sound, int arrIndex, Entity source, float vol)
	{
		Random rand = new Random();

		if (WeatherUtilSound.soundTimer[arrIndex] <= System.currentTimeMillis())
		{
			String soundStr = sound[WeatherUtilSound.snd_rand[arrIndex]];

			WeatherUtilSound.playPlayerLockedSound(source.getPositionVector(), new StringBuilder().append("streaming." + soundStr).toString(), vol, 1.0F);

			int length = WeatherUtilSound.soundToLength.get(soundStr);
			//-500L, for blending
			WeatherUtilSound.soundTimer[arrIndex] = System.currentTimeMillis() + length - 500L;
			WeatherUtilSound.snd_rand[arrIndex] = rand.nextInt(sound.length);
		}

		return false;
	}

	public void tickMisc() {

		ClientWeather weather = ClientWeather.get();
		if (weather.getRainType() == RainType.ACID) {
			if (WorldRenderer.RAIN_TEXTURES != RAIN_TEXTURES_GREEN) {
				WorldRenderer.RAIN_TEXTURES = RAIN_TEXTURES_GREEN;
			}
		} else {
			if (WorldRenderer.RAIN_TEXTURES != RAIN_TEXTURES) {
				WorldRenderer.RAIN_TEXTURES = RAIN_TEXTURES;
			}
		}

	}

	public void tickParticlePrecipitation() {

		//if (true) return;

		PlayerEntity entP = Minecraft.getInstance().player;

		WeatherManagerClient weatherMan = ClientTickHandler.weatherManager;
		if (weatherMan == null) return;
		WindManager windMan = weatherMan.getWindManager();
		if (windMan == null) return;

		ClientWeather weather = ClientWeather.get();

		float curPrecipVal = weather.getRainAmount();
		float maxPrecip = 0.5F;

			/*if (entP.world.getGameTime() % 20 == 0) {
				Weather.dbg("curRainStr: " + curRainStr);
			}*/

		//Weather.dbg("curPrecipVal: " + curPrecipVal * 100F);

		int precipitationHeight = entP.world.getHeight(Heightmap.Type.MOTION_BLOCKING, new BlockPos(MathHelper.floor(entP.getPosX()), 0, MathHelper.floor(entP.getPosZ()))).getY();

		Biome biome = entP.world.getBiome(new BlockPos(MathHelper.floor(entP.getPosX()), 0, MathHelper.floor(entP.getPosZ())));

		World world = entP.world;
		Random rand = entP.world.rand;

		//System.out.println("ClientTickEvent time: " + world.getGameTime());

		double particleAmp = 1F;

		//funnel.tickGame();

		//check rules same way vanilla texture precip does
		if (biome != null && (biome.getPrecipitation() != Biome.RainType.NONE))
		{
			//now absolute it for ez math
			curPrecipVal = Math.min(maxPrecip, Math.abs(curPrecipVal));

			curPrecipVal *= 1F;

			float adjustedRate = 1F;
			if (Minecraft.getInstance().gameSettings.particles == ParticleStatus.DECREASED) {
				adjustedRate = 0.5F;
			} else if (Minecraft.getInstance().gameSettings.particles == ParticleStatus.MINIMAL) {
				adjustedRate = 0.2F;
			}

			if (curPrecipVal > 0) {

				//particleAmp = 1;

				int spawnCount;
				int spawnNeed = (int)(curPrecipVal * 40F * PRECIPITATION_PARTICLE_EFFECT_RATE * particleAmp);
				int safetyCutout = 100;

				int extraRenderCount = 15;

				//attempt to fix the cluttering issue more noticable when barely anything spawning
				if (curPrecipVal < 0.1 && PRECIPITATION_PARTICLE_EFFECT_RATE > 0) {
					//swap rates
					int oldVal = extraRenderCount;
					extraRenderCount = spawnNeed;
					spawnNeed = oldVal;
				}

				//replaced use of getBiomeProvider().getTemperatureAtHeight(temperature, precipitationHeight) below
				//since temperatures have X Z noise variance now, i might need to redesign the if temp check if statement to be inside loop, but is that performant?
				BlockPos posForTemperature = entP.getPosition();

				//rain
				if (entP.world.getBiome(posForTemperature).getTemperature(posForTemperature) >= 0.15F) {

					//Weather.dbg("precip: " + curPrecipVal);

					spawnCount = 0;
					int spawnAreaSize = 20;

					boolean rainParticle = false;
					boolean groundSplash = true;
					boolean downfall = false;

					if (rainParticle && spawnNeed > 0) {
						for (int i = 0; i < safetyCutout; i++) {
							BlockPos pos = new BlockPos(
									entP.getPosX() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2),
									entP.getPosY() - 5 + rand.nextInt(25),
									entP.getPosZ() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2));

							//EntityRenderer.addRainParticles doesnt actually use isRainingAt,
							//switching to match what that method does to improve consistancy and tough as nails compat
							if (canPrecipitateAt(world, pos)/*world.isRainingAt(pos)*/) {
								ParticleTexExtraRender rain = new ParticleTexExtraRender(entP.world,
										pos.getX(),
										pos.getY(),
										pos.getZ(),
										0D, 0D, 0D, ParticleRegistry.rain_white);
								//rain.setCanCollide(true);
								//rain.setKillOnCollide(true);
								rain.setKillWhenUnderTopmostBlock(true);
								rain.setCanCollide(false);
								rain.killWhenUnderCameraAtLeast = 5;
								rain.setTicksFadeOutMaxOnDeath(5);
								rain.setDontRenderUnderTopmostBlock(true);
								rain.setExtraParticlesBaseAmount(extraRenderCount);
								rain.fastLight = true;
								rain.setSlantParticleToWind(true);
								rain.windWeight = 1F;

								//old slanty rain way
								rain.setFacePlayer(true);
								rain.setSlantParticleToWind(true);

								//rain.setFacePlayer(true);
								rain.setScale(2F * 0.15F);
								rain.isTransparent = true;
								rain.setGravity(2.5F);
								//rain.isTransparent = true;
								rain.setMaxAge(50);
								//opted to leave the popin for rain, its not as bad as snow, and using fade in causes less rain visual overall
								rain.setTicksFadeInMax(5);
								rain.setAlphaF(0);
								rain.rotationYaw = rain.getWorld().rand.nextInt(360) - 180F;
								rain.setMotionY(-0.5D/*-5D - (entP.world.rand.nextInt(5) * -1D)*/);

								rain.spawnAsWeatherEffect();

								spawnCount++;
								if (spawnCount >= spawnNeed) {
									break;
								}
							}
						}
					}

					//TODO: make ground splash and downfall use spawnNeed var style design

					float acidRainRed = 0.5F;
					float acidRainGreen = 1F;
					float acidRainBlue = 0.5F;

					float vanillaRainRed = 0.7F;
					float vanillaRainGreen = 0.7F;
					float vanillaRainBlue = 1F;

					spawnAreaSize = 40;
					//ground splash
					if (groundSplash == true && curPrecipVal > 0.15) {
						for (int i = 0; i < 30F * curPrecipVal * PRECIPITATION_PARTICLE_EFFECT_RATE * particleAmp * 4F * adjustedRate; i++) {
							BlockPos pos = new BlockPos(
									entP.getPosX() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2),
									entP.getPosY() - 5 + rand.nextInt(15),
									entP.getPosZ() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2));


							//get the block on the topmost ground
							pos = world.getHeight(Heightmap.Type.MOTION_BLOCKING, pos).down();

							BlockState state = world.getBlockState(pos);
							double maxY = 0;
							double minY = 0;
							VoxelShape shape = state.getShape(world, pos);
							if (!shape.isEmpty()) {
								minY = shape.getBoundingBox().minY;
								maxY = shape.getBoundingBox().maxY;
							}

							if (pos.distanceSq(entP.getPosition()) > (spawnAreaSize / 2) * (spawnAreaSize / 2))
								continue;

							//block above topmost ground
							if (canPrecipitateAt(world, pos.up())/*world.isRainingAt(pos)*/) {

								//fix for splash spawning invisibly 1 block underwater
								if (world.getBlockState(pos).getMaterial() == Material.WATER) {
									pos = pos.add(0,1,0);
								}

								ParticleTexFX rain = new ParticleTexFX(entP.world,
										pos.getX() + rand.nextFloat(),
										pos.getY() + 0.01D + maxY,
										pos.getZ() + rand.nextFloat(),
										0D, 0D, 0D, ParticleRegistry.cloud256_6);
								//rain.setCanCollide(true);
								rain.setKillWhenUnderTopmostBlock(true);
								rain.setCanCollide(false);
								rain.killWhenUnderCameraAtLeast = 5;
								//rain.setKillOnCollide(true);
								//rain.setKillWhenUnderTopmostBlock(true);
								//rain.setTicksFadeOutMaxOnDeath(5);

								//rain.setDontRenderUnderTopmostBlock(true);
								//rain.setExtraParticlesBaseAmount(5);
								//rain.setDontRenderUnderTopmostBlock(true);

								boolean upward = rand.nextBoolean();

								rain.windWeight = 20F;
								rain.setFacePlayer(upward);
								//SHADER COMPARE TEST
								//rain.setFacePlayer(false);

								rain.setScale(0.2F + (rand.nextFloat() * 0.05F));
								rain.setMaxAge(15);
								rain.setGravity(-0.0F);
								//opted to leave the popin for rain, its not as bad as snow, and using fade in causes less rain visual overall
								rain.setTicksFadeInMax(0);
								rain.setAlphaF(0);
								rain.setTicksFadeOutMax(4);
								rain.renderOrder = 2;

								rain.rotationYaw = rain.getWorld().rand.nextInt(360) - 180F;
								rain.rotationPitch = 90;
								rain.setMotionY(0D);
								/*rain.setMotionX(0);
								rain.setMotionZ(0);*/
								rain.setMotionX((rand.nextFloat() - 0.5F) * 0.01F);
								rain.setMotionZ((rand.nextFloat() - 0.5F) * 0.01F);

								if (weather.getRainType() == RainType.ACID) {
									rain.particleRed = acidRainRed;
									rain.particleGreen = acidRainGreen;
									rain.particleBlue = acidRainBlue;
								} else {
									rain.particleRed = vanillaRainRed;
									rain.particleGreen = vanillaRainGreen;
									rain.particleBlue = vanillaRainBlue;
								}

								rain.spawnAsWeatherEffect();
							}
						}
					}

					//if (true) return;

					spawnAreaSize = 20;
					//downfall - at just above 0.3 cause rainstorms lock at 0.3 but flicker a bit above and below
					if (downfall == true && curPrecipVal > 0.32) {

						int scanAheadRange = 0;
						//quick is outside check, prevent them spawning right near ground
						//and especially right above the roof so they have enough space to fade out
						//results in not seeing them through roofs
						if (WeatherUtilDim.canBlockSeeSky(world, entP.getPosition())) {
							scanAheadRange = 3;
						} else {
							scanAheadRange = 10;
						}

						for (int i = 0; i < 2F * curPrecipVal * PRECIPITATION_PARTICLE_EFFECT_RATE * adjustedRate; i++) {
							BlockPos pos = new BlockPos(
									entP.getPosX() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2),
									entP.getPosY() + 5 + rand.nextInt(15),
									entP.getPosZ() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2));

							if (WeatherUtilEntity.getDistanceSqEntToPos(entP, pos) < 10D * 10D) continue;

							//pos = world.getPrecipitationHeight(pos).add(0, 1, 0);

							if (canPrecipitateAt(world, pos.up(-scanAheadRange))/*world.isRainingAt(pos)*/) {
								ParticleTexFX rain = new ParticleTexFX(entP.world,
										pos.getX() + rand.nextFloat(),
										pos.getY() - 1 + 0.01D,
										pos.getZ() + rand.nextFloat(),
										0D, 0D, 0D, ParticleRegistry.downfall3);
								//rain.setCanCollide(true);
								//rain.setKillOnCollide(true);
								rain.setCanCollide(false);
								rain.killWhenUnderCameraAtLeast = 5;
								rain.setKillWhenUnderTopmostBlock(true);
								rain.setKillWhenUnderTopmostBlock_ScanAheadRange(scanAheadRange);
								rain.setTicksFadeOutMaxOnDeath(10);

								//rain.particleTextureJitterX = 0;
								//rain.particleTextureJitterY = 0;

								//rain.setDontRenderUnderTopmostBlock(true);
								//rain.setExtraParticlesBaseAmount(5);
								//rain.setDontRenderUnderTopmostBlock(true);
								//rain.setSlantParticleToWind(true);

								boolean upward = rand.nextBoolean();

								rain.windWeight = 8F;
								rain.setFacePlayer(true);
								//SHADER COMPARE TEST
								rain.setFacePlayer(false);
								rain.facePlayerYaw = true;

								rain.setScale(9F + (rand.nextFloat() * 0.3F));
								//rain.setScale(25F);
								rain.setMaxAge(60);
								rain.setGravity(0.35F);
								//opted to leave the popin for rain, its not as bad as snow, and using fade in causes less rain visual overall
								rain.setTicksFadeInMax(20);
								rain.setAlphaF(0);
								rain.setTicksFadeOutMax(20);

								rain.rotationYaw = rain.getWorld().rand.nextInt(360) - 180F;
								rain.rotationPitch = 90;
								//SHADER COMPARE TEST
								rain.rotationPitch = 0;
								rain.setMotionY(-0.3D);
								/*rain.setMotionX(0);
								rain.setMotionZ(0);*/
								rain.setMotionX((rand.nextFloat() - 0.5F) * 0.01F);
								rain.setMotionZ((rand.nextFloat() - 0.5F) * 0.01F);

								if (weather.getRainType() == RainType.ACID) {
									rain.particleRed = acidRainRed;
									rain.particleGreen = acidRainGreen;
									rain.particleBlue = acidRainBlue;
								} else {
									rain.particleRed = vanillaRainRed;
									rain.particleGreen = vanillaRainGreen;
									rain.particleBlue = vanillaRainBlue;
								}

								rain.spawnAsWeatherEffect();
							}
						}
					}
				//snow
				} else {
					//Weather.dbg("rate: " + curPrecipVal * 5F * ConfigMisc.Particle_Precipitation_effect_rate);

					spawnCount = 0;
					//less for snow, since it falls slower so more is on screen longer
					spawnNeed = (int)(curPrecipVal * 40F * PRECIPITATION_PARTICLE_EFFECT_RATE * particleAmp);

					int spawnAreaSize = 50;

					if (spawnNeed > 0) {
						for (int i = 0; i < safetyCutout/*curPrecipVal * 20F * PRECIPITATION_PARTICLE_EFFECT_RATE*/; i++) {
							BlockPos pos = new BlockPos(
									entP.getPosX() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2),
									entP.getPosY() - 5 + rand.nextInt(25),
									entP.getPosZ() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2));

							if (canPrecipitateAt(world, pos)) {
								ParticleTexExtraRender snow = new ParticleTexExtraRender(entP.world, pos.getX(), pos.getY(), pos.getZ(),
										0D, 0D, 0D, ParticleRegistry.snow);

								snow.setCanCollide(false);
								snow.setKillWhenUnderTopmostBlock(true);
								snow.setTicksFadeOutMaxOnDeath(5);
								snow.setDontRenderUnderTopmostBlock(true);
								snow.setExtraParticlesBaseAmount(10);
								snow.killWhenFarFromCameraAtLeast = 20;

								snow.setMotionY(-0.1D);
								snow.setScale(1.3F);
								snow.setGravity(0.1F);
								snow.windWeight = 0.2F;
								snow.setMaxAge(40);
								snow.setFacePlayer(false);
								snow.setTicksFadeInMax(5);
								snow.setAlphaF(0);
								snow.setTicksFadeOutMax(5);
								//snow.setCanCollide(true);
								//snow.setKillOnCollide(true);
								snow.rotationYaw = snow.getWorld().rand.nextInt(360) - 180F;
								snow.spawnAsWeatherEffect();

								spawnCount++;
								if (spawnCount >= spawnNeed) {
									break;
								}
							}

						}
					}

				}
			}

			boolean groundFire = ClientWeather.get().isHeatwave();
			int spawnAreaSize = 40;

			if (groundFire) {
				for (int i = 0; i < 10F * PRECIPITATION_PARTICLE_EFFECT_RATE * particleAmp * 1F * adjustedRate; i++) {
					BlockPos pos = new BlockPos(
							entP.getPosX() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2),
							entP.getPosY() - 5 + rand.nextInt(15),
							entP.getPosZ() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2));


					//get the block on the topmost ground
					pos = world.getHeight(Heightmap.Type.MOTION_BLOCKING, pos).down();

					BlockState state = world.getBlockState(pos);
					double maxY = 0;
					double minY = 0;
					VoxelShape shape = state.getShape(world, pos);
					if (!shape.isEmpty()) {
						minY = shape.getBoundingBox().minY;
						maxY = shape.getBoundingBox().maxY;
					}

					if (pos.distanceSq(entP.getPosition()) > (spawnAreaSize / 2) * (spawnAreaSize / 2))
						continue;

					//block above topmost ground
					if (canPrecipitateAt(world, pos.up()) && world.getBlockState(pos).getMaterial() != Material.WATER) {

						world.addParticle(ParticleTypes.SMOKE, pos.getX() + rand.nextFloat(), pos.getY() + 0.01D + maxY, pos.getZ() + rand.nextFloat(), 0.0D, 0.0D, 0.0D);
						world.addParticle(ParticleTypes.FLAME, pos.getX() + rand.nextFloat(), pos.getY() + 0.01D + maxY, pos.getZ() + rand.nextFloat(), 0.0D, 0.0D, 0.0D);

					}
				}
			}
		}
	}

	public static boolean canPrecipitateAt(World world, BlockPos strikePosition)
	{
		return world.getHeight(Heightmap.Type.MOTION_BLOCKING, strikePosition).getY() <= strikePosition.getY();
	}

	public synchronized void tryParticleSpawning()
    {
    	try {
			for (Particle ent : spawnQueue) {
				if (ent != null/* && ent.world != null*/) {
					if (ent instanceof EntityRotFX) {
						((EntityRotFX) ent).spawnAsWeatherEffect();
					}
				}
			}
			for (Particle ent : spawnQueueNormal) {
				if (ent != null/* && ent.world != null*/) {
					Minecraft.getInstance().particles.addEffect(ent);
				}
			}
    	} catch (Exception ex) {
    		ex.printStackTrace();
    	}

        spawnQueue.clear();
        spawnQueueNormal.clear();
    }
	
	public void profileSurroundings()
    {
        //tryClouds();
        
    	Minecraft client = Minecraft.getInstance();
    	World worldRef = lastWorldDetected;
    	PlayerEntity player = Minecraft.getInstance().player;
        WeatherManagerClient manager = ClientTickHandler.weatherManager;
    	
        if (worldRef == null || player == null || manager == null || manager.wind == null)
        {
        	try {
        		Thread.sleep(1000L);
        	} catch (Exception ex) {
        		ex.printStackTrace();
        	}
            return;
        }

        if (threadLastWorldTickTime == worldRef.getGameTime())
        {
            return;
        }

        threadLastWorldTickTime = worldRef.getGameTime();
        
        Random rand = new Random();
        
        //mining a tree causes leaves to fall
        int size = 40;
        int hsize = size / 2;
        int curX = (int)player.getPosX();
        int curY = (int)player.getPosY();
        int curZ = (int)player.getPosZ();

        float windStr = manager.wind.getWindSpeed();

        //Wind requiring code goes below
        int spawnRate = (int)(30 / (windStr + 0.001));
        
        

        float lastBlockCount = lastTickFoundBlocks;
        
        float particleCreationRate = 0.7F;
        
        float maxScaleSample = 15000;
        if (lastBlockCount > maxScaleSample) lastBlockCount = maxScaleSample-1;
        float scaleRate = (maxScaleSample - lastBlockCount) / maxScaleSample;
        
        spawnRate = (int) ((spawnRate / (scaleRate + 0.001F)) / (particleCreationRate + 0.001F));
        
        spawnRate *= (client.gameSettings.particles.getId()+1);
        //since reducing threaded ticking to 200ms sleep, 1/4 rate, must decrease rand size
        spawnRate /= 2;
        
        //performance fix
        if (spawnRate < 40)
        {
            spawnRate = 40;
        }
        
        lastTickFoundBlocks = 0;
        
		double particleAmp = 1F;

		spawnRate = (int)((double)spawnRate / particleAmp);
        
        for (int xx = curX - hsize; xx < curX + hsize; xx++)
        {
            for (int yy = curY - (hsize / 2); yy < curY + hsize; yy++)
            {
                for (int zz = curZ - hsize; zz < curZ + hsize; zz++)
                {
                        //for (int i = 0; i < p_blocks_leaf.size(); i++)
                        //{
                            Block block = getBlock(worldRef, xx, yy, zz);
                            
                            //if (block != null && block.getMaterial() == Material.leaves)

					/*block.getMaterial() == Material.fire*/
					if (block != null && (block.getMaterial(block.getDefaultState()) == Material.LEAVES
									|| block.getMaterial(block.getDefaultState()) == Material.TALL_PLANTS ||
							block.getMaterial(block.getDefaultState()) == Material.PLANTS))
                            {
                            	
                            	lastTickFoundBlocks++;
                            	
                            	if (worldRef.rand.nextInt(spawnRate) == 0)
                                {
                            		//bottom of tree check || air beside vine check

									//far out enough to avoid having the AABB already inside the block letting it phase through more
									//close in as much as we can to make it look like it came from the block
									double relAdj = 0.70D;

									BlockPos pos = getRandomWorkingPos(worldRef, new BlockPos(xx, yy, zz));
									double xRand = 0;
									double yRand = 0;
									double zRand = 0;

									if (pos != null) {

										//further limit the spawn position along the face side to prevent it clipping into perpendicular blocks
										float particleAABB = 0.1F;
										float particleAABBAndBuffer = particleAABB + 0.05F;
										float invert = 1F - (particleAABBAndBuffer * 2F);

										if (pos.getY() != 0) {
											xRand = particleAABBAndBuffer + (rand.nextDouble() - 0.5D) * invert;
											zRand = particleAABBAndBuffer + (rand.nextDouble() - 0.5D) * invert;
										} else if (pos.getX() != 0) {
											yRand = particleAABBAndBuffer + (rand.nextDouble() - 0.5D) * invert;
											zRand = particleAABBAndBuffer + (rand.nextDouble() - 0.5D) * invert;
										} else if (pos.getZ() != 0) {
											yRand = particleAABBAndBuffer + (rand.nextDouble() - 0.5D) * invert;
											xRand = particleAABBAndBuffer + (rand.nextDouble() - 0.5D) * invert;
										}

										EntityRotFX var31 = new ParticleTexLeafColor(worldRef, xx, yy, zz, 0D, 0D, 0D, ParticleRegistry.leaf);
										var31.setPosition(xx + 0.5D + (pos.getX() * relAdj) + xRand,
												yy + 0.5D + (pos.getY() * relAdj) + yRand,
												zz + 0.5D + (pos.getZ() * relAdj) + zRand);
										var31.setPrevPosX(var31.getPosX());
										var31.setPrevPosY(var31.getPosY());
										var31.setPrevPosZ(var31.getPosZ());
										var31.setMotionX(0);
										var31.setMotionY(0);
										var31.setMotionZ(0);
										var31.setSize(particleAABB, particleAABB);
										//ParticleBreakingTemp test = new ParticleBreakingTemp(worldRef, (double)xx, (double)yy - 0.5, (double)zz, ParticleRegistry.leaf);
										var31.setGravity(0.05F);
										var31.setCanCollide(true);
										var31.setKillOnCollide(false);
										var31.collisionSpeedDampen = false;
										var31.killWhenUnderCameraAtLeast = 20;
										var31.killWhenFarFromCameraAtLeast = 20;
										var31.isTransparent = false;

										var31.rotationYaw = rand.nextInt(360);
										var31.rotationPitch = rand.nextInt(360);
										//var31.updateQuaternion(null);

										spawnQueue.add(var31);
									}
								}
                            }
					//}


                    
                }
            }
        }
    }

	/**
	 * Returns the successful relative position
	 *
	 * @param world
	 * @param posOrigin
	 * @return
	 */
    public static BlockPos getRandomWorkingPos(World world, BlockPos posOrigin) {
		Collections.shuffle(listPosRandom);
		for (BlockPos posRel : listPosRandom) {
			Block blockCheck = getBlock(world, posOrigin.add(posRel));

			if (blockCheck != null && CoroUtilBlock.isAir(blockCheck)) {
				return posRel;
			}
		}

		return null;
	}
	
	@OnlyIn(Dist.CLIENT)
    public static void tryWind(World world)
    {
		
		Minecraft client = Minecraft.getInstance();
		PlayerEntity player = client.player;

        if (player == null)
        {
            return;
        }


        WeatherManagerClient weatherMan = ClientTickHandler.weatherManager;
        if (weatherMan == null) return;
        WindManager windMan = weatherMan.getWindManager();
        if (windMan == null) return;

        //Weather Effects

		//System.out.println("particles moved: " + handleCount);

        //WindManager windMan = ClientTickHandler.weatherManager.windMan;

		//Built in particles
        if (WeatherUtilParticle.fxLayers != null && windMan.getWindSpeed() >= 0.10) {
			for (Queue<Particle> type : WeatherUtilParticle.fxLayers.values()) {
				for (Particle particle : type) {
	                    if (particle instanceof UnderwaterParticle) {
	                    	continue;
						}

	                    if ((WeatherUtilBlock.getPrecipitationHeightSafe(world, new BlockPos(MathHelper.floor(CoroUtilEntOrParticle.getPosX(particle)), 0, MathHelper.floor(CoroUtilEntOrParticle.getPosZ(particle)))).getY() - 1 < (int)CoroUtilEntOrParticle.getPosY(particle) + 1) || (particle instanceof ParticleTexFX))
	                    {
	                        if ((particle instanceof FlameParticle))
	                        {
	                        	if (windMan.getWindSpeed() >= 0.20) {
									particle.age += 1;
								}
	                        }

	                        //rustle!
							windMan.applyWindForceNew(particle, 1F/20F, 0.5F);
	                    }

                }
            }
        }
    }
	
	//Thread safe functions

	@OnlyIn(Dist.CLIENT)
	private static Block getBlock(World parWorld, BlockPos pos)
	{
		return getBlock(parWorld, pos.getX(), pos.getY(), pos.getZ());
	}

    @OnlyIn(Dist.CLIENT)
    private static Block getBlock(World parWorld, int x, int y, int z)
    {
        try
        {
            if (!parWorld.isBlockLoaded(new BlockPos(x, 0, z)))
            {
                return null;
            }

            return parWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    public static boolean isFogOverridding() {
		Minecraft client = Minecraft.getInstance();
		BlockState blockAtCamera = client.gameRenderer.getActiveRenderInfo().getBlockAtCamera();
		if (blockAtCamera.getMaterial().isLiquid()) return false;
    	return heatwaveIntensity > 0;
    }
    
    public static void renderTick(TickEvent.RenderTickEvent event) {
		Minecraft client = Minecraft.getInstance();
		ClientWeather weather = ClientWeather.get();
		//commented out hasWeather here for LT2020 because it was false before the transition was fully done, resulting in a tiny bit of rain that never goes away unless heatwave is active
		//quick fix instead of redesigning code, hopefully doesnt have side effects, this just constantly sets the rain amounts anyways
		if (client.world != null/* && weather.hasWeather()*/) {
			ClientTickHandler.checkClientWeather();
			client.world.setRainStrength(weather.getVanillaRainAmount());
		}
	}
}
