package weather2;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import weather2.util.WeatherUtilSound;
@Mod.EventBusSubscriber(Dist.CLIENT)
public class ClientProxy extends CommonProxy
{
	public static ClientTickHandler clientTickHandler;
	
    public ClientProxy()
    {
        clientTickHandler = new ClientTickHandler();
    }

    @Override
    public void init()
    {
    	super.init();
    	
    	WeatherUtilSound.init();
    }
}
