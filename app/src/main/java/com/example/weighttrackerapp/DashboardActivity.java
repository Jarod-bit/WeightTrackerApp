
package com.example.weighttrackerapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private static final double GOAL_WEIGHT = 180.0;

    private EditText weightInput;
    private LinearLayout weightHistoryContainer;
    private TextView historyEmptyText;

    private DatabaseHelper databaseHelper;
    private long userId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Identify the logged-in user.
        userId = getIntent().getLongExtra("user_id", -1);

        if (userId <= 0) {
            Toast.makeText(
                    this,
                    "Please log in first",
                    Toast.LENGTH_SHORT
            ).show();

            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        // Connect to the SQLite database.
        databaseHelper = new DatabaseHelper(this);

        // Connect dashboard elements.
        weightInput = findViewById(R.id.weightInput);
        weightHistoryContainer = findViewById(
                R.id.weightHistoryContainer
        );
        historyEmptyText = findViewById(
                R.id.historyEmptyText
        );

        Button buttonAddWeight = findViewById(
                R.id.buttonAddWeight
        );

        Button buttonSmsSettings = findViewById(
                R.id.buttonSmsSettings
        );

        // Load saved weight records.
        loadWeightEntries();

        buttonAddWeight.setOnClickListener(
                v -> addWeightEntry()
        );

        buttonSmsSettings.setOnClickListener(v -> {
            Intent intent = new Intent(
                    this,
                    SmsActivity.class
            );

            intent.putExtra("user_id", userId);
            startActivity(intent);
        });
    }

    // CREATE: Add and save a new weight entry.
    private void addWeightEntry() {

        Double weight = parseWeight(
                weightInput.getText().toString()
        );

        if (weight == null) {
            return;
        }

        String currentDate = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(new Date());

        long result = databaseHelper.addWeight(
                userId,
                weight,
                currentDate
        );

        if (result != -1) {

            weightInput.setText("");

            Toast.makeText(
                    this,
                    "Weight saved successfully!",
                    Toast.LENGTH_SHORT
            ).show();

            // Refresh saved entries.
            loadWeightEntries();

            // Check whether the new weight meets the goal.
            checkGoalNotification(weight);

        } else {

            Toast.makeText(
                    this,
                    "Unable to save weight",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // Check whether the user qualifies for an SMS alert.
    private void checkGoalNotification(double weight) {

        if (weight > GOAL_WEIGHT) {
            return;
        }

        SharedPreferences preferences = getSharedPreferences(
                SmsActivity.PREFS_NAME,
                MODE_PRIVATE
        );

        boolean enabled = preferences.getBoolean(
                SmsActivity.enabledKey(userId),
                false
        );

        boolean alreadyRequested = preferences.getBoolean(
                SmsActivity.goalSentKey(userId),
                false
        );

        // Do not send without consent or repeat a prior request.
        if (!enabled || alreadyRequested) {
            return;
        }

        String phone = preferences.getString(
                SmsActivity.phoneKey(userId),
                ""
        );

        if (phone == null ||
                !phone.matches("\\+1\\d{10}")) {

            Toast.makeText(
                    this,
                    "Weight saved, but SMS phone number is invalid",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        // Verify SMS permission before sending.
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
        ) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(
                    this,
                    "Goal reached! Enable SMS permission in settings.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        sendGoalSms(phone, weight, preferences);
    }

    // Submit the one-time SMS goal notification.
    private void sendGoalSms(
            String phone,
            double weight,
            SharedPreferences preferences) {

        String message = String.format(
                Locale.US,
                "Congratulations! You reached your Weight Tracker " +
                        "goal of %.0f lbs! Your latest recorded weight " +
                        "is %.1f lbs. Great job!",
                GOAL_WEIGHT,
                weight
        );

        try {

            SmsManager smsManager = SmsManager.getDefault();

            smsManager.sendTextMessage(
                    phone,
                    null,
                    message,
                    null,
                    null
            );

            // Mark the request to avoid automatically repeating it.
            // This confirms submission, not carrier delivery.
            preferences.edit()
                    .putBoolean(
                            SmsActivity.goalSentKey(userId),
                            true
                    )
                    .apply();

            Toast.makeText(
                    this,
                    "Goal reached! SMS send requested.",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Weight saved, but SMS could not be sent.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // READ: Retrieve weight history from SQLite.
    private void loadWeightEntries() {

        weightHistoryContainer.removeAllViews();

        boolean hasEntries = false;

        try (Cursor cursor =
                     databaseHelper.getWeightEntries(userId)) {

            while (cursor.moveToNext()) {

                long entryId = cursor.getLong(0);
                double weight = cursor.getDouble(1);
                String date = cursor.getString(2);

                createWeightRow(
                        entryId,
                        date,
                        weight
                );

                hasEntries = true;
            }
        }

        historyEmptyText.setVisibility(
                hasEntries ? View.GONE : View.VISIBLE
        );
    }

    // Display a row containing date, weight, and actions.
    private void createWeightRow(
            long entryId,
            String date,
            double weight) {

        LinearLayout row = new LinearLayout(this);

        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                dp(8),
                dp(6),
                dp(8),
                dp(6)
        );

        TextView dateText = new TextView(this);
        dateText.setText(date);
        dateText.setTextSize(14);
        dateText.setSingleLine(true);

        row.addView(
                dateText,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        TextView weightText = new TextView(this);

        weightText.setText(
                String.format(
                        Locale.US,
                        "%.1f lbs",
                        weight
                )
        );

        weightText.setTextSize(14);
        weightText.setSingleLine(true);

        row.addView(
                weightText,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        Button manageButton = new Button(this);

        manageButton.setText("Manage");
        manageButton.setTextSize(12);
        manageButton.setAllCaps(false);
        manageButton.setMinWidth(0);
        manageButton.setMinimumWidth(0);

        manageButton.setOnClickListener(v ->
                showEntryOptions(entryId, weight)
        );

        row.addView(manageButton);

        weightHistoryContainer.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(0xFFCCCCCC);

        weightHistoryContainer.addView(
                divider,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                )
        );
    }

    // Show Edit and Delete choices for a record.
    private void showEntryOptions(
            long entryId,
            double currentWeight) {

        String[] options = {
                "Edit Weight",
                "Delete Weight"
        };

        new AlertDialog.Builder(this)
                .setTitle("Manage Weight Entry")
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {
                        showEditDialog(entryId, currentWeight);
                    } else {
                        showDeleteDialog(entryId);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // UPDATE: Edit an existing database record.
    private void showEditDialog(
            long entryId,
            double currentWeight) {

        EditText editWeight = new EditText(this);

        editWeight.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        editWeight.setText(
                String.valueOf(currentWeight)
        );

        editWeight.selectAll();

        LinearLayout container = new LinearLayout(this);

        container.setPadding(
                dp(20),
                dp(8),
                dp(20),
                dp(8)
        );

        container.addView(editWeight);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Weight")
                .setMessage("Enter the new weight in lbs.")
                .setView(container)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(ignored -> {

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                Double newWeight = parseWeight(
                        editWeight.getText().toString()
                );

                if (newWeight == null) {
                    return;
                }

                boolean updated = databaseHelper.updateWeight(
                        userId,
                        entryId,
                        newWeight
                );

                if (updated) {

                    Toast.makeText(
                            this,
                            "Weight updated!",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadWeightEntries();
                    dialog.dismiss();

                } else {

                    Toast.makeText(
                            this,
                            "Unable to update weight",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        });

        dialog.show();
    }

    // DELETE: Confirm before removing a database record.
    private void showDeleteDialog(long entryId) {

        new AlertDialog.Builder(this)
                .setTitle("Delete Weight")
                .setMessage(
                        "Are you sure you want to delete this weight entry?"
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> {

                            boolean deleted =
                                    databaseHelper.deleteWeight(
                                            userId,
                                            entryId
                                    );

                            if (deleted) {

                                Toast.makeText(
                                        this,
                                        "Weight entry deleted",
                                        Toast.LENGTH_SHORT
                                ).show();

                                loadWeightEntries();

                            } else {

                                Toast.makeText(
                                        this,
                                        "Unable to delete weight",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Validate weight input.
    private Double parseWeight(String input) {

        try {

            double weight = Double.parseDouble(
                    input.trim()
            );

            if (weight <= 0 ||
                    Double.isNaN(weight) ||
                    Double.isInfinite(weight)) {

                throw new NumberFormatException();
            }

            return weight;

        } catch (NumberFormatException e) {

            Toast.makeText(
                    this,
                    "Please enter a valid weight greater than 0",
                    Toast.LENGTH_SHORT
            ).show();

            return null;
        }
    }

    // Convert density-independent pixels.
    private int dp(int value) {

        return (int) (
                value *
                        getResources().getDisplayMetrics().density
                        + 0.5f
        );
    }

    @Override
    protected void onDestroy() {

        if (databaseHelper != null) {
            databaseHelper.close();
        }

        super.onDestroy();
    }
}
