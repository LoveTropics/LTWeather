package weather2;

import com.lovetropics.minigames.common.config.ConfigLT;
import com.lovetropics.minigames.common.item.MinigameItems;
import com.lovetropics.minigames.common.minigames.IMinigameInstance;
import com.lovetropics.minigames.common.minigames.behaviours.MinigameBehaviorTypes;
import com.lovetropics.minigames.common.minigames.behaviours.instances.PhasesMinigameBehavior;
import com.lovetropics.minigames.common.minigames.behaviours.instances.survive_the_tide.SurviveTheTideRulesetBehavior;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.fml.network.PacketDistributor;
import weather2.util.WeatherUtil;

public class MinigameWeatherInstanceServer extends MinigameWeatherInstance {
    public MinigameWeatherInstanceServer() {
        super();
    }

    public void tick(IMinigameInstance minigame) {
        super.tick(minigame);
        World world = minigame.getWorld();
        PhasesMinigameBehavior phases = minigame.getDefinition().getBehavior(MinigameBehaviorTypes.PHASES.get()).orElse(null);
        SurviveTheTideRulesetBehavior rules = minigame.getDefinition().getBehavior(MinigameBehaviorTypes.SURVIVE_THE_TIDE_RULESET.get()).orElse(null);
        if (world != null && phases != null && rules != null) {
            PhasesMinigameBehavior.MinigamePhase phase = phases.getCurrentPhase();
            int tickRate = 20;
            if (world.getGameTime() % tickRate == 0) {

                if (!rules.isSafePhase(phase)) {
                    if (!specialWeatherActive()) {

                        //testing vals to maybe make configs
                        float rateAmp = 1F;
                        // TODO phase names
                        if (phase.is("phase2")) {
                            rateAmp = 1.5F;
                        } else if (phase.is("phase3")) {
                            rateAmp = 2.0F;
                        } else if (phase.is("phase4")) {
                            rateAmp = 2.5F;
                        }

                        // TODO configs
                        //favors first come first serve but if the rates are low enough its negligable probably
                        if (true) {//random.nextFloat() <= ConfigLT.MINIGAME_SURVIVE_THE_TIDE.rainHeavyChance.get() * rateAmp) {
                            heavyRainfallStart(phase);
                        } /*else if (random.nextFloat() <= ConfigLT.MINIGAME_SURVIVE_THE_TIDE.rainAcidChance.get() * rateAmp) {
                            if (phase != SurviveTheTideMinigameDefinition.MinigamePhase.PHASE4 || ConfigLT.MINIGAME_SURVIVE_THE_TIDE.minigame_SurviveTheTide_worlderBorder_acidRainOnPhase4.get()) {
                                acidRainStart(phase);
                            }
                        } else if (random.nextFloat() <= ConfigLT.MINIGAME_SURVIVE_THE_TIDE.heatwaveChance.get() * rateAmp) {
                            heatwaveStart(phase);
                        }*/
                    }
                }

                // TODO phase names
                if (rules.isSafePhase(phase)) {
                    windSpeed = 0.1F;
                } else if (phase.is("phase2")) {
                    windSpeed = 0.5F;
                } else if (phase.is("phase3")) {
                    windSpeed = 1.0F;
                } else if (phase.is("phase4")) {
                    windSpeed = 1.5F;
                }

                //dbg("" + minigameDefinition.getDimension() + minigameDefinition.getPhaseTime());

                tickSync(minigame);
            }

            //heavyRainfallStart(phase);
            //heavyRainfallTime = 0;
            //acidRainTime = 0;
            //acidRainStart(phase);
            //heatwaveStart(phase);
            //heatwaveTime = 0;

            if (heavyRainfallTime > 0) {
                heavyRainfallTime--;

                //if (heavyRainfallTime == 0) dbg("heavyRainfallTime ended");
            }

            if (acidRainTime > 0) {
                acidRainTime--;

                //if (acidRainTime == 0) dbg("acidRainTime ended");
            }

            if (heatwaveTime > 0) {
                heatwaveTime--;

                //if (heatwaveTime == 0) dbg("heatwaveTime ended");
            }

            //need to set for overworld due to vanilla bug
            World overworld = WeatherUtil.getWorld(0);
            if (overworld != null) {
                if (specialWeatherActive() && !heatwaveActive()) {
                    overworld.getWorldInfo().setRaining(true);
                    overworld.getWorldInfo().setThundering(true);
                } else {
                    overworld.getWorldInfo().setRaining(false);
                    overworld.getWorldInfo().setThundering(false);
                }
            }
        }

        world.getPlayers().forEach(player -> tickPlayer(player));
    }

    @Override
    public void tickPlayer(PlayerEntity player) {
        if (player.isCreative()) return;

        ItemStack offhand = player.getItemStackFromSlot(EquipmentSlotType.OFFHAND);

        if (acidRainActive()) {
            if (player.world.getHeight(Heightmap.Type.MOTION_BLOCKING, player.getPosition()).getY() <= player.getPosition().getY()) {
                if (player.world.getGameTime() % ConfigLT.MINIGAME_SURVIVE_THE_TIDE.acidRainDamageRate.get() == 0) {
                    Item umbrella = MinigameItems.ACID_REPELLENT_UMBRELLA.get();

                    if (offhand.getItem() != umbrella) {
                        player.attackEntityFrom(DamageSource.GENERIC, ConfigLT.MINIGAME_SURVIVE_THE_TIDE.acidRainDamage.get());
                    }
                }
            }
        } else if (heatwaveActive()) {
            if (player.world.getHeight(Heightmap.Type.MOTION_BLOCKING, player.getPosition()).getY() <= player.getPosition().getY()) {
                Item sunscreen = MinigameItems.SUPER_SUNSCREEN.get();

                if (offhand.getItem() != sunscreen) {
                    player.addPotionEffect(new EffectInstance(Effects.SLOWNESS, 5, 1, true, false, true));
                }
            }
        }
    }

    public void tickSync(IMinigameInstance minigame) {
        CompoundNBT data = new CompoundNBT();
        data.putString("packetCommand", WeatherNetworking.NBT_PACKET_COMMAND_MINIGAME);

        data.put(WeatherNetworking.NBT_PACKET_DATA_MINIGAME, serializeNBT());

        WeatherNetworking.HANDLER.send(PacketDistributor.DIMENSION.with(() -> minigame.getDimension()), new PacketNBTFromServer(data));
    }

    // TODO phase names
    public void heavyRainfallStart(PhasesMinigameBehavior.MinigamePhase phase) {
        heavyRainfallTime = ConfigLT.MINIGAME_SURVIVE_THE_TIDE.rainHeavyMinTime.get() + random.nextInt(ConfigLT.MINIGAME_SURVIVE_THE_TIDE.rainHeavyExtraRandTime.get());
        if (phase.is("phase4")) {
            heavyRainfallTime /= 2;
        }
        lastRainWasAcid = false;
        //dbg("heavyRainfallStart: " + heavyRainfallTime);
    }

    public void acidRainStart(PhasesMinigameBehavior.MinigamePhase phase) {
        acidRainTime = ConfigLT.MINIGAME_SURVIVE_THE_TIDE.rainAcidMinTime.get() + random.nextInt(ConfigLT.MINIGAME_SURVIVE_THE_TIDE.rainAcidExtraRandTime.get());
        if (phase.is("phase4")) {
            acidRainTime /= 2;
        }
        lastRainWasAcid = true;
        //dbg("acidRainStart: " + acidRainTime);
    }

    public void heatwaveStart(PhasesMinigameBehavior.MinigamePhase phase) {
        heatwaveTime = ConfigLT.MINIGAME_SURVIVE_THE_TIDE.heatwaveMinTime.get() + random.nextInt(ConfigLT.MINIGAME_SURVIVE_THE_TIDE.heatwaveExtraRandTime.get());
        if (phase.is("phase4")) {
            heatwaveTime /= 2;
        }
        //dbg("heatwaveStart: " + heatwaveTime);
    }

}
