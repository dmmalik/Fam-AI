package ai.fam.app

import android.app.Application
import ai.fam.app.data.ModelDownload

class FamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ModelDownload.watch(this)
    }
}
