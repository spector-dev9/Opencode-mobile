package io.github.spectordev9.opencode

import android.content.Context
import io.github.spectordev9.opencode.core.workspace.WorkspaceService
import io.github.spectordev9.opencode.data.AppPreferences
import io.github.spectordev9.opencode.data.DemoChatRepository
import io.github.spectordev9.opencode.data.DemoWorkspaceService
import io.github.spectordev9.opencode.data.UnavailableChatRepository
import io.github.spectordev9.opencode.data.UnavailableWorkspaceService
import io.github.spectordev9.opencode.ui.port.ChatRepository

/**
 * The single composition root for the UI. Feature screens never reach a
 * runtime, transport, or filesystem directly.
 *
 * Debug builds use clearly labelled demo data so screens can be developed
 * before G0-dependent wiring. Release builds must receive real services.
 */
class AppDependencies(
    val chat: ChatRepository,
    val workspace: WorkspaceService,
    val preferences: AppPreferences,
    val isDemo: Boolean,
) {
    companion object {
        fun forBuild(context: Context): AppDependencies {
            val preferences = AppPreferences(context)
            return if (BuildConfig.DEBUG) {
                AppDependencies(
                    chat = DemoChatRepository(),
                    workspace = DemoWorkspaceService(),
                    preferences = preferences,
                    isDemo = true,
                )
            } else {
                AppDependencies(
                    chat = UnavailableChatRepository(),
                    workspace = UnavailableWorkspaceService(),
                    preferences = preferences,
                    isDemo = false,
                )
            }
        }
    }
}
