
package com.example.weighttrackerapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText usernameText;
    private EditText passwordText;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Connect to the SQLite database.
        databaseHelper = new DatabaseHelper(this);

        // Connect Java variables to the login screen.
        usernameText = findViewById(R.id.usernameText);
        passwordText = findViewById(R.id.passwordText);

        Button buttonLogin = findViewById(R.id.buttonLogin);
        Button buttonCreateAccount =
                findViewById(R.id.buttonCreateAccount);

        // Handle user login.
        buttonLogin.setOnClickListener(v -> loginUser());

        // Handle new account registration.
        buttonCreateAccount.setOnClickListener(
                v -> createAccount()
        );
    }

    // Check the entered username and password.
    private void loginUser() {

        String username =
                usernameText.getText().toString().trim();

        String password =
                passwordText.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(
                    this,
                    "Enter a username and password",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // Validate credentials against saved accounts.
        long userId = databaseHelper.authenticateUser(
                username, password
        );

        if (userId != -1) {

            Toast.makeText(
                    this,
                    "Login successful!",
                    Toast.LENGTH_SHORT
            ).show();

            // Open the dashboard for this user.
            Intent intent = new Intent(
                    MainActivity.this,
                    DashboardActivity.class
            );

            intent.putExtra("user_id", userId);
            startActivity(intent);
            finish();

        } else {

            Toast.makeText(
                    this,
                    "Invalid username or password",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // Register a new account in SQLite.
    private void createAccount() {

        String username =
                usernameText.getText().toString().trim();

        String password =
                passwordText.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(
                    this,
                    "Enter a username and password",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // Save the new account in the database.
        boolean success = databaseHelper.createAccount(
                username, password
        );

        if (success) {

            Toast.makeText(
                    this,
                    "Account created! Please log in.",
                    Toast.LENGTH_LONG
            ).show();

            passwordText.setText("");

        } else {

            Toast.makeText(
                    this,
                    "Unable to create account. Username may exist.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onDestroy() {
        databaseHelper.close();
        super.onDestroy();
    }
}
