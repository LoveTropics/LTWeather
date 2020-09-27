package weather2;

import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lovetropics.minigames.common.config.ConfigLT;
import com.lovetropics.minigames.common.minigames.IMinigameDefinition;
import com.lovetropics.minigames.common.minigames.IMinigameInstance;
import com.lovetropics.minigames.common.minigames.weather.IMinigameWeatherInstance;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public class MinigameWeatherInstance implements IMinigameWeatherInstance {
	
	private static final Logger LOGGER = LogManager.getLogger();

    /**
     *
     *
     * instantiate in IslandRoyaleMinigameDefinition
     * - packet sync what is needed
     * - setup instanced overrides on client
     *
     *
     * phases:
     * - 1: semi peacefull, maybe light rain/wind
     * - 2: heavy wind, acid rain
     * - 3: see doc, "an extreme storm encroaches the map slowly towards the centre"
     * --- assuming can also do same things phase 2 does?
     *
     * phases should be in IslandRoyaleMinigameDefinition for use in other places, and this class listens to them
     *
     * rng that can happen:
     * - wind, can operate independently of other rng events
     *
     * rng that only allows 1 of them at a time:
     * - extreme rain
     * - acid rain
     * - heat wave
     *
     * heat wave:
     * - player movement reduced if player pos can see sky
     *
     * rain:
     * - the usual
     *
     * acid rain:
     * - player damage over time
     * - degrade items and armor over time
     * - use normal rain visual too, color changed
     *
     * extreme rain:
     * - fog closes in
     * - pump up weather2 effects
     * - splashing noise while walking
     * - use normal rain visual too
     *
     * - consider design to factor in worn items to negate player effects
     */

    //only one of these can be active at a time
    protected long heavyRainfallTime = 0;
    protected long acidRainTime = 0;
    protected long heatwaveTime = 0;

    protected float windSpeed = 0;

    //save last state the rain was set to, so that on the client side when rain is fading out, it doesnt switch to blue rain because acidRainTime == 0
    protected boolean lastRainWasAcid = false;

    protected boolean minigameActive = false;

    protected double heatwaveMovementMultiplierClient = 0.75D;

    //operates independently of other weather events
    //TODO: just modify WindManager to do this?
    protected long highWindTime = 0;

    protected Random random = new Random();

    public static boolean debug = false;

    public MinigameWeatherInstance() {

    }
    
    @Override
    public void onStart(IMinigameInstance minigame) {

    }

    @Override
	public void tick(IMinigameInstance minigameDefinition) {

    }

    @Override
	public void tickPlayer(PlayerEntity player) {

    }

    @Override
	public void reset() {
        heavyRainfallTime = 0;
        acidRainTime = 0;
        heatwaveTime = 0;
        windSpeed = 0;
        setMinigameActive(false);
    }

    @Override
	public boolean heavyRainfallActive() {
        return heavyRainfallTime > 0;
    }

    @Override
	public boolean acidRainActive() {
        return acidRainTime > 0;
    }

    @Override
	public boolean heatwaveActive() {
        return heatwaveTime > 0;
    }

    @Override
	public boolean specialWeatherActive() {
        return heavyRainfallActive() || acidRainActive() || heatwaveActive();
    }

    public void dbg(String str) {
        if (debug) {
            LOGGER.info(str);
        }
    }

    @Override
	public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();

        nbt.putLong("heavyRainfallTime", heavyRainfallTime);
        nbt.putLong("acidRainTime", acidRainTime);
        nbt.putLong("heatwaveTime", heatwaveTime);
        nbt.putBoolean("lastRainWasAcid", lastRainWasAcid);
        nbt.putDouble("heatwaveMovementMultiplierClient", ConfigLT.MINIGAME_SURVIVE_THE_TIDE.heatwaveMovementMultiplier.get());
        nbt.putBoolean("minigameActive", minigameActive);

        return nbt;
    }

    @Override
	public void deserializeNBT(CompoundNBT nbt) {
        heavyRainfallTime = nbt.getLong("heavyRainfallTime");
        acidRainTime = nbt.getLong("acidRainTime");
        heatwaveTime = nbt.getLong("heatwaveTime");
        lastRainWasAcid = nbt.getBoolean("lastRainWasAcid");
        heatwaveMovementMultiplierClient = nbt.getDouble("heatwaveMovementMultiplierClient");
        minigameActive = nbt.getBoolean("minigameActive");

        //dbg("minigame weather deserialize(): " + nbt);
    }

    @Override
	public boolean isLastRainWasAcid() {
        return lastRainWasAcid;
    }

    @Override
	public void setLastRainWasAcid(boolean lastRainWasAcid) {
        this.lastRainWasAcid = lastRainWasAcid;
    }

    @Override
	public boolean isMinigameActive() {
        return minigameActive;
    }

    @Override
	public void setMinigameActive(boolean minigameActive) {
        this.minigameActive = minigameActive;
    }

    @Override
	public float getWindSpeed() {
        return windSpeed;
    }
}
