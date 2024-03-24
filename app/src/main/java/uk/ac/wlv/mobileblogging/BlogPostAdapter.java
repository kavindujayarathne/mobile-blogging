package uk.ac.wlv.mobileblogging;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlogPostAdapter extends RecyclerView.Adapter<BlogPostAdapter.BlogPostViewHolder> implements Filterable {
    private static final String TAG = "BlogPostAdapter";
    private List<BlogPost> mBlogPostsFull;
    private List<BlogPost> mBlogPosts;
    private Context mContext;
    private OnItemClickListener mListener;
    private SparseBooleanArray selectedItems = new SparseBooleanArray();

    public void setBlogPostsFull(List<BlogPost> blogPostsFull) {
        mBlogPostsFull.clear();
        mBlogPostsFull.addAll(blogPostsFull);
        Collections.reverse(mBlogPostsFull);
        Log.d(TAG, "mBlogPostsFull updated, now has " + mBlogPostsFull.size() + " items.");
    }

    public BlogPostAdapter(Context context, List<BlogPost> blogPosts, OnItemClickListener listener) {
        this.mContext = context;
        this.mBlogPosts = blogPosts;
        this.mListener = listener;
        mBlogPostsFull = new ArrayList<>(blogPosts);
    }

    @NonNull
    @Override
    public BlogPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.post_item, parent, false);
        return new BlogPostViewHolder(view, mListener);
    }

    @Override
    public void onBindViewHolder(@NonNull BlogPostViewHolder holder, int position) {
        BlogPost blogPost = mBlogPosts.get(position);
        holder.mUserName.setText(blogPost.getUserName());
        holder.mPostTitle.setText(blogPost.getPostTitle());

        // Truncate content to a certain length for preview
        String contentPreview = truncateContent(blogPost.getPostText(), 50);
        holder.mPostText.setText(contentPreview);

        String imageUri = blogPost.getPostImageUri();
        if (imageUri != null && !imageUri.isEmpty()) {
            holder.mPostImage.setVisibility(View.VISIBLE);
            Uri uri = Uri.parse(imageUri);
            if (uri.getScheme() != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                Glide.with(mContext)
                        .load(uri)
                        .into(holder.mPostImage);
            } else {
                holder.mPostImage.setImageURI(uri);
            }
        } else {
            holder.mPostImage.setVisibility(View.GONE);
        }

        if (blogPost.isSynced()) {
            holder.mSyncStatusIcon.setImageResource(R.drawable.ic_sync);
        } else {
            holder.mSyncStatusIcon.setImageResource(R.drawable.ic_sync_disable);
        }

        RelativeLayout selectionBackground = holder.itemView.findViewById(R.id.selection_background);
        if(selectedItems.get(position, false)) {
            selectionBackground.setBackgroundColor(ContextCompat.getColor(mContext, R.color.selected_item_color));
        } else {
            selectionBackground.setBackgroundColor(ContextCompat.getColor(mContext, R.color.non_selected_item_color));
        }
    }

    public void toggleSelection(int position) {
        if (selectedItems.get(position, false)) {
            selectedItems.delete(position);
        } else {
            selectedItems.put(position, true);
        }
        notifyItemChanged(position);
    }

    public void clearSelections() {
        selectedItems.clear();
        notifyDataSetChanged();
    }

    public int getSelectedItemCount() {
        return selectedItems.size();
    }

    public List<Integer> getSelectedItems() {
        List<Integer> items = new ArrayList<>(selectedItems.size());
        for (int i = 0; i < selectedItems.size(); i++) {
            items.add(selectedItems.keyAt(i));
        }
        return items;
    }

    private String truncateContent(String content, int maxLength) {
        if(content.length() > maxLength) {
            return content.substring(0, maxLength) + "...";
        } else {
            return content;
        }
    }

    @Override
    public int getItemCount() {
        return mBlogPosts.size();
    }

    public interface OnItemClickListener {
        void onShareClick(int position);

        void onItemClick(int position);

        void onItemLongClick(int position);
    }

    // Filter logic
    @Override
    public Filter getFilter() {
        return blogPostFilter;
    }

    private final Filter blogPostFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<BlogPost> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                Log.d(TAG, "No search constraint, using full posts list with size: " + mBlogPostsFull.size());
                filteredList.addAll(mBlogPostsFull);
            } else {
                Log.d(TAG, "Performing search with constraint: " + constraint);
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (BlogPost item : mBlogPostsFull) {
                    if (item.getPostTitle().toLowerCase().contains(filterPattern) ||
                            item.getPostText().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            Log.d(TAG, "Filtering completed, found " + filteredList.size() + " results.");
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            Log.d(TAG, "Publishing results to adapter.");
            mBlogPosts.clear();
            mBlogPosts.addAll((List) results.values);
            notifyDataSetChanged();
            Log.d(TAG, "Adapter data set updated, now has " + mBlogPosts.size() + " items.");
        }

    };

    public class BlogPostViewHolder extends RecyclerView.ViewHolder {
        private TextView mUserName;
        private TextView mPostTitle;
        private TextView mPostText;
        private ImageView mPostImage;
        private ImageView mShareIcon;
        private ImageView mSyncStatusIcon;

        public BlogPostViewHolder(View itemView, final OnItemClickListener listener) {
            super(itemView);
            mUserName = itemView.findViewById(R.id.userName);
            mPostTitle = itemView.findViewById(R.id.postTitle);
            mPostText = itemView.findViewById(R.id.postText);
            mPostImage = itemView.findViewById(R.id.postImage);
            mShareIcon = itemView.findViewById(R.id.shareIcon);
            mSyncStatusIcon = itemView.findViewById(R.id.sync_status_icon);

            mShareIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onShareClick(position);
                    }
                }
            });

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            listener.onItemClick(position);
                        }
                    }
                }
            });

            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemLongClick(position);
                    }
                    return true;
                }
            });
        }
    }
}
