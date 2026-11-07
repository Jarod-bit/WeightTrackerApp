
package com.example.weighttrackerapp;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SmsActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 100;

    public static final String PREFS_NAME = "sms_settings";

    private EditText smsPhoneInput;
    private TextView smsStatus;

    private SharedPreferences preferences;
    private long userId;

    // Use separate preferences for each logged-in user.
    public static String enabledKey(long userId) {
        return "enabled_" + userId;
    }

    public static String phoneKey(long userId) {
        return "phone_" + userId;
    }

    public static String goalSentKey(long userId) {
        return "goal_sent_" + userId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms);

        // Identify the logged-in user.
        userId = getIntent().getLongExtra("user_id", -1);

        if (userId <= 0) {
            Toast.makeText(
                    this,
                    "Please log in first",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        // Open saved notification preferences.
        preferences = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        );

        smsPhoneInput = findViewById(R.id.smsPhoneInput);
        smsStatus = findViewById(R.id.smsStatus);

        Button buttonAllowSms =
                findViewById(R.id.buttonAllowSms);

        Button buttonDisableSms =
                findViewById(R.id.buttonDisableSms);

        Button buttonSkipSms =
                findViewById(R.id.buttonSkipSms);

        // Restore the previously saved phone number.
        String savedPhone = preferences.getString(
                phoneKey(userId),
                ""
        );

        smsPhoneInput.setText(savedPhone);

        updateStatus();

        // Enable SMS alerts.
        buttonAllowSms.setOnClickListener(v -> {

            String phone = normalizePhone(
                    smsPhoneInput.getText().toString()
            );

            if (phone == null) {
                Toast.makeText(
                        this,
                        "Enter a valid 10-digit US phone number",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED) {

                saveEnabledSettings(phone);

            } else {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.SEND_SMS
                        },
                        SMS_PERMISSION_CODE
                );
            }
        });

        // Disable SMS without affecting weight tracking.
        buttonDisableSms.setOnClickListener(v -> {

            preferences.edit()
                    .putBoolean(enabledKey(userId), false)
                    .apply();

            updateStatus();

            Toast.makeText(
                    this,
                    "SMS alerts disabled",
                    Toast.LENGTH_SHORT
            ).show();
        });

        // Return to the dashboard.
        buttonSkipSms.setOnClickListener(v -> finish());
    }

    // Save the user's explicit SMS notification choice.
    private void saveEnabledSettings(String phone) {

        preferences.edit()
                .putString(phoneKey(userId), phone)
                .putBoolean(enabledKey(userId), true)
                .apply();

        smsPhoneInput.setText(phone);

        updateStatus();

        Toast.makeText(
                this,
                "SMS alerts enabled",
                Toast.LENGTH_SHORT
        ).show();
    }

    // Accept 10-digit US numbers or numbers beginning with 1.
    private String normalizePhone(String input) {

        String digits = input.replaceAll("[^0-9]", "");

        if (digits.length() == 11 &&
                digits.startsWith("1")) {

            digits = digits.substring(1);
        }

        if (digits.length() != 10) {
            return null;
        }

        return "+1" + digits;
    }

    // Display whether SMS notifications are enabled.
    private void updateStatus() {

        boolean enabled = preferences.getBoolean(
                enabledKey(userId),
                false
        );

        boolean permissionGranted =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED;

        if (enabled && permissionGranted) {

            smsStatus.setText(
                    "SMS Notifications: Enabled"
            );

        } else {

            smsStatus.setText(
                    "SMS Notifications: Disabled"
            );
        }
    }

    // Handle the user's permission decision.
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode != SMS_PERMISSION_CODE) {
            return;
        }

        if (grantResults.length > 0 &&
                grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {

            String phone = normalizePhone(
                    smsPhoneInput.getText().toString()
            );

            if (phone != null) {
                saveEnabledSettings(phone);
            }

        } else {

            preferences.edit()
                    .putBoolean(enabledKey(userId), false)
                    .apply();

            updateStatus();

            Toast.makeText(
                    this,
                    "SMS permission denied. Weight tracking will still work.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (preferences != null) {
            updateStatus();
        }
    }
}
