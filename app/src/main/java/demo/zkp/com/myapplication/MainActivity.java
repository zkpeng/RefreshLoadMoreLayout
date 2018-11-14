package demo.zkp.com.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

public class MainActivity extends AppCompatActivity implements RefreshLoadMoreLayout.OnRefreshLoadMore {

    private RefreshLoadMoreLayout refreshLoadMoreLayout;
    private RecyclerView recyclerView;
    private RecyclerViewAdapter recyclerViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        refreshLoadMoreLayout = (RefreshLoadMoreLayout) findViewById(R.id.refresh_view);
        refreshLoadMoreLayout.setOnRefreshLoadMoreListener(this);
        refreshLoadMoreLayout.setSupportRefresh(true);
        refreshLoadMoreLayout.setSupportLoadMore(true);

        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewAdapter = new RecyclerViewAdapter(this);
        recyclerView.setAdapter(recyclerViewAdapter);
    }

    /**
     * 刷新操作
     */
    @Override
    public void onRefresh() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                recyclerViewAdapter.refreshValues();
                refreshLoadMoreLayout.stopRefresh();
            }
        }, 2000);
    }

    /**
     * 加载更多操作
     */
    @Override
    public void onLoadMore() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                recyclerViewAdapter.insertIntems();
                refreshLoadMoreLayout.stopLoadMore();
            }
        }, 2000);
    }
}
