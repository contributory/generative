package org.starfall.generative.shared.storage.preferences

import kotlinx.serialization.Serializable

/** Persisted together as one user-owned appearance profile. Image references are app-private. */
@Serializable
data class UserProfile(
    val photo: String? = null,
    val chatBackground: String? = null,
    val drawerBackground: String? = null,
    val menuBackground: String? = null,
    val sheetBackground: String? = null,
    val dialogBackground: String? = null
)

enum class UserProfileImageSlot {
    PHOTO, CHAT, DRAWER, MENU;

    fun get(profile: UserProfile): String? = when (this) {
        PHOTO -> profile.photo
        CHAT -> profile.chatBackground
        DRAWER -> profile.drawerBackground
        MENU -> profile.menuBackground
    }

    fun put(profile: UserProfile, path: String?): UserProfile = when (this) {
        PHOTO -> profile.copy(photo = path)
        CHAT -> profile.copy(chatBackground = path)
        DRAWER -> profile.copy(drawerBackground = path)
        MENU -> profile.copy(menuBackground = path)
    }
}
