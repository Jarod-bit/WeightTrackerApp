
package com.example.weighttrackerapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "WeightTracker.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_USERS = "users";
    private static final String TABLE_WEIGHTS = "weight_entries";

    private static final int HASH_ITERATIONS = 120000;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // Store registered user accounts.
        db.execSQL(
                "CREATE TABLE " + TABLE_USERS + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT UNIQUE NOT NULL, " +
                        "password_hash TEXT NOT NULL, " +
                        "salt TEXT NOT NULL)"
        );

        // Store each user's weight entries.
        db.execSQL(
                "CREATE TABLE " + TABLE_WEIGHTS + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "user_id INTEGER NOT NULL, " +
                        "weight REAL NOT NULL, " +
                        "entry_date TEXT NOT NULL, " +
                        "FOREIGN KEY(user_id) REFERENCES users(id) " +
                        "ON DELETE CASCADE)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,
                          int oldVersion, int newVersion) {
        // Add migrations here if the schema changes.
    }

    // CREATE ACCOUNT
    public boolean createAccount(String username, String password) {

        if (username == null || username.trim().isEmpty()
                || password == null || password.isEmpty()) {
            return false;
        }

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        byte[] passwordHash = hashPassword(password, salt);

        ContentValues values = new ContentValues();
        values.put("username", username.trim());
        values.put("password_hash",
                Base64.encodeToString(passwordHash, Base64.NO_WRAP));
        values.put("salt",
                Base64.encodeToString(salt, Base64.NO_WRAP));

        SQLiteDatabase db = getWritableDatabase();

        long result = db.insertWithOnConflict(
                TABLE_USERS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        return result != -1;
    }

    // LOGIN USER
    public long authenticateUser(String username, String password) {

        if (username == null || password == null) {
            return -1;
        }

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_USERS,
                new String[]{"id", "password_hash", "salt"},
                "username = ?",
                new String[]{username.trim()},
                null,
                null,
                null
        )) {

            if (!cursor.moveToFirst()) {
                return -1;
            }

            long userId = cursor.getLong(0);

            byte[] storedHash = Base64.decode(
                    cursor.getString(1), Base64.NO_WRAP);

            byte[] salt = Base64.decode(
                    cursor.getString(2), Base64.NO_WRAP);

            byte[] enteredHash = hashPassword(password, salt);

            return MessageDigest.isEqual(storedHash, enteredHash)
                    ? userId : -1;
        }
    }

    // ADD WEIGHT ENTRY
    public long addWeight(long userId, double weight,
                          String entryDate) {

        if (userId <= 0 || !isValidWeight(weight)
                || entryDate == null || entryDate.isEmpty()) {
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("weight", weight);
        values.put("entry_date", entryDate);

        return db.insert(TABLE_WEIGHTS, null, values);
    }

    // VIEW ALL WEIGHT ENTRIES FOR A USER
    // The caller must close the returned Cursor.
    public Cursor getWeightEntries(long userId) {

        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                TABLE_WEIGHTS,
                new String[]{"id", "weight", "entry_date"},
                "user_id = ?",
                new String[]{String.valueOf(userId)},
                null,
                null,
                "entry_date DESC, id DESC"
        );
    }

    // UPDATE AN EXISTING WEIGHT ENTRY
    public boolean updateWeight(long userId, long entryId,
                                double newWeight) {

        if (userId <= 0 || entryId <= 0
                || !isValidWeight(newWeight)) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("weight", newWeight);

        int rowsUpdated = db.update(
                TABLE_WEIGHTS,
                values,
                "id = ? AND user_id = ?",
                new String[]{
                        String.valueOf(entryId),
                        String.valueOf(userId)
                }
        );

        return rowsUpdated > 0;
    }

    // DELETE A WEIGHT ENTRY
    public boolean deleteWeight(long userId, long entryId) {

        if (userId <= 0 || entryId <= 0) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();

        int rowsDeleted = db.delete(
                TABLE_WEIGHTS,
                "id = ? AND user_id = ?",
                new String[]{
                        String.valueOf(entryId),
                        String.valueOf(userId)
                }
        );

        return rowsDeleted > 0;
    }

    // VALIDATE WEIGHT VALUES
    private boolean isValidWeight(double weight) {
        return !Double.isNaN(weight)
                && !Double.isInfinite(weight)
                && weight > 0;
    }

    // SECURELY HASH USER PASSWORDS
    private byte[] hashPassword(String password, byte[] salt) {

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                HASH_ITERATIONS,
                256
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(
                            "PBKDF2WithHmacSHA1"
                    );

            return factory.generateSecret(spec).getEncoded();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Unable to hash password", e
            );

        } finally {
            spec.clearPassword();
        }
    }
}
