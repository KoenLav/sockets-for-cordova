package cz.blocshop.socketsforcordova

import android.os.Bundle
import org.apache.cordova.CordovaActivity

class SocketsForCordovaActivity : CordovaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.init()
        // Set by <content src="index.html" /> in config.xml
        super.loadUrl(Config.getStartUrl())
        //super.loadUrl("file:///android_asset/www/index.html")
    }
}
