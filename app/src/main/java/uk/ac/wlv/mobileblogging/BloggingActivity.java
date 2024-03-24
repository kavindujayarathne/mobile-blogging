package uk.ac.wlv.mobileblogging;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BloggingActivity extends AppCompatActivity {
    private static final String TAG = "BloggingActivity";
    private BlogPostAdapter mBlogPostAdapter;
    private List<BlogPost> mBlogPosts;
    private ActionMode actionMode;
    private boolean isSearching = false;

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.contextual_menu, menu);
            Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setVisibility(View.GONE);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.action_delete) {
                List<Integer> selectedPostIds = new ArrayList<>();
                for (int i = mBlogPostAdapter.getSelectedItemCount() - 1; i >= 0; i--) {
                    int position = mBlogPostAdapter.getSelectedItems().get(i);
                    BlogPost post = mBlogPosts.get(position);
                    selectedPostIds.add(post.getId());
                }

                try (DatabaseHelper databaseHelper = new DatabaseHelper(BloggingActivity.this)) {
                    databaseHelper.deletePost(selectedPostIds);

                    Toast.makeText(BloggingActivity.this, "Posts deleted successfully.", Toast.LENGTH_SHORT).show();

                    List<Integer> selectedItemsPositions = new ArrayList<>(mBlogPostAdapter.getSelectedItems());
                    Collections.sort(selectedItemsPositions, Collections.reverseOrder());
                    for (int position : selectedItemsPositions) {
                        mBlogPosts.remove(position);
                    }

                    mBlogPostAdapter.clearSelections();
                    mBlogPostAdapter.notifyDataSetChanged();
                    mBlogPostAdapter.setBlogPostsFull(mBlogPosts);

                } catch (Exception e) {
                    Toast.makeText(BloggingActivity.this, "Failed to delete posts: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }

                mode.finish();
                return true;
            }
            if (item.getItemId() == R.id.action_upload) {
                List<Integer> selectedPostIds = new ArrayList<>(mBlogPostAdapter.getSelectedItems());
                shareSelectedPosts(selectedPostIds);
                mode.finish();
                return true;
            }
            return false;
        }

        private void shareSelectedPosts(List<Integer> selectedPostIds) {
            Toast.makeText(BloggingActivity.this, "Preparing to share " +
                    selectedPostIds.size() + " post(s)...", Toast.LENGTH_SHORT).show();
            if (selectedPostIds.size() == 1) {
                sharePost(mBlogPosts.get(selectedPostIds.get(0)));
            } else {
                for (int position : selectedPostIds) {
                    BlogPost post = mBlogPosts.get(position);
                    sharePost(post);
                }
            }
        }

        private void sharePost(BlogPost post) {
            String subject = post.getPostTitle();
            String shareText = post.getPostText();
            String combinedShareText = subject + "\n\n" + shareText;
            String imageUrl = post.getPostImageUri();

            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(BloggingActivity.this)
                        .downloadOnly()
                        .load(imageUrl)
                        .into(new SimpleTarget<File>() {
                            @Override
                            public void onResourceReady(@NonNull File resource, @NonNull Transition<? super File> transition) {
                                File sharedFile = createTemporaryFile(resource);
                                if (sharedFile != null) {
                                    Uri contentUri = FileProvider.getUriForFile(BloggingActivity.this,
                                            "uk.ac.wlv.mobileblogging.fileprovider", sharedFile);
                                    shareIntent(combinedShareText, contentUri);
                                }
                            }

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                Toast.makeText(BloggingActivity.this, "Failed to load image for sharing.", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                shareIntent(combinedShareText, null);
            }
        }

        private File createTemporaryFile(File resource) {
            try {
                File imagesDir = new File(getFilesDir(), "images");
                if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                    Log.e(TAG, "Failed to create images directory.");
                    return null;
                }
                String filename = "image_" + System.currentTimeMillis() + ".jpg";
                File sharedFile = new File(imagesDir, filename);
                if (copyFile(resource, sharedFile)) {
                    return sharedFile;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error creating temporary file", e);
            }
            return null;
        }

        private boolean copyFile(File src, File dst) throws IOException {
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                return true;
            }
        }

        private void shareIntent(String text, Uri imageUri) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            if (imageUri != null) {
                shareIntent.setType("image/jpeg");
                shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            } else {
                shareIntent.setType("text/plain");
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
            Toast.makeText(BloggingActivity.this, "Sharing in progress...", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mBlogPostAdapter.clearSelections();
            actionMode = null;

            final Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setVisibility(View.GONE);

            toolbar.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (actionMode == null) {
                        toolbar.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        toolbar.setVisibility(View.VISIBLE);
                                    }
                                })
                                .start();
                    }
                }
            }, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blogging);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);

        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );

        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.nav_logout) {
                    logout();
                }
                return true;
            }
        });

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mBlogPosts = new ArrayList<>();

        mBlogPostAdapter = new BlogPostAdapter(this, mBlogPosts, new BlogPostAdapter.OnItemClickListener() {
            @Override
            public void onShareClick(int position) {
            }

            @Override
            public void onItemClick(int position) {
                if(actionMode != null) {
                    toggleSelection(position);
                } else {
                    BlogPost selectedPost = mBlogPosts.get(position);
                    Intent detailIntent = new Intent(BloggingActivity.this, BlogPostDetailActivity.class);
                    detailIntent.putExtra("blogPostId", selectedPost.getId());
                    startActivity(detailIntent);
                }
            }

            @Override
            public void onItemLongClick(int position) {
                if(actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback);
                }
                toggleSelection(position);

            }
        });
        recyclerView.setAdapter(mBlogPostAdapter);

        setupFirebaseDataListener();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        moveTaskToBack(true);
    }

    private void logout() {
        SharedPreferences preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("loggedInUserEmail");
        editor.putBoolean("isLoggedIn", false);
        editor.apply();

        Intent intent = new Intent(BloggingActivity.this, SignInActivity.class);
        Toast.makeText(this, "Successfully Logged out!", Toast.LENGTH_SHORT).show();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toggleSelection(int position) {
        mBlogPostAdapter.toggleSelection(position);
        int count = mBlogPostAdapter.getSelectedItemCount();

        if (count == 0 && actionMode != null) {
            actionMode.finish();
        } else if (actionMode != null) {
            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isSearching) {
            retrievePostsFromDatabase();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_blogging, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setQueryHint("Search...");

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "Query submitted: " + query);
                mBlogPostAdapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "Query text changed: " + newText);
                isSearching = newText != null && !newText.isEmpty();
                mBlogPostAdapter.getFilter().filter(newText);
                return true;
            }
        });
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            Intent intent = new Intent(this, PostCreationActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void retrievePostsFromDatabase() {
        DatabaseHelper databaseHelper = new DatabaseHelper(this);
        List<BlogPost> posts = databaseHelper.getAllPosts();
        mBlogPosts.clear();
        mBlogPosts.addAll(posts);
        Collections.reverse(mBlogPosts);
        mBlogPostAdapter.notifyDataSetChanged();
        mBlogPostAdapter.setBlogPostsFull(posts);
    }

    private void setupFirebaseDataListener() {
        Log.d(TAG, "Setting up Firebase data listener.");
        DatabaseReference firebaseRef = FirebaseDatabase.getInstance().getReference("posts");
        ValueEventListener postListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                DatabaseHelper databaseHelper = new DatabaseHelper(BloggingActivity.this);
                List<Integer> firebasePostIds = new ArrayList<>();

                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    BlogPost firebasePost = postSnapshot.getValue(BlogPost.class);
                    if (firebasePost != null) {
                        firebasePostIds.add(firebasePost.getId());
                        BlogPost localPost = databaseHelper.getPostById(firebasePost.getId());

//                        Log.d(TAG, "Firebase Post: " + firebasePost.toString());
//                        Log.d(TAG, "Local Post: " + (localPost != null ? localPost.toString() : "null"));

                        if (localPost == null || !localPost.equals(firebasePost)) {
//                            Log.d(TAG, "Updating local post with Firebase data");
                            databaseHelper.addOrUpdatePost(firebasePost);
                        }
                    }
                }

                List<BlogPost> localPosts = databaseHelper.getAllPosts();
                for (BlogPost localPost : localPosts) {
                    if (!firebasePostIds.contains(localPost.getId()) && localPost.isSynced()) {
                        databaseHelper.deleteLocalPost(localPost.getId());
                    }
                }

                mBlogPosts.clear();
                List<BlogPost> allPosts = databaseHelper.getAllPosts();
                for (BlogPost post : allPosts) {
                    if (post.isSynced() || !firebasePostIds.contains(post.getId())) {
                        mBlogPosts.add(post);
                    }
                }
                Collections.reverse(mBlogPosts);
                mBlogPostAdapter.notifyDataSetChanged();
                mBlogPostAdapter.setBlogPostsFull(allPosts);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "Firebase data listener was cancelled.", databaseError.toException());
            }
        };
        firebaseRef.addValueEventListener(postListener);
    }
}
