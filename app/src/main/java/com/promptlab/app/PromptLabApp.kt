package com.promptlab.app

import android.app.Application
import com.promptlab.app.data.PromptLabDb

class PromptLabApp : Application() {
    val db: PromptLabDb by lazy { PromptLabDb.get(this) }
}
