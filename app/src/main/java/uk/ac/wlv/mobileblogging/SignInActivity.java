package uk.ac.wlv.mobileblogging;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SignInActivity extends AppCompatActivity {
    private TextView mSignUpTextView;
    private EditText mEmail;
    private EditText mPassword;
    private DatabaseHelper mDbHelper;
    private Button mSignInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isUserLoggedIn()) {
            Intent intent = new Intent(SignInActivity.this, BloggingActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        setContentView(R.layout.activity_signin);
        mSignUpTextView = findViewById(R.id.signUpTextView);
        mSignUpTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SignInActivity.this, SignUpActivity.class);
                startActivity(intent);
            }
        });
        mEmail = findViewById(R.id.email);
        mPassword = findViewById(R.id.password);
        mDbHelper = new DatabaseHelper(this);
        mSignInButton = findViewById(R.id.signInButton);
        mSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = mEmail.getText().toString();
                String password = mPassword.getText().toString();

                if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                    Toast.makeText(SignInActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                } else {
                    if (mDbHelper.checkUser(email, password)) {
                        Intent intent = new Intent(SignInActivity.this, BloggingActivity.class);
                        startActivity(intent);
                        Toast.makeText(SignInActivity.this, "Sign-in successful", Toast.LENGTH_SHORT).show();
                        saveUserEmail(email);
                    } else {
                        Toast.makeText(SignInActivity.this, "Sign-in failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void saveUserEmail(String email) {
        SharedPreferences preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("loggedInUserEmail", email);
        editor.putBoolean("isLoggedIn", true);
        editor.apply();
    }

    private boolean isUserLoggedIn() {
        SharedPreferences preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        return preferences.getBoolean("isLoggedIn", false);
    }

}
