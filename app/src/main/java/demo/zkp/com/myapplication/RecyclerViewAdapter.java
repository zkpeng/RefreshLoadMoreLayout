package demo.zkp.com.myapplication;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by zkp on 2016/12/23.
 */

public class RecyclerViewAdapter extends RecyclerView.Adapter {

    private Context context;
    private String prefix = "item_";
    private int size = 15;

    public RecyclerViewAdapter(Context context) {
        this.context = context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item, null);
        return new PhotoHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {
        PhotoHolder h = (PhotoHolder) holder;
        h.textView.setText(prefix + position);
        h.textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(context, "click on position:" + position, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return size;
    }

    public static class PhotoHolder extends RecyclerView.ViewHolder {

        TextView textView;

        public PhotoHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.test);
        }
    }

    public void refreshValues() {
        prefix = "ITEM_";
        notifyDataSetChanged();
    }

    public void insertIntems() {
        size = size + 2;
        notifyItemRangeInserted(size - 2, 2);
    }
}
