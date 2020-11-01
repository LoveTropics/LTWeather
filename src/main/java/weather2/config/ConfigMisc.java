package weather2.config;

import modconfig.ConfigComment;
import modconfig.IConfigCategory;
import weather2.Weather;
import weather2.util.WeatherUtilConfig;

import java.io.File;

public class ConfigMisc implements IConfigCategory {
	
	public static boolean Misc_ForceVanillaCloudsOff = false;
	public static boolean consoleDebug = false;

	//Weather
	@ConfigComment("If true, lets server side do vanilla weather rules, weather2 will only make storms when server side says 'rain' is on")
	public static boolean overcastMode = true;
	@ConfigComment("Used if overcastMode is off, 1 = lock weather on, 0 = lock weather off, -1 = dont lock anything, let server do whatever")
	public static int lockServerWeatherMode = 0; //is only used if overcastMode is off
	//cloudOption
	@ConfigComment("How many ticks between cloud particle spawning")
	public static int Cloud_ParticleSpawnDelay = 2;

	@ConfigComment("How much to randomly change cloud coverage % amount, performed every 10 seconds")
	public static double Cloud_Coverage_Random_Change_Amount = 0.05D;

	@ConfigComment("Minimum percent of cloud coverage, supports negative for extended cloudless sky coverage")
	public static double Cloud_Coverage_Min_Percent = 0D;

	@ConfigComment("Maximum percent of cloud coverage, supports over 100% for extended full cloud sky coverage")
	public static double Cloud_Coverage_Max_Percent = 100D;
	
	public static int Thread_Particle_Process_Delay = 400;
	//sound
	public static double volWaterfallScale = 0.5D;
	public static double volWindTreesScale = 0.5D;

	//dimension settings
	public static String Dimension_List_Weather = "0,-127";
	public static String Dimension_List_Clouds = "0,-127";
	public static String Dimension_List_Storms = "0,-127";
	public static String Dimension_List_WindEffects = "0,-127";

	@ConfigComment("Use if you are on a server with weather but want it ALL off client side for performance reasons, overrides basically every client based setting")
	public static boolean Client_PotatoPC_Mode = false;

	@ConfigComment("Server and client side, Locks down the mod to only do wind, leaves, foliage shader if on, etc. No weather systems, turns overcast mode on")
	public static boolean Aesthetic_Only_Mode = true;

	public ConfigMisc() {
		
	}

	@Override
	public String getName() {
		return "Misc";
	}

	@Override
	public String getRegistryName() {
		return Weather.MODID + getName();
	}

	@Override
	public String getConfigFileName() {
		return "Weather2" + File.separator + getName();
	}

	@Override
	public String getCategory() {
		return "Weather2: " + getName();
	}

	@Override
	public void hookUpdatedValues() {
		//Weather.dbg("block list processing disabled");
		//TODO: 1.14 uncomment
		//WeatherUtil.doBlockList();
		WeatherUtilConfig.processLists();
	}

}
