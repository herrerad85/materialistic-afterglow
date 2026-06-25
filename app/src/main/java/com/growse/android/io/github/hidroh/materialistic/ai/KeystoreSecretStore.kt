/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [SecretStore] backed by Google Tink AEAD with an Android Keystore master key (E7-D3). The
 * AEAD keyset itself lives in a dedicated SharedPreferences file, encrypted ("wrapped") by a
 * non-extractable Keystore master key, so the keyset is never usable off-device. The user's API key
 * is encrypted with that AEAD and only the base64 ciphertext is persisted in a second prefs file.
 *
 * What is and is not written to prefs: the wrapped keyset (safe, encrypted under the Keystore key)
 * and the AEAD ciphertext of the API key (safe, AEAD-encrypted) ARE written. The PLAINTEXT API key
 * is NEVER written to SharedPreferences and is NEVER logged - breadcrumbs carry no value, only the
 * fact that an operation happened or failed.
 *
 * No JVM/Robolectric unit test covers this class: the Android Keystore and Tink's Android
 * integration are unavailable under Robolectric, so the real encrypt/decrypt round-trip is
 * device-QA'd by the parent (E7-D8). [FakeSecretStore] covers consumer tests.
 */
@Singleton
class KeystoreSecretStore @Inject constructor(@ApplicationContext private val context: Context) :
    SecretStore {

  // Lazily built so construction (and Hilt graph creation) never touches the Keystore on the main
  // thread before the store is actually used.
  private val aead: Aead by lazy { buildAead() }

  override fun putApiKey(value: String) {
    val ciphertext = aead.encrypt(value.toByteArray(Charsets.UTF_8), EMPTY_ASSOCIATED_DATA)
    val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    storePrefs().edit().putString(PREF_CIPHERTEXT, encoded).apply()
  }

  override fun getApiKey(): String? {
    val encoded = storePrefs().getString(PREF_CIPHERTEXT, null)
    if (encoded.isNullOrEmpty()) {
      return null
    }
    return try {
      val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
      val plaintext = aead.decrypt(ciphertext, EMPTY_ASSOCIATED_DATA)
      String(plaintext, Charsets.UTF_8)
    } catch (e: Exception) {
      // Breadcrumb only: never log the ciphertext or any decrypted bytes.
      Log.w(TAG, "Stored API key could not be decrypted; treating as absent", e)
      null
    }
  }

  // Presence alone is not enough: a restored/corrupted ciphertext (e.g. from a different keyset)
  // is unusable, so report only a key that actually decrypts. getApiKey() discards the plaintext
  // here and never logs it.
  override fun hasApiKey(): Boolean = getApiKey() != null

  override fun clearApiKey() {
    storePrefs().edit().remove(PREF_CIPHERTEXT).apply()
  }

  private fun storePrefs() = context.getSharedPreferences(STORE_PREFS, Context.MODE_PRIVATE)

  private fun buildAead(): Aead {
    AeadConfig.register()
    val keysetHandle =
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_PREF_KEY, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
    return keysetHandle.getPrimitive(Aead::class.java)
  }

  private companion object {
    const val TAG = "KeystoreSecretStore"

    // Keyset (the AEAD key material, wrapped by the Keystore master key) lives in its own prefs
    // file.
    const val KEYSET_PREFS = "ai_secret_keyset"
    const val KEYSET_PREF_KEY = "aead_keyset"
    const val MASTER_KEY_URI = "android-keystore://afterglow_ai_secret_master"

    // The AEAD ciphertext of the user's API key lives in a separate prefs file.
    const val STORE_PREFS = "ai_secret_store"
    const val PREF_CIPHERTEXT = "api_key_ciphertext"

    val EMPTY_ASSOCIATED_DATA = ByteArray(0)
  }
}
