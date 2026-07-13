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



    fun reload() {
        try {
            registerMainAPI(MyLiveTVProvider())
           
        } catch (e: Exception) {
        }
    }
}