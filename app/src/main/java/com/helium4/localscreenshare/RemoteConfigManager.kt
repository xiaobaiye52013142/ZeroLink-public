package com.helium4.localscreenshare

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

object RemoteConfigManager {

    private val config = FirebaseRemoteConfig.getInstance()

    fun init() {
        config.setDefaultsAsync(
            mapOf(
                "force_update_version" to 0L,
                "soft_update_version" to 0L,
                "update_message" to "",
                "update_url" to "",
                "changelog" to "",
                "multi_receiver_premium" to false
            )
        )
        config.fetch(3600).addOnCompleteListener { config.activate() }
    }

    fun forceUpdateVersion(): Int = config.getLong("force_update_version").toInt()
    fun softUpdateVersion(): Int = config.getLong("soft_update_version").toInt()
    fun updateMessage(): String = config.getString("update_message")
    fun updateUrl(): String = config.getString("update_url")
    fun changelog(): String = config.getString("changelog")
    fun isMultiReceiverUnlocked(): Boolean = config.getBoolean("multi_receiver_premium")
}
