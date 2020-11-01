package weather2.client;

import CoroUtil.api.weather.IWindHandler;
import CoroUtil.forge.CULog;
import CoroUtil.util.ChunkCoordinatesBlock;
import CoroUtil.util.CoroUtilBlock;
import CoroUtil.util.CoroUtilEntOrParticle;
import com.lovetropics.minigames.common.minigames.weather.RainType;
import extendedrenderer.particle.ParticleRegistry;
import extendedrenderer.particle.behavior.ParticleBehaviorSandstorm;
import extendedrenderer.particle.behavior.ParticleBehaviors;
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
import net.minecraft.client.settings.ParticleStatus;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
import weather2.config.ConfigLTOverrides;
import weather2.config.ConfigMisc;
import weather2.config.ConfigParticle;
import weather2.util.*;
import weather2.weathersystem.WeatherManagerClient;
import weather2.weathersystem.wind.WindManager;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class SceneEnhancer implements Runnable {
	
	//this is for the thread we make
	public World lastWorldDetected = null;

	//used for acting on fire/smoke
	public static ParticleBehaviors pm;
	
	public static List<Particle> spawnQueueNormal = new ArrayList<>();
    public static List<Particle> spawnQueue = new ArrayList<>();
    
    public static long threadLastWorldTickTime;
    public static int lastTickFoundBlocks;
    public static long lastTickAmbient;
    public static long lastTickAmbientThreaded;
    
    //consider caching somehow without desyncing or overflowing
    //WE USE 0 TO MARK WATER, 1 TO MARK LEAVES
    public static ArrayList<ChunkCoordinatesBlock> soundLocations = new ArrayList<>();
    public static HashMap<ChunkCoordinatesBlock, Long> soundTimeLocations = new HashMap<>();
    
    public static Block SOUNDMARKER_WATER = Blocks.WATER;
    /*public static Block SOUNDMARKER_LEAVES = Blocks.LEAVES;*/
    public static List<Block> LEAVES_BLOCKS = new ArrayList<>();
    
    public static float curPrecipStr = 0F;
    public static float curPrecipStrTarget = 0F;
    
    public static float curOvercastStr = 0F;
    public static float curOvercastStrTarget = 0F;

    //sandstorm fog state
    public static float stormFogRed = 0;
    public static float stormFogGreen = 0;
    public static float stormFogBlue = 0;
    public static float stormFogRedOrig = 0;
    public static float stormFogGreenOrig = 0;
    public static float stormFogBlueOrig = 0;

    public static float stormFogStart = 0;
    public static float stormFogEnd = 0;

    public static float stormFogStartClouds = 0;
    public static float stormFogEndClouds = 0;
    public static float adjustAmountSmooth = 0F;

    public static ParticleBehaviorSandstorm particleBehavior;
	private int rainSoundCounter;

	private static List<BlockPos> listPosRandom = new ArrayList<>();

	public SceneEnhancer() {
		pm = new ParticleBehaviors(null);

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
				Thread.sleep(ConfigMisc.Thread_Particle_Process_Delay);
			} catch (Throwable throwable) {
                throwable.printStackTrace();
            }
		}
	}

	//run from client side _client_ thread
	public void tickClient() {
		if (!WeatherUtil.isPaused() && !ConfigMisc.Client_PotatoPC_Mode) {
			tryParticleSpawning();
			tickRainRates();
			tickParticlePrecipitation();
			trySoundPlaying();

			Minecraft client = Minecraft.getInstance();

			if (client.world != null && lastWorldDetected != client.world) {
				lastWorldDetected = client.world;
				reset();
			}

			tryWind(client.world);

			if (particleBehavior == null) {
				particleBehavior = new ParticleBehaviorSandstorm(null);
			}
			particleBehavior.tickUpdateList();
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
								if (cCor.block == SOUNDMARKER_WATER) {
									soundTimeLocations.put(cCor, System.currentTimeMillis() + 2500 + rand.nextInt(50));
									//client.getSoundHandler().playSound(Weather.modID + ":waterfall", cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), (float)ConfigMisc.volWaterfallScale, 0.75F + (rand.nextFloat() * 0.05F));
									//client.world.playSound(cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), Weather.modID + ":env.waterfall", (float)ConfigMisc.volWaterfallScale, 0.75F + (rand.nextFloat() * 0.05F), false);
									client.world.playSound(cCor, SoundRegistry.get("env.waterfall"), SoundCategory.AMBIENT, (float)ConfigMisc.volWaterfallScale, 0.75F + (rand.nextFloat() * 0.05F), false);
									//System.out.println("play waterfall at: " + cCor.getPosX() + " - " + cCor.getPosY() + " - " + cCor.getPosZ());
									//TODO: 1.14 test if this works
								} else if (LEAVES_BLOCKS.contains(cCor.block)) {
									
										
									float windSpeed = WindReader.getWindSpeed(client.world, new Vec3d(cCor), WindReader.WindType.EVENT);
									if (windSpeed > 0.2F) {
										soundTimeLocations.put(cCor, System.currentTimeMillis() + 12000 + rand.nextInt(50));
										//client.getSoundHandler().playSound(Weather.modID + ":wind_calmfade", cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), (float)(windSpeed * 4F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F));
										//client.world.playSound(cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), Weather.modID + ":env.wind_calmfade", (float)(windSpeed * 4F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F), false);
										client.world.playSound(cCor, SoundRegistry.get("env.wind_calmfade"), SoundCategory.AMBIENT, (float)(windSpeed * 4F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F), false);
										//System.out.println("play leaves sound at: " + cCor.getPosX() + " - " + cCor.getPosY() + " - " + cCor.getPosZ() + " - windSpeed: " + windSpeed);
									} else {
										windSpeed = WindReader.getWindSpeed(client.world, new Vec3d(cCor));
										//if (windSpeed > 0.3F) {
										if (client.world.rand.nextInt(15) == 0) {
											soundTimeLocations.put(cCor, System.currentTimeMillis() + 12000 + rand.nextInt(50));
											//client.getSoundHandler().playSound(Weather.modID + ":wind_calmfade", cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), (float)(windSpeed * 2F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F));
											//client.world.playSound(cCor.getPosX(), cCor.getPosY(), cCor.getPosZ(), Weather.modID + ":env.wind_calmfade", (float)(windSpeed * 2F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F), false);
											client.world.playSound(cCor, SoundRegistry.get("env.wind_calmfade"), SoundCategory.AMBIENT, (float)(windSpeed * 2F * ConfigMisc.volWindTreesScale), 0.70F + (rand.nextFloat() * 0.1F), false);
										}
											//System.out.println("play leaves sound at: " + cCor.getPosX() + " - " + cCor.getPosY() + " - " + cCor.getPosZ() + " - windSpeed: " + windSpeed);
										//}
									}
										
									
								}
								
							} else {
								//System.out.println("still waiting, diff: " + (lastPlayTime - System.currentTimeMillis()));
							}
	                    }
	            	}
	            }
			}

			boolean extraRainStuff = false;

			if (extraRainStuff) {
				Minecraft client = Minecraft.getInstance();

				float vanillaCutoff = 0.2F;
				float precipStrength = Math.abs(getRainStrengthAndControlVisuals(client.player, ClientTickHandler.clientConfigData.overcastMode));

				//if less than vanilla sound playing amount
				if (precipStrength <= vanillaCutoff) {

					float volAmp = 0.2F + ((precipStrength / vanillaCutoff) * 0.8F);

					Random random = new Random();

					float f = client.world.getRainStrength(1.0F);

					if (!client.gameSettings.fancyGraphics) {
						f /= 2.0F;
					}

					if (f != 0.0F) {
						//random.setSeed((long)this.rendererUpdateCount * 312987231L);
						Entity entity = client.getRenderViewEntity();
						World world = client.world;
						BlockPos blockpos = new BlockPos(entity);
						int i = 10;
						double d0 = 0.0D;
						double d1 = 0.0D;
						double d2 = 0.0D;
						int j = 0;
						int k = 3;//(int) (400.0F * f * f);

						if (client.gameSettings.particles == ParticleStatus.DECREASED) {
							k >>= 1;
						} else if (client.gameSettings.particles == ParticleStatus.MINIMAL) {
							k = 0;
						}

						for (int l = 0; l < k; ++l) {
							BlockPos blockpos1 = world.getHeight(Heightmap.Type.MOTION_BLOCKING, blockpos.add(random.nextInt(10) - random.nextInt(10), 0, random.nextInt(10) - random.nextInt(10)));
							Biome biome = world.getBiome(blockpos1);
							BlockPos blockpos2 = blockpos1.down();
							BlockState iblockstate = world.getBlockState(blockpos2);

							if (blockpos1.getY() <= blockpos.getY() + 10 && blockpos1.getY() >= blockpos.getY() - 10 && biome.getPrecipitation() == Biome.RainType.RAIN && biome.getTemperature(blockpos1) >= 0.15F) {
								double d3 = random.nextDouble();
								double d4 = random.nextDouble();
								//AxisAlignedBB axisalignedbb = iblockstate.getBoundingBox(world, blockpos2);
								VoxelShape shape = iblockstate.getCollisionShape(world, blockpos2);
								double maxY = 0;
								double minY = 0;
								if (!shape.isEmpty()) {
									minY = shape.getBoundingBox().minY;
									maxY = shape.getBoundingBox().maxY;
								}


								if (iblockstate.getMaterial() != Material.LAVA && iblockstate.getBlock() != Blocks.MAGMA_BLOCK) {
									if (iblockstate.getMaterial() != Material.AIR) {
										++j;

										if (random.nextInt(j) == 0) {
											d0 = (double) blockpos2.getX() + d3;
											d1 = (double) ((float) blockpos2.getY() + 0.1F) + maxY - 1.0D;
											d2 = (double) blockpos2.getZ() + d4;
										}

										client.world.addParticle(ParticleTypes.DRIPPING_WATER, false, (double) blockpos2.getX() + d3, (double) ((float) blockpos2.getY() + 0.1F) + maxY, (double) blockpos2.getZ() + d4, 0.0D, 0.0D, 0.0D);
									}
								} else {
									client.world.addParticle(ParticleTypes.SMOKE, false, (double) blockpos1.getX() + d3, (double) ((float) blockpos1.getY() + 0.1F) - minY, (double) blockpos1.getZ() + d4, 0.0D, 0.0D, 0.0D);
								}
							}
						}

						if (j > 0 && random.nextInt(3) < this.rainSoundCounter++) {
							this.rainSoundCounter = 0;

							if (d1 > (double) (blockpos.getY() + 1) && world.getHeight(Heightmap.Type.MOTION_BLOCKING, blockpos).getY() > MathHelper.floor((float) blockpos.getY())) {
								client.world.playSound(d0, d1, d2, SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.WEATHER, 0.1F * volAmp, 0.5F, false);
							} else {
								client.world.playSound(d0, d1, d2, SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER, 0.2F * volAmp, 1.0F, false);
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
    	
    	Random rand = new Random();
    	
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
                        	if (ConfigMisc.volWindTreesScale > 0 && ((block.getMaterial(block.getDefaultState()) == Material.LEAVES))) {
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
	
	public void tickParticlePrecipitation() {

		//if (true) return;

		if (ConfigParticle.Particle_RainSnow) {
			PlayerEntity entP = Minecraft.getInstance().player;

			WeatherManagerClient weatherMan = ClientTickHandler.weatherManager;
			if (weatherMan == null) return;
			WindManager windMan = weatherMan.getWindManager();
			if (windMan == null) return;

			ClientWeather weather = ClientWeather.get();

			float curPrecipVal = weather.getRainAmount();
			if (ConfigLTOverrides.vanillaRainOverride) {
				curPrecipVal = getRainStrengthAndControlVisuals(entP);
			} else {

			}

			
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
					int spawnNeed = (int)(curPrecipVal * 40F * ConfigParticle.Precipitation_Particle_effect_rate * particleAmp);
					int safetyCutout = 100;

					int extraRenderCount = 15;

					//attempt to fix the cluttering issue more noticable when barely anything spawning
					if (curPrecipVal < 0.1 && ConfigParticle.Precipitation_Particle_effect_rate > 0) {
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

						boolean rainParticle = ConfigParticle.Particle_Rain;
						boolean groundSplash = ConfigParticle.Particle_Rain_GroundSplash;
						boolean downfall = ConfigParticle.Particle_Rain_DownfallSheet;

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

						float acidRainRed = 0.7F;
						float acidRainGreen = 1F;
						float acidRainBlue = 0.7F;

						float vanillaRainRed = 0.7F;
						float vanillaRainGreen = 0.7F;
						float vanillaRainBlue = 1F;

						spawnAreaSize = 40;
						//ground splash
						if (groundSplash == true && curPrecipVal > 0.15) {
							for (int i = 0; i < 30F * curPrecipVal * ConfigParticle.Precipitation_Particle_effect_rate * particleAmp * 4F * adjustedRate; i++) {
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

							for (int i = 0; i < 2F * curPrecipVal * ConfigParticle.Precipitation_Particle_effect_rate * adjustedRate; i++) {
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
						spawnNeed = (int)(curPrecipVal * 40F * ConfigParticle.Precipitation_Particle_effect_rate * particleAmp);

						int spawnAreaSize = 50;

						if (spawnNeed > 0) {
							for (int i = 0; i < safetyCutout/*curPrecipVal * 20F * ConfigParticle.Precipitation_Particle_effect_rate*/; i++) {
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
					for (int i = 0; i < 10F * ConfigParticle.Precipitation_Particle_effect_rate * particleAmp * 1F * adjustedRate; i++) {
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
	}

	public static boolean canPrecipitateAt(World world, BlockPos strikePosition)
	{
		return world.getHeight(Heightmap.Type.MOTION_BLOCKING, strikePosition).getY() <= strikePosition.getY();
	}
	
	public static float getRainStrengthAndControlVisuals(PlayerEntity entP) {
		return getRainStrengthAndControlVisuals(entP, false);
	}

	/**
	 * Returns value between -1 to 1
	 * -1 is full on snow
	 * 1 is full on rain
	 * 0 is no precipitation
	 *
	 * also controls the client side raining and thundering values for vanilla
	 *
	 * @param entP
	 * @param forOvercast
	 * @return
	 */
	public static float getRainStrengthAndControlVisuals(PlayerEntity entP, boolean forOvercast) {
		
		Minecraft client = Minecraft.getInstance();

		ClientTickHandler.checkClientWeather();

	    float tempAdj = 1F;

		float overcastModeMinPrecip = 0.01F;

		if (!ClientTickHandler.clientConfigData.overcastMode) {
			client.world.getWorldInfo().setRaining(false);
			client.world.getWorldInfo().setThundering(false);

			if (forOvercast) {
				curOvercastStrTarget = 0;
			} else {
				curPrecipStrTarget = 0;
			}
		} else {
			if (ClientTickHandler.weatherManager.isVanillaRainActiveOnServer) {
				client.world.getWorldInfo().setRaining(true);
				client.world.getWorldInfo().setThundering(true);

				if (forOvercast) {
					curOvercastStrTarget = overcastModeMinPrecip;
				} else {
					curPrecipStrTarget = overcastModeMinPrecip;
				}
			} else {
				if (forOvercast) {
					curOvercastStrTarget = 0;
				} else {
					curPrecipStrTarget = 0;
				}
			}
		}
	    	
	    if (forOvercast) {
			if (curOvercastStr < 0.001 && curOvercastStr > -0.001F) {
				return 0;
			} else {
				return curOvercastStr * tempAdj;
			}
	    } else {
			if (curPrecipStr < 0.001 && curPrecipStr > -0.001F) {
				return 0;
			} else {
				return curPrecipStr * tempAdj;
			}
	    }
	}

	public static void tickRainRates() {

		float rateChange = 0.0005F;

		if (curOvercastStr > curOvercastStrTarget) {
			curOvercastStr -= rateChange;
		} else if (curOvercastStr < curOvercastStrTarget) {
			curOvercastStr += rateChange;
		}

		if (curPrecipStr > curPrecipStrTarget) {
			curPrecipStr -= rateChange;
		} else if (curPrecipStr < curPrecipStrTarget) {
			curPrecipStr += rateChange;
		}
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
    		CULog.err("Weather2: Error handling particle spawn queue: ");
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
    	
        if (worldRef == null || player == null || manager == null || manager.windMan == null)
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

        float windStr = manager.windMan.getWindSpeedForPriority();

        if ((!ConfigParticle.Wind_Particle_leafs && !ConfigParticle.Wind_Particle_waterfall)/* || weatherMan.wind.strength < 0.10*/)
        {
            return;
        }

        //Wind requiring code goes below
        int spawnRate = (int)(30 / (windStr + 0.001));
        
        

        float lastBlockCount = lastTickFoundBlocks;
        
        float particleCreationRate = (float) ConfigParticle.Wind_Particle_effect_rate;
        
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
                            
                            if (block != null && (block.getMaterial(block.getDefaultState()) == Material.LEAVES
									|| block.getMaterial(block.getDefaultState()) == Material.TALL_PLANTS ||
							block.getMaterial(block.getDefaultState()) == Material.PLANTS))
                            {
                            	
                            	lastTickFoundBlocks++;
                            	
                            	if (worldRef.rand.nextInt(spawnRate) == 0)
                                {
                            		//bottom of tree check || air beside vine check
	                                if (ConfigParticle.Wind_Particle_leafs) {

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
                            }
                           else if (ConfigParticle.Wind_Particle_fire && (block != null && block == Blocks.FIRE/*block.getMaterial() == Material.fire*/)) {
                            	lastTickFoundBlocks++;
                            	
                            	//
                            	if (worldRef.rand.nextInt(Math.max(1, (spawnRate / 100))) == 0) {
                            		double speed = 0.15D;
                            		//System.out.println("xx:" + xx);
                                	EntityRotFX entityfx = pm.spawnNewParticleIconFX(worldRef, ParticleRegistry.smoke, xx + rand.nextDouble(), yy + 0.2D + rand.nextDouble() * 0.2D, zz + rand.nextDouble(), (rand.nextDouble() - rand.nextDouble()) * speed, 0.03D, (rand.nextDouble() - rand.nextDouble()) * speed);//pm.spawnNewParticleWindFX(worldRef, ParticleRegistry.smoke, xx + rand.nextDouble(), yy + 0.2D + rand.nextDouble() * 0.2D, zz + rand.nextDouble(), (rand.nextDouble() - rand.nextDouble()) * speed, 0.03D, (rand.nextDouble() - rand.nextDouble()) * speed);
                                	ParticleBehaviors.setParticleRandoms(entityfx, true, true);
                                	ParticleBehaviors.setParticleFire(entityfx);
                                	entityfx.setMaxAge(100+rand.nextInt(300));
                                	spawnQueueNormal.add(entityfx);
                        			//entityfx.spawnAsWeatherEffect();
                        			//pm.particles.add(entityfx);
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

        Random rand = new Random();

		for (int i = 0; i < ClientTickHandler.weatherManager.listWeatherEffectedParticles.size(); i++) {
			Particle particle = ClientTickHandler.weatherManager.listWeatherEffectedParticles.get(i);

			if (!particle.isAlive()) {
				ClientTickHandler.weatherManager.listWeatherEffectedParticles.remove(i--);
			}
		}
        
        //Weather Effects

		//System.out.println("particles moved: " + handleCount);

        //WindManager windMan = ClientTickHandler.weatherManager.windMan;

		//Built in particles
        if (WeatherUtilParticle.fxLayers != null && windMan.getWindSpeedForPriority() >= 0.10) {
			for (Queue<Particle> type : WeatherUtilParticle.fxLayers.values()) {
				for (Particle particle : type) {
	                    if (ConfigParticle.Particle_VanillaAndWeatherOnly) {
	                    	String className = particle.getClass().getName();
	                    	if (className.contains("net.minecraft.") || className.contains("weather2.")) {
	                    		
	                    	} else {
	                    		continue;
	                    	}
	                    	//Weather.dbg("process: " + className);
	                    }

	                    if (particle instanceof UnderwaterParticle) {
	                    	continue;
						}
	
	                    if ((WeatherUtilBlock.getPrecipitationHeightSafe(world, new BlockPos(MathHelper.floor(CoroUtilEntOrParticle.getPosX(particle)), 0, MathHelper.floor(CoroUtilEntOrParticle.getPosZ(particle)))).getY() - 1 < (int)CoroUtilEntOrParticle.getPosY(particle) + 1) || (particle instanceof ParticleTexFX))
	                    {
	                        if ((particle instanceof FlameParticle))
	                        {
	                        	if (windMan.getWindSpeedForPriority() >= 0.20) {
									particle.age += 1;
								}
	                        }
	                        else if (particle instanceof IWindHandler) {
	                        	if (((IWindHandler)particle).getParticleDecayExtra() > 0 && WeatherUtilParticle.getParticleAge(particle) % 2 == 0)
	                            {
									particle.age += ((IWindHandler)particle).getParticleDecayExtra();
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
		BlockState iblockstate = client.gameRenderer.getActiveRenderInfo().getBlockAtCamera();
		if (iblockstate.getMaterial().isLiquid()) return false;
    	return adjustAmountSmooth > 0;
    }
    
    public static void renderTick(TickEvent.RenderTickEvent event) {

		if (ConfigMisc.Client_PotatoPC_Mode) return;

		if (!ConfigLTOverrides.vanillaRainOverride) {
			Minecraft client = Minecraft.getInstance();
			ClientWeather weather = ClientWeather.get();
			if (client.world != null && weather.hasWeather()) {
				ClientTickHandler.checkClientWeather();
				client.world.setRainStrength(weather.getVanillaRainAmount());
			}
			return;
		}

		if (event.phase == TickEvent.Phase.START) {
			Minecraft client = Minecraft.getInstance();
			PlayerEntity entP = client.player;
			if (entP != null) {
				float curRainStr = SceneEnhancer.getRainStrengthAndControlVisuals(entP, true);
				curRainStr = Math.abs(curRainStr);
				client.world.setRainStrength(curRainStr);
			}
		}
	}
}
