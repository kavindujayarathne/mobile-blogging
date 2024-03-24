package uk.ac.wlv.mobileblogging;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "MobileBlogging.db";
    private static final int DATABASE_VERSION = 1;

    // Table 1
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PASSWORD = "password";

    // Table 2
    public static final String TABLE_POSTS = "posts";
    public static final String COLUMN_POST_ID = "post_id";
    public static final String COLUMN_USER_NAME = "user_name";
    public static final String COLUMN_POST_TITLE = "post_title";
    public static final String COLUMN_POST_TEXT = "post_text";
    public static final String COLUMN_POST_IMAGE_RESOURCE = "post_image_resource";
    public static final String COLUMN_IS_SYNCED = "is_synced";

    // SQL statement to create the users table
    private static final String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_NAME + " TEXT, " +
            COLUMN_EMAIL + " TEXT, " +
            COLUMN_PASSWORD + " TEXT);";

    private static final String CREATE_POSTS_TABLE = "CREATE TABLE " + TABLE_POSTS + " (" +
            COLUMN_POST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_USER_NAME + " TEXT, " +
            COLUMN_POST_TITLE + " TEXT, " +
            COLUMN_POST_TEXT + " TEXT, " +
            COLUMN_POST_IMAGE_RESOURCE + " TEXT, " +
            COLUMN_IS_SYNCED + " INTEGER DEFAULT 0);";


    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USERS_TABLE);
        db.execSQL(CREATE_POSTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    public long addUser(String name, String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_EMAIL, email);
        values.put(COLUMN_PASSWORD, password);

        long result = db.insert(TABLE_USERS, null, values);
        db.close();
        return result;
    }

    public boolean checkUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ID};
        String selection = COLUMN_EMAIL + " = ?" + " AND " + COLUMN_PASSWORD + " = ?";
        String[] selectionArgs = {email, password};
        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();
        return count > 0;
    }

    public boolean userExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + COLUMN_EMAIL + "=?";
        Cursor cursor = db.rawQuery(query, new String[]{email});

        boolean exists = cursor.getCount() > 0;

        cursor.close();
        db.close();

        return exists;
    }

    public long addPost(String userName, String postTitle, String postText, String imageUri, boolean isSynced) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_NAME, userName);
        values.put(COLUMN_POST_TITLE, postTitle);
        values.put(COLUMN_POST_TEXT, postText);
        values.put(COLUMN_POST_IMAGE_RESOURCE, imageUri);
        values.put(COLUMN_IS_SYNCED, isSynced ? 1 : 0);

        long newPostId = db.insert(TABLE_POSTS, null, values);

        if (newPostId != -1 && isSynced) {
            BlogPost newPost = new BlogPost((int) newPostId, userName, postTitle, postText, imageUri, true);

            DatabaseReference firebaseRef = FirebaseDatabase.getInstance().getReference("posts");
            firebaseRef.child(String.valueOf(newPostId)).setValue(newPost);
        }

        db.close();
        return newPostId;
    }

    public String getUserName(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_NAME};
        String selection = COLUMN_EMAIL + " = ?";
        String[] selectionArgs = {email};
        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);

        String userName = null;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COLUMN_NAME);
            if (columnIndex != -1) {
                userName = cursor.getString(columnIndex);
            }
        }

        if (cursor != null) {
            cursor.close();
        }
        db.close();
        return userName;
    }

    public List<BlogPost> getAllPosts() {
        List<BlogPost> posts = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_POSTS, null, null, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            int postIdColumnIndex = cursor.getColumnIndex(COLUMN_POST_ID);
            int userNameColumnIndex = cursor.getColumnIndex(COLUMN_USER_NAME);
            int postTitleColumnIndex = cursor.getColumnIndex(COLUMN_POST_TITLE);
            int postTextColumnIndex = cursor.getColumnIndex(COLUMN_POST_TEXT);
            int imageUriColumnIndex = cursor.getColumnIndex(COLUMN_POST_IMAGE_RESOURCE);
            int isSyncedColumnIndex = cursor.getColumnIndex(COLUMN_IS_SYNCED);

            do {
                if (userNameColumnIndex != -1 && postTitleColumnIndex != -1 && postTextColumnIndex != -1 && imageUriColumnIndex != -1 && isSyncedColumnIndex != -1) {
                    int id = cursor.getInt(postIdColumnIndex);
                    String userName = cursor.getString(userNameColumnIndex);
                    String postTitle = cursor.getString(postTitleColumnIndex);
                    String postText = cursor.getString(postTextColumnIndex);
                    String imageUri = cursor.getString(imageUriColumnIndex);
                    boolean isSynced = cursor.getInt(isSyncedColumnIndex) == 1;

                    BlogPost post = new BlogPost(id, userName, postTitle, postText, imageUri, isSynced);
                    posts.add(post);
                }
            } while (cursor.moveToNext());

            cursor.close();
        } else {
            Log.d(TAG, "Cursor is null or empty.");
        }

        db.close();
//        Log.d(TAG, "Total posts loaded: " + posts.size());
        return posts;
    }

    // Method to get a single post by ID
    public BlogPost getPostById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_POSTS, null, COLUMN_POST_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null);

        BlogPost blogPost = null;
        if (cursor != null && cursor.moveToFirst()) {
            int postIdIndex = cursor.getColumnIndex(COLUMN_POST_ID);
            int userNameIndex = cursor.getColumnIndex(COLUMN_USER_NAME);
            int postTitleIndex = cursor.getColumnIndex(COLUMN_POST_TITLE);
            int postTextIndex = cursor.getColumnIndex(COLUMN_POST_TEXT);
            int imageUriIndex = cursor.getColumnIndex(COLUMN_POST_IMAGE_RESOURCE);
            int isSyncedIndex = cursor.getColumnIndex(COLUMN_IS_SYNCED);

            if (postIdIndex != -1 && userNameIndex != -1 && postTitleIndex != -1 && postTextIndex != -1 && imageUriIndex != -1 && isSyncedIndex != -1) {
                int postId = cursor.getInt(postIdIndex);
                String userName = cursor.getString(userNameIndex);
                String postTitle = cursor.getString(postTitleIndex);
                String postText = cursor.getString(postTextIndex);
                String imageUri = cursor.getString(imageUriIndex);
                boolean isSynced = cursor.getInt(isSyncedIndex) == 1;

                blogPost = new BlogPost(postId, userName, postTitle, postText, imageUri, isSynced);
            } else {
                Log.e(TAG, "One or more columns not found in the cursor");
            }
            cursor.close();
        }
        db.close();
        return blogPost;
    }

    // Method to update a single post by ID
    public boolean updatePost(int postId, String title, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_POST_TITLE, title);
        values.put(COLUMN_POST_TEXT, content);

        int updateCount = db.update(TABLE_POSTS, values, COLUMN_POST_ID + " = ?", new String[]{String.valueOf(postId)});

        if (updateCount > 0) {
            BlogPost updatedPost = getPostById(postId);

            DatabaseReference firebaseRef = FirebaseDatabase.getInstance().getReference("posts");
            firebaseRef.child(String.valueOf(postId)).setValue(updatedPost);
        }

        db.close();
        return updateCount > 0;
    }

    // Method to delete posts
    public void deletePost(List<Integer> postIds) {
        SQLiteDatabase db = this.getWritableDatabase();
        DatabaseReference firebaseRef = FirebaseDatabase.getInstance().getReference("posts");

        for (int postId : postIds) {
            String sql = "DELETE FROM " + TABLE_POSTS + " WHERE " + COLUMN_POST_ID + " = " + postId;
            db.execSQL(sql);

            firebaseRef.child(String.valueOf(postId)).removeValue();
        }

        db.close();
    }

    // Method to delete local posts which delete from the firebase
    public void deleteLocalPost(int postId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String sql = "DELETE FROM " + TABLE_POSTS + " WHERE " + COLUMN_POST_ID + " = " + postId;
        db.execSQL(sql);
        db.close();
    }

    // Method to add or update post from the server side
    public void addOrUpdatePost(BlogPost post) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_NAME, post.getUserName());
        values.put(COLUMN_POST_TITLE, post.getPostTitle());
        values.put(COLUMN_POST_TEXT, post.getPostText());
        values.put(COLUMN_POST_IMAGE_RESOURCE, post.getPostImageUri());
        values.put(COLUMN_IS_SYNCED, 1);

        Cursor cursor = db.query(TABLE_POSTS, null, COLUMN_POST_ID + " = ?",
                new String[]{String.valueOf(post.getId())}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            db.update(TABLE_POSTS, values, COLUMN_POST_ID + " = ?", new String[]{String.valueOf(post.getId())});
        } else {
            values.put(COLUMN_POST_ID, post.getId());
            db.insert(TABLE_POSTS, null, values);
        }
        if (cursor != null) {
            cursor.close();
        }
        db.close();
    }

    public List<BlogPost> getUnsyncedPosts() {
        List<BlogPost> posts = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_POSTS, null, COLUMN_IS_SYNCED + " = 0", null, null, null, null);

            int postIdIndex = cursor.getColumnIndexOrThrow(COLUMN_POST_ID);
            int userNameIndex = cursor.getColumnIndexOrThrow(COLUMN_USER_NAME);
            int titleIndex = cursor.getColumnIndexOrThrow(COLUMN_POST_TITLE);
            int textIndex = cursor.getColumnIndexOrThrow(COLUMN_POST_TEXT);
            int imageUriIndex = cursor.getColumnIndexOrThrow(COLUMN_POST_IMAGE_RESOURCE);

            while (cursor.moveToNext()) {
                int id = cursor.getInt(postIdIndex);
                String userName = cursor.getString(userNameIndex);
                String title = cursor.getString(titleIndex);
                String text = cursor.getString(textIndex);
                String imageUri = cursor.getString(imageUriIndex);

                posts.add(new BlogPost(id, userName, title, text, imageUri, false));

            }
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to get unsynced posts from database", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            db.close();
        }
        return posts;
    }

    public void synchronizePosts() {
        List<BlogPost> unsyncedPosts = getUnsyncedPosts();
        for (BlogPost post : unsyncedPosts) {
            if (post.getPostImageUri() != null && !post.getPostImageUri().isEmpty()) {
                Uri imageUri = Uri.parse(post.getPostImageUri());
                StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("postImages/" + UUID.randomUUID().toString());

                storageRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String remoteImageUrl = uri.toString();
                        updatePostInFirebase(post, remoteImageUrl);
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get download URL", e);
                    });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload image to Firebase Storage", e);
                });
            } else {
                updatePostInFirebase(post, "");
            }
        }
    }

    public void updatePostInFirebase(BlogPost post, String remoteImageUrl) {
        DatabaseReference firebaseRef = FirebaseDatabase.getInstance().getReference("posts");
        post.setPostImageUri(remoteImageUrl);

        post.setSynced(true);

        firebaseRef.child(String.valueOf(post.getId())).setValue(post)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Post updated successfully in Firebase"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update post in Firebase", e));
    }
}