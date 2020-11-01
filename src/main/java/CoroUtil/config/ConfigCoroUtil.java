package CoroUtil.config;

import modconfig.ConfigComment;
import modconfig.IConfigCategory;

import java.io.File;

public class ConfigCoroUtil implements IConfigCategory {

	@ConfigComment("Used by weather2")
	public static boolean foliageShaders = false;

	@ConfigComment("Used by weather2")
	public static boolean particleShaders = false;

	@ConfigComment("For seldom used but important things to print out in production")
	public static boolean useLoggingLog = true;

	@ConfigComment("For debugging things")
	public static boolean useLoggingDebug = false;

	@ConfigComment("For logging warnings/errors")
	public static boolean useLoggingError = true;

	@Override
	public String getName() {
		return "General";
	}

	@Override
	public String getRegistryName() {
		return "coroutil_general";
	}

	@Override
	public String getConfigFileName() {
		return "CoroUtil" + File.separator + getName();
	}

	@Override
	public String getCategory() {
		return getName();
	}

	@Override
	public void hookUpdatedValues() {
	}

}
