// YaSettings.kt
package com.github.sammyvimes.yamakeplugin

import com.intellij.openapi.components.*

@State(
    name = "YaSettings",
    storages = [Storage("YaMakePluginSettings.xml")]
)
@Service(Service.Level.APP)
class YaSettings : PersistentStateComponent<YaSettings.State> {
    data class State(
        var yaPath: String = "ya",
        var currentYaMake: String = "",
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): YaSettings = service()
    }
}
