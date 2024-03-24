package uk.ac.wlv.mobileblogging;

import java.util.Objects;

public class BlogPost {
    private int id;
    private String userName;
    private String postTitle;
    private String postText;
    private String postImageUri;
    private boolean isSynced;

    public BlogPost() { }

    public BlogPost(int id, String userName, String postTitle, String postText, String postImageUri, boolean isSynced) {
        this.id = id;
        this.userName = userName;
        this.postTitle = postTitle;
        this.postText = postText;
        this.postImageUri = postImageUri;
        this.isSynced = isSynced;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced){
        isSynced = synced;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPostTitle() {
        return postTitle;
    }

    public void setPostTitle(String postTitle) {
        this.postTitle = postTitle;
    }

    public String getPostText() {
        return postText;
    }

    public void setPostText(String postText) {
        this.postText = postText;
    }

    public String getPostImageUri() {
        return postImageUri;
    }

    public void setPostImageUri(String postImageUri) {
        this.postImageUri = postImageUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlogPost blogPost = (BlogPost) o;
        return id == blogPost.id &&
                isSynced == blogPost.isSynced &&
                Objects.equals(userName, blogPost.userName) &&
                Objects.equals(postTitle, blogPost.postTitle) &&
                Objects.equals(postText, blogPost.postText) &&
                Objects.equals(postImageUri, blogPost.postImageUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userName, postTitle, postText, postImageUri, isSynced);
    }
}

