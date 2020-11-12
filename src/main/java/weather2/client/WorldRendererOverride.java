package weather2.client;

import com.lovetropics.minigames.common.minigames.weather.RainType;
import extendedrenderer.ParticleRegistry2ElectricBubbleoo;
import extendedrenderer.particle.ParticleRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.RenderTypeBuffers;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.settings.ParticleStatus;
import net.minecraft.fluid.IFluidState;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.Heightmap;
import weather2.ClientWeather;

import java.util.Random;

public class WorldRendererOverride extends WorldRenderer {


    public WorldRendererOverride(Minecraft p_i225967_1_, RenderTypeBuffers p_i225967_2_) {
        super(p_i225967_1_, p_i225967_2_);
    }

    @Override
    public void addRainParticles(ActiveRenderInfo activeRenderInfoIn) {
        Minecraft mc = Minecraft.getInstance();
        ClientWeather weather = ClientWeather.get();
        float f = mc.world.getRainStrength(1.0F);
        if (!mc.gameSettings.fancyGraphics) {
            f /= 2.0F;
        }

        if (f != 0.0F) {
            Random random = new Random((long)this.ticks * 312987231L);
            IWorldReader iworldreader = mc.world;
            BlockPos blockpos = new BlockPos(activeRenderInfoIn.getProjectedView());
            int i = 10;
            double d0 = 0.0D;
            double d1 = 0.0D;
            double d2 = 0.0D;
            int j = 0;
            int k = (int)(100.0F * f * f);
            if (mc.gameSettings.particles == ParticleStatus.DECREASED) {
                k >>= 1;
            } else if (mc.gameSettings.particles == ParticleStatus.MINIMAL) {
                k = 0;
            }

            for(int l = 0; l < k; ++l) {
                BlockPos blockpos1 = iworldreader.getHeight(Heightmap.Type.MOTION_BLOCKING, blockpos.add(random.nextInt(10) - random.nextInt(10), 0, random.nextInt(10) - random.nextInt(10)));
                Biome biome = iworldreader.getBiome(blockpos1);
                BlockPos blockpos2 = blockpos1.down();
                if (blockpos1.getY() <= blockpos.getY() + 10 && blockpos1.getY() >= blockpos.getY() - 10 && biome.getPrecipitation() == Biome.RainType.RAIN && biome.getTemperature(blockpos1) >= 0.15F) {
                    double d3 = random.nextDouble();
                    double d4 = random.nextDouble();
                    BlockState blockstate = iworldreader.getBlockState(blockpos2);
                    IFluidState ifluidstate = iworldreader.getFluidState(blockpos1);
                    VoxelShape voxelshape = blockstate.getCollisionShape(iworldreader, blockpos2);
                    double d7 = voxelshape.max(Direction.Axis.Y, d3, d4);
                    double d8 = (double)ifluidstate.getActualHeight(iworldreader, blockpos1);
                    double d5;
                    double d6;
                    if (d7 >= d8) {
                        d5 = d7;
                        d6 = voxelshape.min(Direction.Axis.Y, d3, d4);
                    } else {
                        d5 = 0.0D;
                        d6 = 0.0D;
                    }

                    if (d5 > -Double.MAX_VALUE) {
                        if (!ifluidstate.isTagged(FluidTags.LAVA) && blockstate.getBlock() != Blocks.MAGMA_BLOCK && (blockstate.getBlock() != Blocks.CAMPFIRE || !blockstate.get(CampfireBlock.LIT))) {
                            ++j;
                            if (random.nextInt(j) == 0) {
                                d0 = (double)blockpos2.getX() + d3;
                                d1 = (double)((float)blockpos2.getY() + 0.1F) + d5 - 1.0D;
                                d2 = (double)blockpos2.getZ() + d4;
                            }

                            if (weather.getRainType() == RainType.ACID) {
                                mc.world.addParticle(ParticleRegistry2ElectricBubbleoo.ACIDRAIN_SPLASH, (double) blockpos2.getX() + d3, (double) ((float) blockpos2.getY() + 0.1F) + d5, (double) blockpos2.getZ() + d4, 0.0D, 0.0D, 0.0D);
                            } else {
                                mc.world.addParticle(ParticleTypes.RAIN, (double) blockpos2.getX() + d3, (double) ((float) blockpos2.getY() + 0.1F) + d5, (double) blockpos2.getZ() + d4, 0.0D, 0.0D, 0.0D);
                            }
                        } else {
                            mc.world.addParticle(ParticleTypes.SMOKE, (double)blockpos1.getX() + d3, (double)((float)blockpos1.getY() + 0.1F) - d6, (double)blockpos1.getZ() + d4, 0.0D, 0.0D, 0.0D);
                        }
                    }
                }
            }

            if (j > 0 && random.nextInt(3) < this.rainSoundTime++) {
                this.rainSoundTime = 0;
                if (d1 > (double)(blockpos.getY() + 1) && iworldreader.getHeight(Heightmap.Type.MOTION_BLOCKING, blockpos).getY() > MathHelper.floor((float)blockpos.getY())) {
                    mc.world.playSound(d0, d1, d2, SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.WEATHER, 0.1F, 0.5F, false);
                } else {
                    mc.world.playSound(d0, d1, d2, SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER, 0.2F, 1.0F, false);
                }
            }

        }
    }
}
