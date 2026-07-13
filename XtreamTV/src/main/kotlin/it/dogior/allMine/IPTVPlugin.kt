package it.dogior.allMine

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.*
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey

@CloudstreamPlugin
class IPTVPlugin : Plugin() {
    override fun load(context: Context) {
        reload()
    }

    init {
        this.openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            try {
                val frag = IPTVSettingsFragment(this)
                frag.show(activity.supportFragmentManager, "IPTV")
            } catch (e: Exception) {
            }
        }
    }

    fun reload() {
        try {
            registerMainAPI(MyLiveTVProvider())
            MainActivity.afterPluginsLoadedEvent.invoke(true)
        } catch (e: Exception) {
        }
    }
}