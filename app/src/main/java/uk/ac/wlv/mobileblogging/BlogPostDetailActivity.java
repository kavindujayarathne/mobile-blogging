package uk.ac.wlv.mobileblogging;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.database.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BlogPostDetailActivity extends AppCompatActivity {
    private static final String TAG = "BlogPostDetailActivity";
    private EditText titleTextView;
    private EditText contentTextView;
    private ImageButton saveButton;
    private int postId;
    private ImageView editButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blog_post_detail);

        // Retrieve the views
        TextView userNameTextView = findViewById(R.id.detailUserName);
        titleTextView = findViewById(R.id.detailPostTitle);
        contentTextView = findViewById(R.id.detailPostText);
        ImageView postImageView = findViewById(R.id.detailPostImage);
        ImageButton shareButton = findViewById(R.id.btnShare);
        editButton = findViewById(R.id.btnEdit);
        saveButton = findViewById(R.id.btnSave);

        titleTextView.setEnabled(false);
        contentTextView.setEnabled(false);

        // Get the post ID from the Intent
        postId = getIntent().getIntExtra("blogPostId", -1);
        Log.d(TAG, "Post ID: " + postId);

        if (postId != -1) {
            try(DatabaseHelper databaseHelper = new DatabaseHelper(this)) {
                BlogPost blogPost = databaseHelper.getPostById(postId);

                userNameTextView.setText(blogPost.getUserName());
                titleTextView.setText(blogPost.getPostTitle());
                contentTextView.setText(blogPost.getPostText());

                String imageUri = blogPost.getPostImageUri();
                if (imageUri != null && !imageUri.isEmpty()) {
                    postImageView.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(Uri.parse(imageUri))
                            .into(postImageView);
                } else {
                    postImageView.setVisibility(View.GONE);
                    postImageView.setImageDrawable(null);
                }

                shareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String subject = blogPost.getPostTitle();
                        String shareText = blogPost.getPostText();
                        String imageUrl = blogPost.getPostImageUri();

                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Glide.with(BlogPostDetailActivity.this)
                                    .downloadOnly()
                                    .load(imageUrl)
                                    .into(new SimpleTarget<File>() {
                                        @Override
                                        public void onResourceReady(@NonNull File resource, @NonNull Transition<? super File> transition) {
                                            File imagesDir = new File(getFilesDir(), "images");
                                            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                                                Log.e(TAG, "Failed to create images directory.");
                                                return;
                                            }

                                            String filename = "image_" + System.currentTimeMillis() + ".jpg";
                                            File sharedFile = new File(imagesDir, filename);
                                            if (copyFile(resource, sharedFile)) {
                                                Uri contentUri = FileProvider.getUriForFile(BlogPostDetailActivity.this,
                                                        "uk.ac.wlv.mobileblogging.fileprovider", sharedFile);
                                                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                                shareIntent.setType("image/jpeg");
                                                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                                                shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                                                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                                                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                                            }
                                        }

                                        @Override
                                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                            Toast.makeText(BlogPostDetailActivity.this, "Failed to load image for sharing.", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        } else {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                        }
                    }

                    private boolean copyFile(File src, File dst) {
                        try (InputStream in = new FileInputStream(src);
                             OutputStream out = new FileOutputStream(dst)) {
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                            return true;
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to copy file", e);
                            return false;
                        }
                    }
                });

                editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    titleTextView.setEnabled(true);
                    contentTextView.setEnabled(true);
                    saveButton.setVisibility(View.VISIBLE);
                    editButton.setVisibility(View.GONE);
                }
            });

            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    savePost();

                    titleTextView.setEnabled(false);
                    contentTextView.setEnabled(false);
                    saveButton.setVisibility(View.GONE);
                    editButton.setVisibility(View.VISIBLE);
                }
            });

            } catch (Exception e) {
                Toast.makeText(this, "Error loading post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

        } else {
            Toast.makeText(this, "Error loading post.", Toast.LENGTH_LONG).show();
        }
    }

    private void savePost() {
        String updatedTitle = titleTextView.getText().toString();
        String updatedContent = contentTextView.getText().toString();

        try (DatabaseHelper databaseHelper = new DatabaseHelper(this)) {
            boolean success = databaseHelper.updatePost(postId, updatedTitle, updatedContent);

            if (success) {
                titleTextView.setEnabled(false);
                contentTextView.setEnabled(false);
                saveButton.setVisibility(View.GONE);
                editButton.setVisibility(View.VISIBLE);

                Toast.makeText(this, "Post saved successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to save post.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error saving post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
