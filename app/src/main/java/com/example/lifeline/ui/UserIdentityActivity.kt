package com.example.lifeline.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lifeline.databinding.ActivityUserIdentityBinding

/**
 * First-launch identity setup screen.
 * Collects user's name and phone number for SOS messages.
 * Shown only once — navigates to MainActivity after setup.
 */
class UserIdentityActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "lifeline_prefs"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_PHONE = "user_phone"
        const val KEY_IDENTITY_SET = "identity_set"
    }

    private lateinit var binding: ActivityUserIdentityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip if identity already set
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IDENTITY_SET, false)) {
            navigateToMain()
            return
        }

        binding = ActivityUserIdentityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSave.setOnClickListener {
            saveIdentity()
        }
    }

    private fun saveIdentity() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        if (name.isEmpty()) {
            binding.etName.error = "Please enter your name"
            return
        }
        if (phone.isEmpty()) {
            binding.etPhone.error = "Please enter your phone number"
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_PHONE, phone)
            .putBoolean(KEY_IDENTITY_SET, true)
            .apply()

        Toast.makeText(this, "Identity saved!", Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
