package weather2;

import net.minecraft.nbt.CompoundNBT;
import weather2.config.ConfigMisc;

/**
 * Used for anything that needs to be used on both client and server side, to avoid config mismatch between dedicated server and clients
 */
public class ClientConfigData {

    public boolean overcastMode = false;
    public boolean Aesthetic_Only_Mode = false;

    /**
     * For client side
     *
     * @param nbt
     */
    public void read(CompoundNBT nbt) {
        overcastMode = nbt.getBoolean("overcastMode");
        Aesthetic_Only_Mode = nbt.getBoolean("Aesthetic_Only_Mode");
    }

    /**
     * For server side
     *
     * @param data
     */
    public static void write(CompoundNBT data) {

        data.putBoolean("overcastMode", ConfigMisc.overcastMode);
        data.putBoolean("Aesthetic_Only_Mode", ConfigMisc.Aesthetic_Only_Mode);


    }

}
