package demo.zkp.com.myapplication;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

public class MainActivity extends AppCompatActivity implements RefreshLoadMoreLayout.OnRefreshLoadMore {

    private RefreshLoadMoreLayout refreshableRecycerView;
    private RecyclerView recyclerView;
    private RecyclerViewAdapter recyclerViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        refreshableRecycerView = (RefreshLoadMoreLayout) findViewById(R.id.refresh_view);
        refreshableRecycerView.setOnRefreshLoadMoreListener(this);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewAdapter = new RecyclerViewAdapter(this);
        recyclerView.setAdapter(recyclerViewAdapter);
    }

    @Override
    public void onRefresh() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                recyclerViewAdapter.refreshValues();
                refreshableRecycerView.stopRefresh();
            }
        }, 2000);
    }

    @Override
    public void onLoadMore() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                recyclerViewAdapter.insertIntems();
                refreshableRecycerView.stopLoadMore();
            }
        }, 2000);
    }
}
