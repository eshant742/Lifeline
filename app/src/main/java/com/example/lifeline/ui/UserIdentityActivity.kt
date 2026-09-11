package com.example.lifeline.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lifeline.R
import com.example.lifeline.databinding.ActivityUserIdentityBinding

/**
 * First-launch identity and medical profile setup screen.
 *
 * Collects:
 * - User's name and phone number for SOS messages
 * - Blood type, allergies, medical conditions, emergency contact
 *
 * All data is saved to SharedPreferences and embedded in every SOS broadcast.
 * Shown only once — navigates to MainActivity after setup.
 */
class UserIdentityActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "lifeline_prefs"
        // Identity keys
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_PHONE = "user_phone"
        const val KEY_IDENTITY_SET = "identity_set"
        // Medical profile keys
        const val KEY_BLOOD_TYPE = "blood_type"
        const val KEY_ALLERGIES = "allergies"
        const val KEY_MEDICAL_CONDITIONS = "medical_conditions"
        const val KEY_EMERGENCY_CONTACT = "emergency_contact"
    }

    private lateinit var binding: ActivityUserIdentityBinding

    // Blood type options
    private val bloodTypes = arrayOf(
        "Not specified", "O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-"
    )

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

        setupBloodTypeDropdown()

        binding.btnSave.setOnClickListener {
            saveIdentity()
        }
    }

    private fun setupBloodTypeDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            bloodTypes
        )
        binding.spinnerBloodType.setAdapter(adapter)
        binding.spinnerBloodType.setText(bloodTypes[0], false)
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

        // Get medical profile (all optional)
        val selectedBloodType = binding.spinnerBloodType.text.toString()
        val bloodType = if (selectedBloodType == "Not specified") "" else selectedBloodType
        val allergies = binding.etAllergies.text.toString().trim()
        val medicalConditions = binding.etMedicalConditions.text.toString().trim()
        val emergencyContact = binding.etEmergencyContact.text.toString().trim()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            // Identity
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_PHONE, phone)
            .putBoolean(KEY_IDENTITY_SET, true)
            // Medical profile
            .putString(KEY_BLOOD_TYPE, bloodType)
            .putString(KEY_ALLERGIES, allergies)
            .putString(KEY_MEDICAL_CONDITIONS, medicalConditions)
            .putString(KEY_EMERGENCY_CONTACT, emergencyContact)
            .apply()

        val medicalInfo = buildString {
            append("Identity saved!")
            if (bloodType.isNotEmpty()) append(" 🩸$bloodType")
        }
        Toast.makeText(this, medicalInfo, Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
