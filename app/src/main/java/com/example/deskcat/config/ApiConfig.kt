package com.example.deskcat.config

import android.content.Context
import com.example.deskcat.R

object ApiConfig {
    fun removeBgApiKey(context: Context): String {
        return context.resources.getString(R.string.remove_bg_api_key).trim()
    }

    data class DoubaoImageConfig(
        val apiKey: String,
        val model: String,
        val apiHost: String,
        val imageInputField: String,
    ) {
        val generationUrl: String
            get() = "https://${apiHost.removePrefix("https://").removePrefix("http://")}/api/v3/images/generations"

        val isConfigured: Boolean
            get() = apiKey.isNotBlank() &&
                model.isNotBlank() &&
                apiHost.isNotBlank() &&
                imageInputField.isNotBlank()
    }

    fun doubaoImage(context: Context): DoubaoImageConfig {
        val resources = context.resources
        return DoubaoImageConfig(
            apiKey = resources.getString(R.string.doubao_api_key).trim(),
            model = resources.getString(R.string.doubao_image_model).trim(),
            apiHost = resources.getString(R.string.doubao_api_host).trim(),
            imageInputField = resources.getString(R.string.doubao_image_input_field).trim(),
        )
    }

}
