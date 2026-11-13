package ai.fam.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import ai.fam.app.data.ModelDownload
import ai.fam.app.ui.FamNav
import ai.fam.app.ui.FamTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamTheme {
                FamNav()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ModelDownload.foreground(this, true)
    }

    override fun onStop() {
        ModelDownload.foreground(this, false)
        super.onStop()
    }
}
