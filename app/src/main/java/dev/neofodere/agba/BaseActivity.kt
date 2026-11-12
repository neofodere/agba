package dev.neofodere.agba

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import dev.neofodere.agba.data.AppLanguage
import dev.neofodere.agba.data.Settings
import java.util.Locale

open class BaseActivity : ComponentActivity() {
    private var appliedLanguage = AppLanguage.AUTO

    override fun attachBaseContext(base: Context) {
        appliedLanguage = Settings(base).language
        val tag = appliedLanguage.tag
        if (tag == null) {
            super.attachBaseContext(base)
            return
        }
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (app.settings.language != appliedLanguage) recreate()
    }
}
