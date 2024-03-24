package uk.ac.wlv.mobileblogging;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class PostCreationActivity extends AppCompatActivity {
    private static final String TAG = "PostCreationActivity";
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private Uri selectedImageUri = null;
    private ImageView imagePreview;
    private DatabaseHelper databaseHelper;
    private EditText editTextPostTitle;
    private EditText editTextPostText;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_postcreation);
        databaseHelper = new DatabaseHelper(this);
        imagePreview = findViewById(R.id.imagePreview);
        editTextPostTitle = findViewById(R.id.editTextTitle);
        editTextPostText = findViewById(R.id.editTextContent);
        Button btnUploadImage = findViewById(R.id.btnUploadImage);
        Button btnCaptureImage = findViewById(R.id.btnCaptureImage);
        Button btnCreatePost = findViewById(R.id.btnCreatePost);

        btnUploadImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImagePicker();
            }
        });

        btnCaptureImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureImage();
            }
        });

        btnCreatePost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String postTitle = editTextPostTitle.getText().toString().trim();
                String postText = editTextPostText.getText().toString().trim();
                if (selectedImageUri != null) {
                    if (isOnline()) {
                        uploadImageAndCreatePost(postTitle, postText);
                    } else {
                        String imageUri = (selectedImageUri != null) ? selectedImageUri.toString() : "";
                        Log.d(TAG, "Locally saved image path" + imageUri);
                        createPost(postTitle, postText, imageUri, false);
                    }
                } else {
                    createPost(postTitle, postText, "", isOnline());
                }
            }
        });

        SharedPreferences preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        userEmail = preferences.getString("loggedInUserEmail", "");
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void openImagePicker() {
        Log.d(TAG, "openImagePicker");
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void captureImage() {
        Log.d(TAG, "captureImage");
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "captureImage(): Could not create image file", ex);
            }
            if (photoFile != null) {
                selectedImageUri = FileProvider.getUriForFile(this,
                        "uk.ac.wlv.mobileblogging.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, selectedImageUri);
                startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST);
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
    }

    private void uploadImageAndCreatePost(final String postTitle, final String postText) {
        final StorageReference imageRef = FirebaseStorage.getInstance().getReference()
                .child("postImages/" + UUID.randomUUID().toString());

        Log.d(TAG, "Starting image upload to Firebase Storage.");
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

        imageRef.putFile(selectedImageUri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d(TAG, "Image successfully uploaded to Firebase Storage.");
                        Toast.makeText(PostCreationActivity.this, "Image uploaded. Creating post...", Toast.LENGTH_SHORT).show();

                        imageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri downloadUri) {
                                String imageDownloadUrl = downloadUri.toString();
                                Log.d(TAG, "Received image download URL: " + imageDownloadUrl);
                                createPost(postTitle, postText, imageDownloadUrl, true);
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Image upload failed", e);
                        Toast.makeText(PostCreationActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createPost(String postTitle, String postText, String imageUrl, boolean isSynced) {
        Log.d(TAG, "createPost method");

        // Validate fields before creating the post
        if (postTitle.isEmpty() && postText.isEmpty()) {
            Toast.makeText(this, "Post can't be created without title and content", Toast.LENGTH_SHORT).show();
        } else if (postTitle.isEmpty()){
            Toast.makeText(this, "Post can't be created without a title", Toast.LENGTH_SHORT).show();
        } else if (postText.isEmpty()) {
            Toast.makeText(this, "Post can't be created without content", Toast.LENGTH_SHORT).show();
        } else {
            String userName = databaseHelper.getUserName(userEmail);

            Log.d(TAG, "Adding post to database with data: UserName: " + userName + ", Title: " + postTitle + ", Text: " + postText + ", ImageURL: " + imageUrl + ", IsSynced: " + isSynced);
            long result = databaseHelper.addPost(userName, postTitle, postText, imageUrl, isSynced);

            if (result != -1) {
                Intent intent = new Intent(PostCreationActivity.this, BloggingActivity.class);
                startActivity(intent);
                finish();
                Toast.makeText(this, "Successfully Created a Post..!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Post Creation is Failed..!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                imagePreview.setImageURI(selectedImageUri);
            }
        } else if (requestCode == CAPTURE_IMAGE_REQUEST && resultCode == RESULT_OK) {
            if (selectedImageUri != null) {
                imagePreview.setImageURI(selectedImageUri);
            } else {
                Toast.makeText(this, "Error capturing image", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
