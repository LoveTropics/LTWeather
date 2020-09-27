package weather2.client;

import static CoroUtil.util.CoroUtilMisc.adjVal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import com.lovetropics.minigames.common.minigames.MinigameManager;
import com.mojang.blaze3d.platform.GlStateManager;

import CoroUtil.api.weather.IWindHandler;
import CoroUtil.config.ConfigCoroUtil;
import CoroUtil.forge.CULog;
import CoroUtil.util.ChunkCoordinatesBlock;
import CoroUtil.util.CoroUtilBlock;
import CoroUtil.util.CoroUtilCompatibility;
import CoroUtil.util.CoroUtilEntOrParticle;
import CoroUtil.util.CoroUtilMisc;
import CoroUtil.util.CoroUtilPhysics;
import extendedrenderer.EventHandler;
import extendedrenderer.particle.ParticleRegistry;
import extendedrenderer.particle.behavior.ParticleBehaviorFogGround;
import extendedrenderer.particle.behavior.ParticleBehaviorMiniTornado;
import extendedrenderer.particle.behavior.ParticleBehaviorSandstorm;
import extendedrenderer.particle.behavior.ParticleBehaviors;
import extendedrenderer.particle.entity.EntityRotFX;
import extendedrenderer.particle.entity.ParticleTexExtraRender;
import extendedrenderer.particle.entity.ParticleTexFX;
import extendedrenderer.particle.entity.ParticleTexLeafColor;
import extendedrenderer.render.RotatingParticleManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.UnderwaterParticle;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import weather2.ClientTickHandler;
import weather2.SoundRegistry;
import weather2.client.entity.particle.ParticleSandstorm;
import weather2.config.ConfigLTOverrides;
import weather2.config.ConfigMisc;
import weather2.config.ConfigParticle;
import weather2.config.ConfigStorm;
import weather2.util.WeatherUtil;
import weather2.util.WeatherUtilBlock;
import weather2.util.WeatherUtilDim;
import weather2.util.WeatherUtilEntity;
import weather2.util.WeatherUtilParticle;
import weather2.util.WeatherUtilSound;
import weather2.util.WindReader;
import weather2.weathersystem.WeatherManagerClient;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.WeatherObjectSandstorm;
import weather2.weathersystem.wind.WindManager;

@OnlyIn(Dist.CLIENT)
public class SceneEnhancer implements Runnable {
	
	//this is for the thread we make
	public World lastWorldDetected = null;

	//used for acting on fire/smoke
	public static ParticleBehaviors pm;
	
	public static List<Particle> spawnQueueNormal = new ArrayList();
    public static List<Particle> spawnQueue = new ArrayList();
    
    public static long threadLastWorldTickTime;
    public static int lastTickFoundBlocks;
    public static long lastTickAmbient;
    public static long lastTickAmbientThreaded;
    
    //consider caching somehow without desyncing or overflowing
    //WE USE 0 TO MARK WATER, 1 TO MARK LEAVES
    public static ArrayList<ChunkCoordinatesBlock> soundLocations = new ArrayList();
    public static HashMap<ChunkCoordinatesBlock, Long> soundTimeLocations = new HashMap();
    
    public static Block SOUNDMARKER_WATER = Blocks.WATER;
    /*public static Block SOUNDMARKER_LEAVES = Blocks.LEAVES;*/
    public static List<Block> LEAVES_BLOCKS = new ArrayList<>();
    
    public static float curPrecipStr = 0F;
    public static float curPrecipStrTarget = 0F;
    
    public static float curOvercastStr = 0F;
    public static float curOvercastStrTarget = 0F;
    
    //testing
    public static ParticleBehaviorMiniTornado miniTornado;
    
    public static ParticleBehaviorFogGround particleBehaviorFog;
    
    public static Vec3d vecWOP = null;
    
    //sandstorm fog state
    public static double distToStormThreshold = 100;
    public static double distToStorm = distToStormThreshold + 50;
    public static float stormFogRed = 0;
    public static float stormFogGreen = 0;
    public static float stormFogBlue = 0;
    public static float stormFogRedOrig = 0;
    public static float stormFogGreenOrig = 0;
    public static float stormFogBlueOrig = 0;
    public static float stormFogDensity = 0;
    public static float stormFogDensityOrig = 0;

    public static float stormFogStart = 0;
    public static float stormFogEnd = 0;
    public static float stormFogStartOrig = 0;
    public static float stormFogEndOrig = 0;
    
    public static float stormFogStartClouds = 0;
    public static float stormFogEndClouds = 0;
    public static float stormFogStartCloudsOrig = 0;
    public static float stormFogEndCloudsOrig = 0;
    
    public static boolean needFogState = true;
    
    public static float scaleIntensityTarget = 0F;
    public static float scaleIntensitySmooth = 0F;
    
    public static float adjustAmountTarget = 0F;
    public static float adjustAmountSmooth = 0F;

	public static float adjustAmountTargetPocketSandOverride = 0F;
    
    public static boolean isPlayerOutside = true;

    public static ParticleBehaviorSandstorm particleBehavior;

    public static ParticleTexExtraRender testParticle;

	public static EntityRotFX testParticle2;
	private int rainSoundCounter;

	private static List<BlockPos> listPosRandom = new ArrayList<>();

	public static List<EntityRotFX> testParticles = new ArrayList<>();

	public static Matrix4f matrix = new Matrix4f();
	public static Matrix4f matrix2 = new Matrix4f();

	public static Vector3f vec = new Vector3f();
	public static Vector3f vec2 = new Vector3f();

	//TODO: 1.14 uncomment
	//public static TornadoFunnel funnel;

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

			//tickTest();
			//tickTestFog();
			tickSandstorm();
			//tickTestSandstormParticles();


			if (particleBehavior == null) {
				particleBehavior = new ParticleBehaviorSandstorm(null);
			}
			particleBehavior.tickUpdateList();

			//TODO: 1.14 uncomment
			/*if (ConfigCoroUtil.foliageShaders && EventHandler.queryUseOfShaders()) {
				if (!FoliageEnhancerShader.useThread) {
					if (client.world.getGameTime() % 40 == 0) {
						FoliageEnhancerShader.tickClientThreaded();
					}
				}

				if (client.world.getGameTime() % 5 == 0) {
					FoliageEnhancerShader.tickClientCloseToPlayer();
				}
			}*/

		}
	}
	
	//run from our newly created thread
	public void tickClientThreaded() {
		Minecraft client = Minecraft.getInstance();

		//TEMP
		/*try {
			Thread.sleep(10000);
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}*/

		if (client != null && client.world != null && client.player != null && ClientTickHandler.minigameWeatherInstance.isMinigameActive()) {
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
                        	
                        	//Waterfall
                        	if (ConfigParticle.Wind_Particle_waterfall && ((block.getMaterial(block.getDefaultState()) == Material.WATER))) {
                            	
                            	/*int meta = getBlockMetadata(worldRef, xx, yy, zz);
                            	if ((meta & 8) != 0) {
                            		
                            		int bottomY = yy;
                            		int index = 0;
                            		
                            		//this scans to bottom till not water, kinda overkill? owell lets keep it, and also add rule if index > 4 (waterfall height of 4)
                            		while (yy-index > 0) {
                            			Block id2 = getBlock(worldRef, xx, yy-index, zz);
                            			if (id2 != null && !(id2.getMaterial(id2.getDefaultState()) == Material.WATER)) {
                            				break;
                            			}
                            			index++;
                            		}
                            		
                            		bottomY = yy-index+1;
                            		
                            		//check if +10 from here is water with right meta too
                            		int meta2 = getBlockMetadata(worldRef, xx, bottomY+10, zz);
                            		Block block2 = getBlock(worldRef, xx, bottomY+10, zz);;
                            		
                        			if (index >= 4 && (block2 != null && block2.getMaterial(block2.getDefaultState()) == Material.WATER && (meta2 & 8) != 0)) {
                        				boolean proxFail = false;
                        				for (int j = 0; j < soundLocations.size(); j++) {
                                			if (Math.sqrt(soundLocations.get(j).getDistanceSquared(xx, bottomY, zz)) < 5) {
                                				proxFail = true;
                                				break;
                                			}
                                		}
                        				
                        				if (!proxFail) {
                        					soundLocations.add(new ChunkCoordinatesBlock(xx, bottomY, zz, SOUNDMARKER_WATER, 0));
                        					//System.out.println("add waterfall");
                        				}
                        			}
                            	}*/
                            } else if (ConfigMisc.volWindTreesScale > 0 && ((block.getMaterial(block.getDefaultState()) == Material.LEAVES))) {
                            	boolean proxFail = false;
                				for (int j = 0; j < soundLocations.size(); j++) {
                        			if (Math.sqrt(soundLocations.get(j).distanceSq(new Vec3i(xx, yy, zz))) < 15) {
                        				proxFail = true;
                        				break;
                        			}
                        		}
                				
                				if (!proxFail) {
                					soundLocations.add(new ChunkCoordinatesBlock(xx, yy, zz, block));
                					//System.out.println("add leaves sound location");
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
			
			if (entP.getPosY() >= StormObject.static_YPos_layer0) return;

			WeatherManagerClient weatherMan = ClientTickHandler.weatherManager;
			if (weatherMan == null) return;
			WindManager windMan = weatherMan.getWindManager();
			if (windMan == null) return;


			float curPrecipVal = ClientTickHandler.minigameWeatherInstance.getParticleRainfallAmount();
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
			
			Biome biomegenbase = entP.world.getBiome(new BlockPos(MathHelper.floor(entP.getPosX()), 0, MathHelper.floor(entP.getPosZ())));

			World world = entP.world;
			Random rand = entP.world.rand;

			//System.out.println("ClientTickEvent time: " + world.getGameTime());

			double particleAmp = 1F;
			if (RotatingParticleManager.useShaders && ConfigCoroUtil.particleShaders) {
				particleAmp = ConfigMisc.shaderParticleRateAmplifier;
				//ConfigCoroAI.optimizedCloudRendering = true;
			} else {
				//ConfigCoroAI.optimizedCloudRendering = false;
			}

			//TODO: 1.14 uncomment
			/*if (funnel == null) {
				funnel = new TornadoFunnel();
				funnel.pos = new Vec3d(entP.getPosX(), entP.getPosY(), entP.getPosZ());
			}*/

			//funnel.tickGame();

			//check rules same way vanilla texture precip does
            if (biomegenbase != null && (biomegenbase.getPrecipitation() != Biome.RainType.NONE))
            {

            	//biomegenbase.getFloatTemperature(new BlockPos(MathHelper.floor(entP.getPosX()), MathHelper.floor(entP.getPosY()), MathHelper.floor(entP.getPosZ())));
				float temperature = CoroUtilCompatibility.getAdjustedTemperature(world, biomegenbase, entP.getPosition());
	            double d3;
	            float f10;

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

									if (!RotatingParticleManager.useShaders || !ConfigCoroUtil.particleShaders) {
										//old slanty rain way
										rain.setFacePlayer(true);
										rain.setSlantParticleToWind(true);
									} else {
										//new slanty rain way
										rain.setFacePlayer(false);
										rain.extraYRotation = rain.getWorld().rand.nextInt(360) - 180F;
									}

									//rain.setFacePlayer(true);
									rain.setScale(2F);
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
									ClientTickHandler.weatherManager.addWeatheredParticle(rain);

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

									rain.setScale(3F + (rand.nextFloat() * 3F));
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

									if (ClientTickHandler.minigameWeatherInstance.isLastRainWasAcid()) {
										rain.particleRed = acidRainRed;
										rain.particleGreen = acidRainGreen;
										rain.particleBlue = acidRainBlue;
									} else {
										rain.particleRed = vanillaRainRed;
										rain.particleGreen = vanillaRainGreen;
										rain.particleBlue = vanillaRainBlue;
									}

									rain.spawnAsWeatherEffect();
									ClientTickHandler.weatherManager.addWeatheredParticle(rain);
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
									ParticleTexExtraRender rain = new ParticleTexExtraRender(entP.world,
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
									rain.noExtraParticles = true;

									boolean upward = rand.nextBoolean();

									rain.windWeight = 8F;
									rain.setFacePlayer(true);
									//SHADER COMPARE TEST
									rain.setFacePlayer(false);
									rain.facePlayerYaw = true;

									rain.setScale(90F + (rand.nextFloat() * 3F));
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

									if (ClientTickHandler.minigameWeatherInstance.isLastRainWasAcid()) {
										rain.particleRed = acidRainRed;
										rain.particleGreen = acidRainGreen;
										rain.particleBlue = acidRainBlue;
									} else {
										rain.particleRed = vanillaRainRed;
										rain.particleGreen = vanillaRainGreen;
										rain.particleBlue = vanillaRainBlue;
									}

									rain.spawnAsWeatherEffect();
									ClientTickHandler.weatherManager.addWeatheredParticle(rain);
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
									ClientTickHandler.weatherManager.addWeatheredParticle(snow);

									spawnCount++;
									if (spawnCount >= spawnNeed) {
										break;
									}
								}

							}
						}

					}
				}

				boolean groundFire = ClientTickHandler.minigameWeatherInstance.isMinigameActive() && ClientTickHandler.minigameWeatherInstance.heatwaveActive();
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
		/*if (!world.isRaining())
		{
			return false;
		}
		else if (!world.canSeeSky(strikePosition))
		{
			return false;
		}
		else */if (world.getHeight(Heightmap.Type.MOTION_BLOCKING, strikePosition).getY() > strikePosition.getY())
		{
			return false;
		}/*
		else
		{
			Biome biome = world.getBiomeGenForCoords(strikePosition);
			return biome.getEnableSnow() ? false : (this.canSnowAt(strikePosition, false) ? false : biome.canRain());
		}*/
		return true;
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
		
		double maxStormDist = 512 / 4 * 3;
		Vec3d plPos = new Vec3d(entP.getPosX(), StormObject.static_YPos_layer0, entP.getPosZ());
		StormObject storm = null;
		
		ClientTickHandler.checkClientWeather();
		
		storm = ClientTickHandler.weatherManager.getClosestStorm(plPos, maxStormDist, StormObject.STATE_FORMING, true);
		
		if (forOvercast) {
			//storm = ClientTickHandler.weatherManager.getClosestStorm(plPos, maxStormDist, StormObject.STATE_THUNDER, true);
		} else {
			//storm = ClientTickHandler.weatherManager.getClosestStorm(plPos, maxStormDist, StormObject.STATE_FORMING, true);
			
			/*if (storm != null) {
				System.out.println("storm found? " + storm);
				System.out.println("storm water: " + storm.levelWater);
			}*/
		}
		
	    
	    
	    boolean closeEnough = false;
	    double stormDist = 9999;
	    float tempAdj = 1F;

    	float sizeToUse = 0;
	    
	    float overcastModeMinPrecip = 0.23F;
		//overcastModeMinPrecip = 0.16F;
		overcastModeMinPrecip = (float)ConfigStorm.Storm_Rain_Overcast_Amount;
	    
	    //evaluate if storms size is big enough to be over player
	    if (storm != null) {
	    	
	    	sizeToUse = storm.size;
	    	//extend overcast effect, using x2 for now since we cant cancel sound and ground particles, originally was 4x, then 3x, change to that for 1.7 if lex made change
	    	if (forOvercast) {
	    		sizeToUse *= 1F;
	    	}
	    	
	    	stormDist = storm.pos.distanceTo(plPos);
	    	//System.out.println("storm dist: " + stormDist);
	    	if (sizeToUse > stormDist) {
	    		closeEnough = true;
	    	}
	    }
	    
	    if (closeEnough) {
	    	
		    
		    double stormIntensity = (sizeToUse - stormDist) / sizeToUse;
		    
		    tempAdj = storm.levelTemperature > 0 ? 1F : -1F;
		    
		    //limit plain rain clouds to light intensity
		    if (storm.levelCurIntensityStage == StormObject.STATE_NORMAL) {
		    	if (stormIntensity > 0.3) stormIntensity = 0.3;
		    }
		    
		    if (ConfigStorm.Storm_NoRainVisual) {
		    	stormIntensity = 0;
		    }

		    if (stormIntensity < overcastModeMinPrecip) {
		    	stormIntensity = overcastModeMinPrecip;
			}
		    
		    //System.out.println("intensity: " + stormIntensity);
	    	client.world.getWorldInfo().setRaining(true);
	    	client.world.getWorldInfo().setThundering(true);
	    	if (forOvercast) {
	    		curOvercastStrTarget = (float) stormIntensity;
	    	} else {
	    		curPrecipStrTarget = (float) stormIntensity;
	    	}
	    	//client.world.thunderingStrength = (float) stormIntensity;
	    } else {
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
	    	
	    	//client.world.setRainStrength(0);
	    	//client.world.thunderingStrength = 0;
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

	public static float getPrecipStrength(PlayerEntity entP, boolean forOvercast) {
		StormObject storm = getClosestStormCached(entP);
		if (storm != null) {
			float tempAdj = storm.levelTemperature > 0 ? 1F : -1F;

			if (forOvercast) {
				return curOvercastStr * tempAdj;
			} else {
				return curPrecipStr * tempAdj;
			}
		}

		return 0;
	}

	public static void controlVanillaPrecipVisuals(PlayerEntity entP, boolean forOvercast) {

	}

	public static StormObject getClosestStormCached(PlayerEntity entP) {
		if (WeatherManagerClient.closestStormCached == null || entP.world.getGameTime() % 5 == 0) {
			Minecraft client = Minecraft.getInstance();

			double maxStormDist = 512 / 4 * 3;
			Vec3d plPos = new Vec3d(entP.getPosX(), StormObject.static_YPos_layer0, entP.getPosZ());

			ClientTickHandler.checkClientWeather();

			WeatherManagerClient.closestStormCached = ClientTickHandler.weatherManager.getClosestStorm(plPos, maxStormDist, StormObject.STATE_FORMING, true);
		}

		return WeatherManagerClient.closestStormCached;
	}
	
	public synchronized void tryParticleSpawning()
    {
    	if (spawnQueue.size() > 0) {
    		//System.out.println("spawnQueue.size(): " + spawnQueue.size());
    	}
    	
    	try {
	        for (int i = 0; i < spawnQueue.size(); i++)
	        {
	            Particle ent = spawnQueue.get(i);
	
	            if (ent != null/* && ent.world != null*/) {
	            
		            if (ent instanceof EntityRotFX)
		            {
		                ((EntityRotFX) ent).spawnAsWeatherEffect();
		            }/*
		            else
		            {
		                ent.world.addWeatherEffect(ent);
		            }*/
		            ClientTickHandler.weatherManager.addWeatheredParticle(ent);
	            }
	        }
	        for (int i = 0; i < spawnQueueNormal.size(); i++)
	        {
	        	Particle ent = spawnQueueNormal.get(i);
	
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
        //if (true) return;
        
        float windStr = manager.windMan.getWindSpeedForPriority();//(weatherMan.wind.strength <= 1F ? weatherMan.wind.strength : 1F);

        /*if (client.objectMouseOver != null && client.objectMouseOver.getBlockPos() != null) {
        	Block id = client.world.getBlockState(new BlockPos(client.objectMouseOver.getBlockPos().getX(), client.objectMouseOver.getBlockPos().getY(), client.objectMouseOver.getBlockPos().getZ())).getBlock();
        	//System.out.println(client.world.getBlockStateId(client.objectMouseOver.blockX,client.objectMouseOver.blockY,client.objectMouseOver.blockZ));
        	if (CoroUtilBlock.isAir(id) && id.getMaterial() == Material.wood) {
        		float var5 = 0;

        		var5 = (Float)OldUtil.getPrivateValueSRGMCP(PlayerControllerMP.class, (PlayerControllerMP)client.playerController, OldUtil.refl_curBlockDamageMP_obf, OldUtil.refl_curBlockDamageMP_mcp);

                if (var5 > 0) {
                	//weather2 disabled for now
                	//shakeTrees(8);
                }
        	}
        }*/

        //FoliageEnhancerShader.tickThreaded();


        if ((!ConfigParticle.Wind_Particle_leafs && !ConfigParticle.Wind_Particle_waterfall)/* || weatherMan.wind.strength < 0.10*/)
        {
            return;
        }

        //Wind requiring code goes below
        int spawnRate = (int)(30 / (windStr + 0.001));
        
        

        float lastBlockCount = lastTickFoundBlocks;
        
        float particleCreationRate = (float) ConfigParticle.Wind_Particle_effect_rate;
        
        //TEST OVERRIDE
        //uh = (lastBlockCount / 30) + 1;
        float maxScaleSample = 15000;
        if (lastBlockCount > maxScaleSample) lastBlockCount = maxScaleSample-1;
        float scaleRate = (maxScaleSample - lastBlockCount) / maxScaleSample;
        
        spawnRate = (int) ((spawnRate / (scaleRate + 0.001F)) / (particleCreationRate + 0.001F));
        
        int BlockCountRate = (int)(((300 / scaleRate + 0.001F)) / (particleCreationRate + 0.001F)); 
        
        spawnRate *= (client.gameSettings.particles.getId()+1);
        BlockCountRate *= (client.gameSettings.particles.getId()+1);
        
        //since reducing threaded ticking to 200ms sleep, 1/4 rate, must decrease rand size
        spawnRate /= 2;
        
        //performance fix
        if (spawnRate < 40)
        {
            spawnRate = 40;
        }
        
        //performance fix
        if (BlockCountRate < 80) BlockCountRate = 80;
        //patch for block counts over 15000
        if (BlockCountRate > 5000) BlockCountRate = 5000;
        
        //TEMP!!!
        //uh = 10;
        
        //System.out.println("lastTickFoundBlocks: " + lastTickFoundBlocks + " - rand size: " + uh + " - " + BlockCountRate);
        
        lastTickFoundBlocks = 0;
        
        //Wind_Particle_waterfall = true;
        //Wind_Particle_leafs = true;
        //debug = true;
        //if (true) return;

		double particleAmp = 1F;
		if (RotatingParticleManager.useShaders && ConfigCoroUtil.particleShaders) {
			particleAmp = ConfigMisc.shaderParticleRateAmplifier * 2D;
			//ConfigCoroAI.optimizedCloudRendering = true;
		} else {
			//ConfigCoroAI.optimizedCloudRendering = false;
		}

		spawnRate = (int)((double)spawnRate / particleAmp);
        
        //if (debug) System.out.println("windStr: " + windStr + " chance: " + uh);
        //Semi intensive area scanning code
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
											//var31.setSize(1, 1);
											//var31.setKillWhenUnderTopmostBlock(true);

											//System.out.println("add particle");
											//Minecraft.getInstance().particles.addEffect(var31);
											//ExtendedRenderer.rotEffRenderer.addEffect(test);
											//ExtendedRenderer.rotEffRenderer.addEffect(var31);
											//WeatherUtil.setParticleGravity((EntityFX)var31, 0.1F);

											//worldRef.addParticle(EnumParticleTypes.FALLING_DUST, (double)xx, (double)yy, (double)zz, 0.0D, 0.0D, 0.0D, 0);

											/*for (int ii = 0; ii < 10; ii++)
											{
												applyWindForce(var31);
											}*/

											var31.rotationYaw = rand.nextInt(360);
											var31.rotationPitch = rand.nextInt(360);
											//var31.updateQuaternion(null);

											spawnQueue.add(var31);
										}

	                                }
	                                else
	                                {
	                                    /*if (Wind_Particle_leafs)
	                                    {
	                                        //This is non leaves, as in wildgrass or wahtever is in the p_blocks_leaf list (no special rules)
	                                        EntityRotFX var31 = new EntityTexFX(worldRef, (double)xx, (double)yy + 0.5, (double)zz, 0D, 0D, 0D, 10D, 0, effLeafID);
	                                        c_CoroWeatherUtil.setParticleGravity((EntityFX)var31, 0.1F);
	                                        var31.rotationYaw = rand.nextInt(360);
	                                        //var31.spawnAsWeatherEffect();
	                                        spawnQueue.add(var31);
	                                        //client.particles.addEffect(var31);
	                                        
	                                        //System.out.println("leaf spawn!");
	                                    }*/
	                                }
                                }
                            }
                            else if (ConfigParticle.Wind_Particle_waterfall && player.getDistanceSq(xx, yy, zz) < 16 * 16 && (block != null && block.getMaterial(block.getDefaultState()) == Material.WATER)) {

                            	//mostly sure this was just a check to verify its a full height water block
								//but what about oceans?
								/** TODO: 1.14, were assuming meta & 8 USED to be a flow vector but was changed, redesign to either:
								 * do a proper flow vector check, or
								 * check next to block for air and count it as waterfall then
								 * - should prevent oceans qualifying
								 */

                            	/*int meta = getBlockMetadata(worldRef, xx, yy, zz);
                            	if ((meta & 8) != 0) {
                            		lastTickFoundBlocks += 70; //adding more to adjust for the rate 1 waterfall block spits out particles
                            		int chance = (int)(1+(((float)BlockCountRate)/120F));
                            		
                            		Block block2 = getBlock(worldRef, xx, yy-1, zz);
                            		int meta2 = getBlockMetadata(worldRef, xx, yy-1, zz);
                            		Block block3 = getBlock(worldRef, xx, yy+10, zz);
                            		//Block block2 = Block.blocksList[id2];
                            		//Block block3 = Block.blocksList[id3];
                            		
                            		//if ((block2 == null || block2.getMaterial() != Material.water) && (block3 != null && block3.getMaterial() == Material.water)) {
                            			//chance /= 3;
                            			
                            		//}
                            		//System.out.println("woot! " + chance);
                                	if ((((block2 == null || block2.getMaterial(block2.getDefaultState()) != Material.WATER) || (meta2 & 8) == 0) && (block3 != null && block3.getMaterial(block3.getDefaultState()) == Material.WATER)) || worldRef.rand.nextInt(chance) == 0) {
                            		
	                            		float range = 0.5F;
	                            		
	                            		EntityRotFX waterP;
	                            		//if (rand.nextInt(10) == 0) {
	                            			//waterP = new EntityBubbleFX(worldRef, (double)xx + 0.5F + ((rand.nextFloat() * range) - (range/2)), (double)yy + 0.5F + ((rand.nextFloat() * range) - (range/2)), (double)zz + 0.5F + ((rand.nextFloat() * range) - (range/2)), 0D, 0D, 0D);
	                            		//} else {
	                            		waterP = new EntityWaterfallFX(worldRef, (double)xx + 0.5F + ((rand.nextFloat() * range) - (range/2)), (double)yy + 0.5F + ((rand.nextFloat() * range) - (range/2)), (double)zz + 0.5F + ((rand.nextFloat() * range) - (range/2)), 0D, 0D, 0D, 6D, 2);
	                            		//}
                                	
	                            		
	                            		
                            			if (((block2 == null || block2.getMaterial(block2.getDefaultState()) != Material.WATER) || (meta2 & 8) == 0) && (block3 != null && block3.getMaterial(block3.getDefaultState()) == Material.WATER)) {
                            				
                            				range = 2F;
                            				float speed = 0.2F;
                            				
                            				for (int i = 0; i < 10; i++) {
                            					if (worldRef.rand.nextInt(chance / 2) == 0) {
                            						waterP = new EntityWaterfallFX(worldRef, 
    	                            						(double)xx + 0.5F + ((rand.nextFloat() * range) - (range/2)), 
    	                            						(double)yy + 0.7F + ((rand.nextFloat() * range) - (range/2)), 
    	                            						(double)zz + 0.5F + ((rand.nextFloat() * range) - (range/2)),
    	                            						((rand.nextFloat() * speed) - (speed/2)),
    	                            						((rand.nextFloat() * speed) - (speed/2)),
    	                            						((rand.nextFloat() * speed) - (speed/2)),
    	                            						2D, 3);
    	                            				//waterP.motionX = -1.5F;
    	                            				waterP.setMotionY(4.5F);
    	                            				//System.out.println("woot! " + chance);
    	                            				spawnQueueNormal.add(waterP);
                            					}
	                            				
                            				}
                            			} else {
                            				waterP = new EntityWaterfallFX(worldRef, 
                            						(double)xx + 0.5F + ((rand.nextFloat() * range) - (range/2)), 
                            						(double)yy + 0.5F + ((rand.nextFloat() * range) - (range/2)), 
                            						(double)zz + 0.5F + ((rand.nextFloat() * range) - (range/2)), 0D, 0D, 0D, 6D, 2);
                            				
                            				waterP.setMotionY(0.5F);
                            				
                            				spawnQueueNormal.add(waterP);
                            			}
	                            			
	                            		
	                            		//waterP.rotationYaw = rand.nextInt(360);
	                                	
                                	}
                            	}*/
                            	
                            } else if (ConfigParticle.Wind_Particle_fire && (block != null && block == Blocks.FIRE/*block.getMaterial() == Material.fire*/)) {
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

        int dist = 60;

        
        WeatherManagerClient weatherMan = ClientTickHandler.weatherManager;
        if (weatherMan == null) return;
        WindManager windMan = weatherMan.getWindManager();
        if (windMan == null) return;

        //Chunk Entities
        //we're not moving chunk entities with wind this way ....... i think, only weather events like spinning etc
        /*if (list != null)
        {
            for (int i = 0; i < list.size(); i++)
            {
                Entity entity1 = (Entity)list.get(i);

                if (canPushEntity(entity1) && !(entity1 instanceof EntityPlayer))
                {
                    applyWindForce(entity1, 1F);
                }
            }
        }*/
        
        Random rand = new Random();
        
        int handleCount = 0;

        if (world.getGameTime() % 60 == 0) {
			//System.out.println("weather particles: " + ClientTickHandler.weatherManager.listWeatherEffectedParticles.size());
		}

		for (int i = 0; i < ClientTickHandler.weatherManager.listWeatherEffectedParticles.size(); i++) {
			Particle particle = ClientTickHandler.weatherManager.listWeatherEffectedParticles.get(i);

			if (!particle.isAlive()) {
				ClientTickHandler.weatherManager.listWeatherEffectedParticles.remove(i--);
				continue;
			}
		}
        
        //Weather Effects
		//TODO: 1.14 flip to false when we readd our particle renderer
		boolean usingVanillaParticleRenderer = true;
        if (!usingVanillaParticleRenderer) {
			for (int i = 0; i < ClientTickHandler.weatherManager.listWeatherEffectedParticles.size(); i++) {

				Particle particle = ClientTickHandler.weatherManager.listWeatherEffectedParticles.get(i);

				/*if (!particle.isAlive()) {
					ClientTickHandler.weatherManager.listWeatherEffectedParticles.remove(i--);
					continue;
				}*/

				if (ClientTickHandler.weatherManager.windMan.getWindSpeedForPriority() >= 0.10) {

					handleCount++;

					if (particle instanceof EntityRotFX) {

						EntityRotFX entity1 = (EntityRotFX) particle;

						if (entity1 == null) {
							continue;
						}

						if ((WeatherUtilBlock.getPrecipitationHeightSafe(world, new BlockPos(MathHelper.floor(entity1.getPosX()), 0, MathHelper.floor(entity1.getPosZ()))).getY() - 1 < (int) entity1.getPosY() + 1) || (entity1 instanceof ParticleTexFX)) {
							if (entity1 instanceof IWindHandler) {
								if (((IWindHandler) entity1).getParticleDecayExtra() > 0 && WeatherUtilParticle.getParticleAge(entity1) % 2 == 0) {
									WeatherUtilParticle.setParticleAge(entity1, WeatherUtilParticle.getParticleAge(entity1) + ((IWindHandler) entity1).getParticleDecayExtra());
								}
							} else if (WeatherUtilParticle.getParticleAge(entity1) % 2 == 0) {
								WeatherUtilParticle.setParticleAge(entity1, WeatherUtilParticle.getParticleAge(entity1) + 1);
							}

							if ((entity1 instanceof ParticleTexFX) && ((ParticleTexFX) entity1).getParticleTexture() == ParticleRegistry.leaf/*((ParticleTexFX)entity1).getParticleTextureIndex() == WeatherUtilParticle.effLeafID*/) {
								if (entity1.getMotionX() < 0.01F && entity1.getMotionZ() < 0.01F) {
									entity1.setMotionY(entity1.getMotionY() + rand.nextDouble() * 0.02 * ((ParticleTexFX) entity1).getParticleGravity());
								}

								entity1.setMotionY(entity1.getMotionY() - 0.01F * ((ParticleTexFX) entity1).getParticleGravity());

							}
						}

						windMan.applyWindForceNew(entity1, 1F / 20F, 0.5F);
					}
				}
			}
		}
        
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
							//TODO: 1.14 uncomment, tho i think EntityWaterfallFX stopped being used in 1.12?

	                        if (false/*!(entity1 instanceof EntityWaterfallFX)*/) {
		                        if (Math.abs(CoroUtilEntOrParticle.getMotionX(particle)) < 0.01F && Math.abs(CoroUtilEntOrParticle.getMotionZ(particle)) < 0.01F)
		                        {
		                            //ent.setMotionY(ent.getMotionY() + rand.nextDouble() * 0.02);
		                            CoroUtilEntOrParticle.setMotionY(particle, CoroUtilEntOrParticle.getMotionY(particle) + rand.nextDouble() * 0.02);
		                        }
	                        }

	                        windMan.applyWindForceNew(particle, 1F/20F, 0.5F);
	                    }

                }
            }
        }

        //this was code to push player around if really windy, lets not do this anymore, who slides around in wind IRL?
        //maybe maybe if a highwind/hurricane state is active, adjust their ACTIVE movement to adhere to wind vector
        /*if (windMan.getWindSpeedForPriority() >= 0.70)
        {
            if (WeatherUtilEntity.canPushEntity(player))
            {
                applyWindForce(player, 0.2F);
            }
        }*/

        //NEEEEEEEED TO STOP WIND WHEN UNDERGROUND!
		//we kinda did, is it good enough?
        float volScaleFar = windMan.getWindSpeedForPriority() * 1F;

        if (windMan.getWindSpeedForPriority() <= 0.07F)
        {
            volScaleFar = 0F;
        }
        
        volScaleFar *= ConfigMisc.volWindScale;

        //Sound whistling noise
        //First, use volume to represent intensity, maybe get different sound samples for higher level winds as they sound different
        //Second, when facing towards wind, you're ears hear it tearing by you more, when turned 90 degrees you do not, simulate this
        
        //weather2: commented out to test before sound code goes in!!!!!!!!!!!!!!!!!!!!!
        /*tryPlaySound(WeatherUtil.snd_wind_far, 2, client.player, volScaleFar);

        if (lastSoundPositionUpdate < System.currentTimeMillis())
        {
            lastSoundPositionUpdate = System.currentTimeMillis() + 100;

            if (soundID[2] > -1 && soundTimer[2] < System.currentTimeMillis())
            {
                setVolume(new StringBuilder().append("sound_" + soundID[2]).toString(), volScaleFar);
            }
        }*/
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
    
    /*@OnlyIn(Dist.CLIENT)
    private static int getBlockMetadata(World parWorld, int x, int y, int z)
    {
        if (!parWorld.isBlockLoaded(new BlockPos(x, 0, z)))
        {
            return 0;
        }

        BlockState state = parWorld.getBlockState(new BlockPos(x, y, z));
        return state.getBlock().getMetaFromState(state);
    }*/
    
    public static void tickTest() {
    	Minecraft client = Minecraft.getInstance();
    	if (miniTornado == null) {
    		miniTornado = new ParticleBehaviorMiniTornado(new Vec3d(client.player.getPosX(), client.player.getPosY(), client.player.getPosZ()));
    	}
    	
    	//temp
    	//miniTornado.coordSource = new Vec3d(client.player.getPosX(), client.player.getPosY(), client.player.getPosZ() - 4);
    	
    	
    	
    	if (true || miniTornado.particles.size() == 0) {
	    	for (int i = 0; i < 1; i++) {
	    		ParticleTexFX part = new ParticleTexFX(client.world, miniTornado.coordSource.x, miniTornado.coordSource.y, miniTornado.coordSource.z, 0, 0, 0, ParticleRegistry.squareGrey);
	    		miniTornado.initParticle(part);
	    		miniTornado.particles.add(part);
	    		part.spawnAsWeatherEffect();
	    	}
    	}
		
    	miniTornado.tickUpdateList();
    	
    	double x = 5;
    	double z = 5;
    	double x2 = 5;
    	double z2 = 0;
    	
    	double vecX = x - x2;
    	double vecZ = z - z2;
    	
    	double what = Math.atan2(vecZ, vecX);
    	
    	//System.out.println(Math.toDegrees(what));
    }
    
    public static void tickTestFog() {
    	Minecraft client = Minecraft.getInstance();
    	if (particleBehaviorFog == null) {
    		
    		particleBehaviorFog = new ParticleBehaviorFogGround(new Vec3d(client.player.getPosX(), client.player.getPosY(), client.player.getPosZ()));
    	} else {
    		particleBehaviorFog.coordSource = new Vec3d(client.player.getPosX(), client.player.getPosY() + 0.5D, client.player.getPosZ());
    	}
    	
    	if (client.world.getGameTime() % 300 == 0) {
	    	if (/*true || */particleBehaviorFog.particles.size() <= 10000) {
		    	for (int i = 0; i < 1; i++) {
		    		ParticleTexFX part = new ParticleTexFX(client.world, particleBehaviorFog.coordSource.x, particleBehaviorFog.coordSource.y, particleBehaviorFog.coordSource.z
		    				, 0, 0, 0, ParticleRegistry.cloud256);
		    		part.setMotionX(-1);
		    		part.setMotionY(0.1);
		    		particleBehaviorFog.initParticle(part);
		    		//particleBehaviorFog.particles.add(part);
		    		part.spawnAsWeatherEffect();
		    		part.windWeight = 5F;
		    		part.debugID = 1;
		    		part.setMaxAge(280);
		    		part.setVanillaMotionDampen(false);
		    		ClientTickHandler.weatherManager.addWeatheredParticle(part);
		    	}
	    	}
    	}
    	
    	particleBehaviorFog.tickUpdateList();
    }
    
    public static void tickTestSandstormParticles() {
    	Minecraft client = Minecraft.getInstance();
    	
    	//vecWOP = null;
    	
    	if (vecWOP == null) {
    		particleBehaviorFog = new ParticleBehaviorFogGround(new Vec3d(client.player.getPosX(), client.player.getPosY(), client.player.getPosZ()));
    		vecWOP = new Vec3d(client.player.getPosX(), client.player.getPosY(), client.player.getPosZ());
    	}

    	//need circumference math to keep constant distance between particles to spawn based on size of storm
    	//this also needs adjusting based on the chosed particle scale (that is based on players distance to storm)
    	
    	//if (!isInside) {
    		for (int i = 0; i < 0; i++) {
	    		ParticleTexFX part = new ParticleTexFX(client.world, vecWOP.x, vecWOP.y, vecWOP.z
	    				, 0, 0, 0, ParticleRegistry.cloud256);
	    		particleBehaviorFog.initParticle(part);
	    		part.setFacePlayer(false);
	    		
	    		//particleBehaviorFog.particles.add(part);
	    		part.spawnAsWeatherEffect();
    		}
    	//}
    }
    
    /**
     * Manages transitioning fog densities and color from current vanilla settings to our desired settings, and vice versa
     */
    public static void tickSandstorm() {

		if (adjustAmountTargetPocketSandOverride > 0) {
			adjustAmountTargetPocketSandOverride -= 0.01F;
		}

    	Minecraft client = Minecraft.getInstance();
        PlayerEntity player = client.player;
        World world = client.world;
    	Vec3d posPlayer = new Vec3d(client.player.getPosX(), 0/*client.player.getPosY()*/, client.player.getPosZ());
    	WeatherObjectSandstorm sandstorm = ClientTickHandler.weatherManager.getClosestSandstormByIntensity(posPlayer);
        WindManager windMan = ClientTickHandler.weatherManager.getWindManager();
    	float scaleIntensityTarget = 0F;
    	if (sandstorm != null) {

			if (client.world.getGameTime() % 40 == 0) {
				isPlayerOutside = WeatherUtilEntity.isEntityOutside(client.player);
				//System.out.println("isPlayerOutside: " + isPlayerOutside);
			}


    		scaleIntensityTarget = sandstorm.getSandstormScale();
    		/*Vec3d posNoY = new Vec3d(sandstorm.pos);
    		posNoY.yCoord = client.player.getPosY();
    		distToStorm = posPlayer.distanceTo(posNoY);*/
    		
    		List<Vec3d> field_75884_a = sandstorm.getSandstormAsShape();
    		
    		/*for (Vec3d point : field_75884_a) {
    			System.out.println("point: " + point.toString());
    		}*/
    		
    		boolean inStorm = CoroUtilPhysics.isInConvexShape(posPlayer, field_75884_a);
        	if (inStorm) {
        		//System.out.println("in storm");
        		distToStorm = 0;
        	} else {
        		
        		distToStorm = CoroUtilPhysics.getDistanceToShape(posPlayer, field_75884_a);
        		//System.out.println("near storm: " + distToStorm);
        	}
    	} else {
    		distToStorm = distToStormThreshold + 10;
    	}

    	boolean ltOverride = false;
    	ClientTickHandler.checkClientWeather();
    	ltOverride = ClientTickHandler.minigameWeatherInstance.isMinigameActive() && ClientTickHandler.minigameWeatherInstance.heatwaveActive();
		//ltOverride = true;
    	if (ltOverride) {
			scaleIntensityTarget = 0.7F;
			distToStorm = 10;
		}
    	
    	scaleIntensitySmooth = adjVal(scaleIntensitySmooth, scaleIntensityTarget, 0.01F);
    	
    	//temp off
    	//distToStorm = distToStormThreshold;
    	
    	//distToStorm = 0;
    	
    	/**
    	 * new way to detect in sandstorm
    	 * 1. use in convex shape method
    	 * 2. if not, get closest point of shape to player, use that for distance
    	 * -- note, add extra field_75884_a to compare against so its hard to enter between field_75884_a and have it think player is 50 blocks away still
    	 * 
    	 * - better idea, do 1., then if not, do point vs "minimum distance from a point to a line segment."
    	 */
    	
    	
    	
    	//square shape test
    	/*field_75884_a.add(new Vec3d(-100, 0, -100));
    	field_75884_a.add(new Vec3d(-100, 0, 100));
    	field_75884_a.add(new Vec3d(100, 0, 100));
    	field_75884_a.add(new Vec3d(100, 0, -100));*/
    	
    	//triangle test
    	/*field_75884_a.add(new Vec3d(-100, 0, -100));
    	field_75884_a.add(new Vec3d(-100, 0, 100));
    	field_75884_a.add(new Vec3d(100, 0, 0));*/
    	//field_75884_a.add(new Vec3d(100, 0, -100));
    	
    	//tested works well
    	
    	
    	
    	float fogColorChangeRate = 0.01F;
    	float fogDistChangeRate = 2F;
    	float fogDensityChangeRate = 0.01F;
    	
    	//make it be full intensity once storm is halfway there
    	adjustAmountTarget = 1F - (float) ((distToStorm) / distToStormThreshold);
    	adjustAmountTarget *= 2F * scaleIntensitySmooth * (isPlayerOutside ? 1F : 0.5F);

		//use override if needed
		boolean pocketSandOverride = false;
		if (adjustAmountTarget < adjustAmountTargetPocketSandOverride) {
			adjustAmountTarget = adjustAmountTargetPocketSandOverride;
			pocketSandOverride = true;
		}
    	
    	if (adjustAmountTarget < 0F) adjustAmountTarget = 0F;
    	if (adjustAmountTarget > 1F) adjustAmountTarget = 1F;

        //test debug sandstorm fog
        //adjustAmountTarget = 0.95F;
        //adjustAmountTarget = 0F;


        float sunBrightness = client.world.getSunBrightness(1F) * 1F;
        /*client.world.rainingStrength = 1F;
        client.world.thunderingStrength = 1F;*/

    	//since size var adjusts by 10 every x seconds, transition is rough, try to make it smooth but keeps up
		if (!pocketSandOverride) {
			if (adjustAmountSmooth < adjustAmountTarget) {
				adjustAmountSmooth = CoroUtilMisc.adjVal(adjustAmountSmooth, adjustAmountTarget, 0.003F);
			} else {
				adjustAmountSmooth = CoroUtilMisc.adjVal(adjustAmountSmooth, adjustAmountTarget, 0.002F);
			}
		} else {
			adjustAmountSmooth = CoroUtilMisc.adjVal(adjustAmountSmooth, adjustAmountTarget, 0.02F);
		}

		//testing
		//adjustAmountSmooth = 1F;

    	//tick coroutil particle renderer fog state
        EventHandler.sandstormFogAmount = adjustAmountSmooth;

    	if (client.world.getGameTime() % 20 == 0) {
    		//System.out.println(adjustAmount + " - " + distToStorm);
            if (adjustAmountSmooth > 0) {
                //System.out.println("adjustAmountTarget: " + adjustAmountTarget);
                //System.out.println("adjustAmountSmooth: " + adjustAmountSmooth);
            }

            //System.out.println("wut: " + client.world.getCelestialAngle(1));
            //System.out.println("wutF: " + client.world.getSunBrightnessFactor(1F));
            //System.out.println("wut: " + client.world.getSunBrightness(1F));
    	}
    	
    	if (adjustAmountSmooth > 0/*distToStorm < distToStormThreshold*/) {

            //TODO: remove fetching of colors from this now that we dynamically track that
    		if (needFogState) {
    			//System.out.println("getting fog state");
    			
    			try {
    				Object fogState = ObfuscationReflectionHelper.getPrivateValue(GlStateManager.class, null, "FOG");
    				Class<?> innerClass = Class.forName("com.mojang.blaze3d.platform.GlStateManager$FogState");
    				Field fieldDensity = null;
    				Field fieldStart = null;
    				Field fieldEnd = null;
    				try {
    					fieldDensity = innerClass.getField("field_179048_c");
    					fieldDensity.setAccessible(true);
    					fieldStart = innerClass.getField("field_179045_d");
    					fieldStart.setAccessible(true);
    					fieldEnd = innerClass.getField("field_179046_e");
    					fieldEnd.setAccessible(true);
					} catch (Exception e) {
						//dev env mode
						fieldDensity = innerClass.getField("density");
						fieldDensity.setAccessible(true);
						fieldStart = innerClass.getField("start");
    					fieldStart.setAccessible(true);
    					fieldEnd = innerClass.getField("end");
    					fieldEnd.setAccessible(true);
					}
    				stormFogDensity = fieldDensity.getFloat(fogState);
    				
    				stormFogStart = fieldStart.getFloat(fogState);
    				stormFogEnd = fieldEnd.getFloat(fogState);
    				
    				stormFogStartClouds = 0;
    				stormFogEndClouds = 192;
    				
    				
    				stormFogStartOrig = stormFogStart;
    				stormFogEndOrig = stormFogEnd;
    				stormFogStartCloudsOrig = stormFogStartClouds;
    				stormFogEndCloudsOrig = stormFogEndClouds;
    				
    				stormFogDensityOrig = stormFogDensity;
    				
				} catch (Exception e) {
					e.printStackTrace();
				}
    			needFogState = false;
    		}
    		
    		//new dynamic adjusting
    		stormFogRed = stormFogRedOrig + (-(stormFogRedOrig - (0.7F * sunBrightness)) * adjustAmountSmooth);
    		stormFogGreen = stormFogGreenOrig + (-(stormFogGreenOrig - (0.5F * sunBrightness)) * adjustAmountSmooth);
    		stormFogBlue = stormFogBlueOrig + (-(stormFogBlueOrig - (0.25F * sunBrightness)) * adjustAmountSmooth);
    		
    		stormFogDensity = stormFogDensityOrig + (-(stormFogDensityOrig - 0.02F) * adjustAmountSmooth);

    		float targetStart = 0F;
			float targetEnd = 0F;

			boolean ltOverrideAdjustment = true;

			if (ltOverrideAdjustment) {
				targetEnd = 150;

				//stop flicker, screw it use static sun brightness
				sunBrightness = 0.7F;

				stormFogRed = stormFogRedOrig + (-(stormFogRedOrig - (0.7F * sunBrightness)) * adjustAmountSmooth);
				stormFogGreen = stormFogGreenOrig + (-(stormFogGreenOrig - (0.3F * sunBrightness)) * adjustAmountSmooth);
				stormFogBlue = stormFogBlueOrig + (-(stormFogBlueOrig - (0.15F * sunBrightness)) * adjustAmountSmooth);
				//stormFogDensity = 0.9F;
			}

    		stormFogStart = stormFogStartOrig + (-(stormFogStartOrig - targetStart) * adjustAmountSmooth);
    		stormFogEnd = stormFogEndOrig + (-(stormFogEndOrig - targetEnd) * adjustAmountSmooth);
    		stormFogStartClouds = stormFogStartCloudsOrig + (-(stormFogStartCloudsOrig - 0F) * adjustAmountSmooth);
    		stormFogEndClouds = stormFogEndCloudsOrig + (-(stormFogEndCloudsOrig - 20F) * adjustAmountSmooth);
    	} else {
    		if (!needFogState) {
    			//System.out.println("resetting need for fog state");
    		}
    		needFogState = true;
    	}

    	//enhance the scene further with particles around player, check for sandstorm to account for pocket sand modifying adjustAmountTarget
        if (adjustAmountSmooth > 0.75F && sandstorm != null) {

            Vec3d windForce = windMan.getWindForce();

            Random rand = client.world.rand;
            int spawnAreaSize = 80;

			double sandstormParticleRateDebris = ConfigParticle.Sandstorm_Particle_Debris_effect_rate;
			double sandstormParticleRateDust = ConfigParticle.Sandstorm_Particle_Dust_effect_rate;

            float adjustAmountSmooth75 = (adjustAmountSmooth * 8F) - 7F;

			//extra dust
            for (int i = 0; i < ((float)30 * adjustAmountSmooth75 * sandstormParticleRateDust)/*adjustAmountSmooth * 20F * ConfigMisc.Particle_Precipitation_effect_rate*/; i++) {

                BlockPos pos = new BlockPos(
                        player.getPosX() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2),
                        player.getPosY() - 2 + rand.nextInt(10),
                        player.getPosZ() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2));



                if (canPrecipitateAt(world, pos)) {
                    TextureAtlasSprite sprite = ParticleRegistry.cloud256;

                    ParticleSandstorm part = new ParticleSandstorm(world, pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            0, 0, 0, sprite);
                    particleBehavior.initParticle(part);

                    part.setMotionX(windForce.x);
                    part.setMotionZ(windForce.z);

                    part.setFacePlayer(false);
                    part.isTransparent = true;
                    part.rotationYaw = (float)rand.nextInt(360);
                    part.rotationPitch = (float)rand.nextInt(360);
                    part.setMaxAge(40);
                    part.setGravity(0.09F);
                    part.setAlphaF(0F);
                    float brightnessMulti = 1F - (rand.nextFloat() * 0.5F);
                    part.setColor(0.65F * brightnessMulti, 0.6F * brightnessMulti, 0.3F * brightnessMulti);
                    part.setScale(40);
                    part.aboveGroundHeight = 0.2D;

                    part.setKillOnCollide(true);

                    part.windWeight = 1F;

                    particleBehavior.particles.add(part);
                    ClientTickHandler.weatherManager.addWeatheredParticle(part);
                    part.spawnAsWeatherEffect();


                }
            }

            //tumbleweed
            for (int i = 0; i < ((float)1 * adjustAmountSmooth75 * sandstormParticleRateDebris)/*adjustAmountSmooth * 20F * ConfigMisc.Particle_Precipitation_effect_rate*/; i++) {

                BlockPos pos = new BlockPos(
                        player.getPosX() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2),
                        player.getPosY() - 2 + rand.nextInt(10),
                        player.getPosZ() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2));



                if (canPrecipitateAt(world, pos)) {
                    TextureAtlasSprite sprite = ParticleRegistry.tumbleweed;

                    ParticleSandstorm part = new ParticleSandstorm(world, pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            0, 0, 0, sprite);
                    particleBehavior.initParticle(part);

                    part.setMotionX(windForce.x);
                    part.setMotionZ(windForce.z);

                    part.setFacePlayer(true);
                    //part.spinFast = true;
                    part.isTransparent = true;
                    part.rotationYaw = (float)rand.nextInt(360);
                    part.rotationPitch = (float)rand.nextInt(360);
                    part.setMaxAge(80);
                    part.setGravity(0.3F);
                    part.setAlphaF(0F);
                    float brightnessMulti = 1F - (rand.nextFloat() * 0.2F);
                    //part.setColor(0.65F * brightnessMulti, 0.6F * brightnessMulti, 0.3F * brightnessMulti);
                    part.setColor(1F * brightnessMulti, 1F * brightnessMulti, 1F * brightnessMulti);
                    part.setScale(8);
                    part.aboveGroundHeight = 0.5D;
                    part.collisionSpeedDampen = false;
                    part.bounceSpeed = 0.03D;
                    part.bounceSpeedAhead = 0.03D;

                    part.setKillOnCollide(false);

                    part.windWeight = 1F;

                    particleBehavior.particles.add(part);
                    ClientTickHandler.weatherManager.addWeatheredParticle(part);
                    part.spawnAsWeatherEffect();


                }
            }

            //debris
            for (int i = 0; i < ((float)8 * adjustAmountSmooth75 * sandstormParticleRateDebris)/*adjustAmountSmooth * 20F * ConfigMisc.Particle_Precipitation_effect_rate*/; i++) {
                BlockPos pos = new BlockPos(
                        player.getPosX() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2),
                        player.getPosY() - 2 + rand.nextInt(10),
                        player.getPosZ() + rand.nextInt(spawnAreaSize) - (spawnAreaSize / 2));



                if (canPrecipitateAt(world, pos)) {
                    TextureAtlasSprite sprite = null;
                    int tex = rand.nextInt(3);
                    if (tex == 0) {
                        sprite = ParticleRegistry.debris_1;
                    } else if (tex == 1) {
                        sprite = ParticleRegistry.debris_2;
                    } else if (tex == 2) {
                        sprite = ParticleRegistry.debris_3;
                    }

                    ParticleSandstorm part = new ParticleSandstorm(world, pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            0, 0, 0, sprite);
                    particleBehavior.initParticle(part);

                    part.setMotionX(windForce.x);
                    part.setMotionZ(windForce.z);

                    part.setFacePlayer(false);
                    part.spinFast = true;
                    part.isTransparent = true;
                    part.rotationYaw = (float)rand.nextInt(360);
                    part.rotationPitch = (float)rand.nextInt(360);

                    part.setMaxAge(80);
                    part.setGravity(0.3F);
                    part.setAlphaF(0F);
                    float brightnessMulti = 1F - (rand.nextFloat() * 0.5F);
                    //part.setColor(0.65F * brightnessMulti, 0.6F * brightnessMulti, 0.3F * brightnessMulti);
                    part.setColor(1F * brightnessMulti, 1F * brightnessMulti, 1F * brightnessMulti);
                    part.setScale(8);
                    part.aboveGroundHeight = 0.5D;
                    part.collisionSpeedDampen = false;
                    part.bounceSpeed = 0.03D;
                    part.bounceSpeedAhead = 0.03D;

                    part.setKillOnCollide(false);

                    part.windWeight = 1F;

                    particleBehavior.particles.add(part);
                    ClientTickHandler.weatherManager.addWeatheredParticle(part);
                    part.spawnAsWeatherEffect();


                }
            }
        }



		tickSandstormSound();
    }

	/**
	 *
	 */
	public static void tickSandstormSound() {
		/**
		 * dist + storm intensity
		 * 0F - 1F
		 *
		 * 0 = low
		 * 0.33 = med
		 * 0.66 = high
		 *
		 * static sound volume, keep at player
		 */

		Minecraft client = Minecraft.getInstance();
		if (adjustAmountSmooth > 0) {
			if (adjustAmountSmooth < 0.33F) {
				tryPlayPlayerLockedSound(WeatherUtilSound.snd_sandstorm_low, 5, client.player, 1F);
			} else if (adjustAmountSmooth < 0.66F) {
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
    
    public static boolean isFogOverridding() {
		Minecraft client = Minecraft.getInstance();
		BlockState iblockstate = client.gameRenderer.getActiveRenderInfo().getBlockAtCamera();
		if (iblockstate.getMaterial().isLiquid()) return false;
    	return adjustAmountSmooth > 0;
    }
    
    public static void renderWorldLast(RenderWorldLastEvent event) {
    	
    }

    public static void renderTick(TickEvent.RenderTickEvent event) {

		if (ConfigMisc.Client_PotatoPC_Mode) return;

		if (!ConfigLTOverrides.vanillaRainOverride) {
			Minecraft client = Minecraft.getInstance();
			if (client.world != null && ClientTickHandler.minigameWeatherInstance != null && ClientTickHandler.minigameWeatherInstance.isMinigameActive()) {
				ClientTickHandler.checkClientWeather();
				client.world.setRainStrength(ClientTickHandler.minigameWeatherInstance.getVanillaRainRenderAmount());
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
