package demo.zkp.com.myapplication;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;

/**
 * Created by zkp on 2016/12/27.
 * Modified by zkp on 2018/08/11 修改问题，优化性能。
 */

public class RefreshLoadMoreLayout extends LinearLayout {

    boolean isIntercepted = false;
    private int mActivePointerId = INVALIDE_POINTER_ID;
    private float initialY = -1;
    private RelativeLayout mHeader, mFooter;
    private TextView mTvHeader, mTvFooter;
    private RecyclerView mRecyclerView;
    private float radio = 1f;
    private int maxDragDistanceDefault = 400;
    private int maxDragDistance;
    private int headerHeight;
    private int footerHeight;
    private Scroller mScroller;
    private float mPreY;
    private float curY;
    private Handler mHandler;
    private ProgressBar progressBarHeader, progressBarFooter;
    private ImageView ivHeader, ivFooter;
    private LinearLayout llChild;
    private boolean hasRefreshUpAnimPlayed = false;
    private boolean hasRefreshBackAnimPlayed = false;
    private boolean hasLoadMoreDownAnimPlayed = false;
    private boolean hasLoadMoreUpAnimPlayed = false;
    private boolean canLoadMore = true;

    private Status mState = Status.PULL_TO_REFRESH;

    public enum Status {
        PREPARE_TO_REFRESH, PULL_TO_REFRESH, REFRESHING, PREPARE_TO_LOADMORE, PULL_TO_LOADMORE, LOADINGMORE;
    }

    private static final int INVALIDE_POINTER_ID = -1;

    public interface OnRefreshLoadMore {
        void onRefresh();

        void onLoadMore();
    }

    private OnRefreshLoadMore iOnRefreshLoadMore;

    public void setOnRefreshLoadMoreListener(OnRefreshLoadMore iOnRefreshLoadMore) {
        this.iOnRefreshLoadMore = iOnRefreshLoadMore;
    }

    public RefreshLoadMoreLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        View.inflate(getContext(), R.layout.ll_header_footer, this);
        llChild = (LinearLayout) getChildAt(0);
        mScroller = new Scroller(context, new DecelerateInterpolator());
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.RefreshLoadMoreRecycerView);
        maxDragDistance = (int) ta.getDimension(R.styleable.RefreshLoadMoreRecycerView_maxDragDistance, Dp2Px(context, maxDragDistanceDefault));
        headerHeight = (int) ta.getDimension(R.styleable.RefreshLoadMoreRecycerView_headerHeight, Dp2Px(context, 50));
        footerHeight = (int) ta.getDimension(R.styleable.RefreshLoadMoreRecycerView_footerHeight, Dp2Px(context, 50));
        ta.recycle();
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                stopRefresh();
            }
        };
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        initHeaderView();
        mRecyclerView = (RecyclerView) getChildAt(1);
        this.removeViewAt(1);
        llChild.addView(mRecyclerView, 1);
        initFooterView();

        LayoutParams paramsRv = (LayoutParams) this.getChildAt(0).getLayoutParams();
        paramsRv.topMargin = -headerHeight;
        this.getChildAt(0).setLayoutParams(paramsRv);
    }

    /**
     * 初使化下拉刷布局
     */
    private void initHeaderView() {
        mHeader = (RelativeLayout) llChild.getChildAt(0);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mHeader.getLayoutParams();
        params.height = headerHeight;
        mHeader.setLayoutParams(params);

        mTvHeader = (TextView) mHeader.getChildAt(0);
        progressBarHeader = (ProgressBar) mHeader.findViewById(R.id.pb_view_head);
        mTvHeader.setText("下拉刷新");
        ivHeader = (ImageView) mHeader.findViewById(R.id.image_view_head);
        ivHeader.setVisibility(View.VISIBLE);
        ivHeader.setImageResource(R.drawable.down_arrow);
        progressBarHeader.setVisibility(View.GONE);
    }

    /**
     * 初使化上拉加载更多布局
     */
    private void initFooterView() {
        mFooter = (RelativeLayout) llChild.getChildAt(2);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mFooter.getLayoutParams();
        params.height = footerHeight;
        mFooter.setLayoutParams(params);
        mTvFooter = (TextView) mFooter.getChildAt(0);
        progressBarFooter = (ProgressBar) mFooter.findViewById(R.id.pb_view_foot);
        mTvFooter.setText("上拉加载更多");
        ivFooter = (ImageView) mFooter.findViewById(R.id.image_view_foot);
        ivFooter.setVisibility(View.VISIBLE);
        ivFooter.setImageResource(R.drawable.up_arrow);
        progressBarFooter.setVisibility(View.GONE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mState == Status.REFRESHING || mState == Status.LOADINGMORE) {
                    if (mActivePointerId == INVALIDE_POINTER_ID) {
                        mActivePointerId = MotionEventCompat.getPointerId(ev, ev.getActionIndex());
                    }
                    isIntercepted = true;//如果当前正在刷新中或者加载更多中，拦截Event
                }
                initialY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:

                if (mActivePointerId == INVALIDE_POINTER_ID) {
                    mActivePointerId = MotionEventCompat.getPointerId(ev, ev.getActionIndex());
                }
                int actionIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (actionIndex != -1) {
                    float nowY = MotionEventCompat.getY(ev, actionIndex);

                    if (!canRecycerViewScrollDown()) {//RecyclerView不能往下拉
                        //正在往下拉（或者正在下拉状态）或者正处于刷新状态，拦截Event
                        if (nowY - initialY >= 0 || mState == Status.REFRESHING) {
                            isIntercepted = true;
                        } else {
                            isIntercepted = false;
                        }

                    } else if (!canRecycerViewScrollUp() && canLoadMore) {//RecyclerView不能往上拉
                        //正在往上拉（或者正在上拉状态）或者正处于正在加载更多中状态，拦截Event
                        if (initialY - nowY >= 0 || mState == Status.LOADINGMORE) {
                            isIntercepted = true;
                        } else {
                            isIntercepted = false;
                        }
                    } else {
                        initialY = mPreY = ev.getY();
                    }
                }
                break;
        }
        //拦截的话，走自己的onTouchEvent，否则走默认的super.dispatchTouchEvent(ev);
        //此处在dispatchTouchEvent方法中判断是否拦截而不是放在OnInterceptTouchEvent方法中，是为了方便处理由拦截过渡成不拦截的情况，实现更好的效果
        return isIntercepted ? onTouchEvent(ev) : super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                int actionIndex = MotionEventCompat.findPointerIndex(event, mActivePointerId);
                initialY = mPreY = MotionEventCompat.getY(event, actionIndex);
                break;
            case MotionEvent.ACTION_MOVE:
                actionIndex = MotionEventCompat.findPointerIndex(event, mActivePointerId);
                if (actionIndex == -1) {
                    return true;
                }
                curY = MotionEventCompat.getY(event, actionIndex);
                if (mState != Status.REFRESHING && mState != Status.LOADINGMORE) {
                    int iDes = (int) ((initialY - curY) * radio);
                    if (iDes >= -maxDragDistance && iDes <= maxDragDistance) {//在可滑动的范围内
                        llChild.scrollTo(0, iDes);
                    } else {
                        //控制不超过可滑动的最大范围
                        if (iDes > 0) {
                            llChild.scrollTo(0, maxDragDistance);
                        } else {
                            llChild.scrollTo(0, -maxDragDistance);
                        }
                    }
                } else {
                    if (mState == Status.REFRESHING) {//处于正在刷新中状态
                        int dy = (int) ((mPreY - curY) * radio);
                        if (llChild.getScrollY() + dy <= 0) {//Head View处理可见状态
                            if (!canRecycerViewScrollDown()) {//当前RecyclerView不可往下滑动(第一个Item完全显示出来)
                                //控制可滑动的范围
                                if (llChild.getScrollY() + dy >= -maxDragDistance) {
                                    llChild.scrollBy(0, dy);
                                } else {
                                    llChild.scrollTo(0, -maxDragDistance);
                                }
                            } else {
                                //将事件派发给RecyclerView处理（让RecyclerView滑动）
                                dispatchEventToRecyclerView(event);
                            }
                        } else {
                            //让RecylcerView归位，并且将事件派发给RecyclerView处理（让RecyclerView滑动）
                            llChild.scrollTo(0, 0);
                            dispatchEventToRecyclerView(event);
                        }
                    } else if (mState == Status.LOADINGMORE) {//与上面类似
                        int dy = (int) ((mPreY - curY) * radio);
                        if (llChild.getScrollY() + dy >= 0) {
                            if (!canRecycerViewScrollUp()) {
                                if (llChild.getScrollY() + dy <= maxDragDistance) {
                                    llChild.scrollBy(0, dy);
                                } else {
                                    llChild.scrollTo(0, maxDragDistance);
                                }
                            } else {
                                dispatchEventToRecyclerView(event);
                            }
                        } else {
                            llChild.scrollTo(0, 0);
                            dispatchEventToRecyclerView(event);
                        }
                    }
                }
                if (mState != Status.REFRESHING && !canRecycerViewScrollDown()) {
                    if (-llChild.getScrollY() >= headerHeight) {//松开可刷新
                        mTvHeader.setText("松开刷新");
                        mState = Status.PREPARE_TO_REFRESH;
                        if (!hasRefreshUpAnimPlayed) {
                            playRefreshArrowUpAnimation();
                        }
                    } else {
                        mTvHeader.setText("下拉刷新");
                        mState = Status.PULL_TO_REFRESH;
                        playRefreshArrowDownAnimation();
                    }
                }
                if (mState != Status.LOADINGMORE && !canRecycerViewScrollUp() && canLoadMore) {
                    if (llChild.getScrollY() >= footerHeight) {//松开可加载更多
                        mTvFooter.setText("松开加载更多");
                        mState = Status.PREPARE_TO_LOADMORE;
                        if (!hasLoadMoreDownAnimPlayed) {
                            playLoadMoreArrowDownAnimation();
                        }
                    } else {
                        mTvFooter.setText("上拉加载更多");
                        mState = Status.PULL_TO_LOADMORE;
                        playLoadMoreArrowUpAnimation();
                    }
                }
                mPreY = curY;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                actionIndex = MotionEventCompat.findPointerIndex(event, mActivePointerId);
                if (actionIndex == -1) {
                    return true;
                }
                if (mState == Status.PREPARE_TO_REFRESH) {
                    smoothScrollToPositon(-headerHeight);
                    mTvHeader.setText("正在刷新...");
                    mState = Status.REFRESHING;
                    startRefresh();
                    ivHeader.clearAnimation();
                    ivHeader.setVisibility(GONE);
                    progressBarHeader.setVisibility(VISIBLE);
                } else if (mState == Status.PULL_TO_REFRESH) {
                    smoothScrollToPositon(0);
                } else if (mState == Status.REFRESHING) {
                    if (llChild.getScrollY() <= -headerHeight) {
                        smoothScrollToPositon(-headerHeight);
                    }
                }
                if (mState == Status.PREPARE_TO_LOADMORE) {
                    smoothScrollToPositon(footerHeight);
                    mTvFooter.setText("正在加载更多...");
                    mState = Status.LOADINGMORE;
                    startLoadMore();
                    ivFooter.clearAnimation();
                    ivFooter.setVisibility(GONE);
                    progressBarFooter.setVisibility(VISIBLE);
                } else if (mState == Status.PULL_TO_LOADMORE) {
                    smoothScrollToPositon(0);
                } else if (mState == Status.LOADINGMORE) {
                    if (llChild.getScrollY() >= footerHeight) {
                        smoothScrollToPositon(footerHeight);
                    }
                }
                isIntercepted = false;
                mPreY = -1;
                mActivePointerId = INVALIDE_POINTER_ID;
                break;
        }
        return true;
    }

    /**
     * 将事件派发给RecyclerView处理
     *
     * @param event
     */
    private void dispatchEventToRecyclerView(MotionEvent event) {
        MotionEvent newEvent = MotionEvent.obtain(event);
        newEvent.setAction(MotionEvent.ACTION_MOVE);
        mRecyclerView.dispatchTouchEvent(newEvent);
        initialY = event.getY();
    }

    private void playRefreshArrowDownAnimation() {
        if (!hasRefreshBackAnimPlayed) {
            RotateAnimation animation = new RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF,
                    0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            animation.setDuration(200);
            animation.setFillAfter(false);
            ivHeader.setAnimation(animation);
            animation.start();
            hasRefreshBackAnimPlayed = true;
            hasRefreshUpAnimPlayed = false;
        }
    }

    private void playRefreshArrowUpAnimation() {
        RotateAnimation animation = new RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF,
                0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(200);
        animation.setFillAfter(true);
        ivHeader.setAnimation(animation);
        animation.start();
        hasRefreshUpAnimPlayed = true;
        hasRefreshBackAnimPlayed = false;
    }

    private void playLoadMoreArrowDownAnimation() {
        if (!hasLoadMoreDownAnimPlayed) {
            RotateAnimation animation = new RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF,
                    0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            animation.setDuration(200);
            animation.setFillAfter(true);
            ivFooter.setAnimation(animation);
            animation.start();
            hasLoadMoreDownAnimPlayed = true;
            hasLoadMoreUpAnimPlayed = false;
        }
    }

    private void playLoadMoreArrowUpAnimation() {
        if (!hasLoadMoreUpAnimPlayed) {
            RotateAnimation animation = new RotateAnimation(180f, 360, Animation.RELATIVE_TO_SELF,
                    0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            animation.setDuration(200);
            animation.setFillAfter(false);
            ivFooter.setAnimation(animation);
            animation.start();
            hasLoadMoreUpAnimPlayed = true;
            hasLoadMoreDownAnimPlayed = false;
        }
    }

    private void startRefresh() {
        if (this.iOnRefreshLoadMore != null) {
            this.iOnRefreshLoadMore.onRefresh();
        } else {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopRefresh();
                }
            }, 3000);
        }
    }

    private void startLoadMore() {
        if (this.iOnRefreshLoadMore != null) {
            this.iOnRefreshLoadMore.onLoadMore();
        } else {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopLoadMore();
                }
            }, 3000);
        }
    }

    public void stopRefresh() {
        if (mState == Status.REFRESHING) {
            smoothScrollToPositon(0);
            mTvHeader.setText("下拉刷新");
            mState = Status.PULL_TO_REFRESH;
            ivHeader.setVisibility(VISIBLE);
            progressBarHeader.setVisibility(GONE);
            hasRefreshUpAnimPlayed = false;
            hasRefreshBackAnimPlayed = false;
            initialY = curY;
        }
    }

    public void stopLoadMore() {
        if (mState == Status.LOADINGMORE) {
            smoothScrollToPositon(0);
            mTvFooter.setText("上拉加载更多");
            mState = Status.PULL_TO_LOADMORE;
            ivFooter.setVisibility(VISIBLE);
            progressBarFooter.setVisibility(GONE);
            hasLoadMoreDownAnimPlayed = false;
            hasLoadMoreUpAnimPlayed = false;
            initialY = curY;
        }
    }

    public void canLoadMore(boolean can) {
        canLoadMore = can;
    }

    /**
     * 滑动到指定位置
     *
     * @param toY
     */
    private void smoothScrollToPositon(int toY) {
        mScroller.startScroll(0, llChild.getScrollY(), 0, toY - llChild.getScrollY());
        invalidate();
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            llChild.scrollTo(0, mScroller.getCurrY());
            invalidate();
        }
    }

    private boolean canRecycerViewScrollDown() {
        return ViewCompat.canScrollVertically(mRecyclerView, -1);
    }

    private boolean canRecycerViewScrollUp() {
        return ViewCompat.canScrollVertically(mRecyclerView, 1);
    }

    public int Dp2Px(Context context, float dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
