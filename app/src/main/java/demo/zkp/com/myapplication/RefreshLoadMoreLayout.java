package demo.zkp.com.myapplication;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
 */

public class RefreshLoadMoreLayout extends LinearLayout {

    boolean isIntercepted = false;
    private int mActivePointerId = INVALIDE_POINTER_ID;
    private float initialY = -1;
    private RelativeLayout mHeader, mFooter;
    private TextView mTvHeader, mTvFooter;
    private RecyclerView mRecyclerView;
    private float radio = 1f;
    private int maxDragDistance;
    private Scroller mScroller;
    private float mPreY;
    private Handler mHandler;
    private ProgressBar progressBarHeader, progressBarFooter;
    private ImageView ivHeader, ivFooter;
    private boolean hasRefreshUpAnimPlayed = false;
    private boolean hasRefreshBackAnimPlayed = false;
    private boolean hasLoadMoreDownAnimPlayed = false;
    private boolean hasLoadMoreUpAnimPlayed = false;
    private boolean canLoadMore = true;

    private int mState = PULL_TO_REFRESH;
    private static final int PREPARE_TO_REFRESH = 0;
    private static final int PULL_TO_REFRESH = 1;
    private static final int REFRESHING = 2;
    private static final int PREPARE_TO_LOADMORE = 3;
    private static final int PULL_TO_LOADMORE = 4;
    private static final int LOADINGMORE = 5;

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
        maxDragDistance = Dp2Px(context, 400);
        mScroller = new Scroller(context, new DecelerateInterpolator());
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                stopRefresh();
            }
        };
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.RefreshLoadMoreRecycerView);
        maxDragDistance = (int) ta.getDimension(R.styleable.RefreshLoadMoreRecycerView_maxDragDistance, maxDragDistance);
        ta.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        initHeaderView();
        mRecyclerView = (RecyclerView) getChildAt(1);
        initFooterView();
    }

    private void initHeaderView() {
        mHeader = (RelativeLayout) getChildAt(0);
        mTvHeader = (TextView) mHeader.getChildAt(0);
        progressBarHeader = (ProgressBar) mHeader.findViewById(R.id.pb_view_head);
        mTvHeader.setText("下拉刷新");
        ivHeader = (ImageView) mHeader.findViewById(R.id.image_view_head);
        ivHeader.setVisibility(View.VISIBLE);
        ivHeader.setImageResource(R.drawable.down_arrow);
        progressBarHeader.setVisibility(View.GONE);
    }

    private void initFooterView() {
        mFooter = (RelativeLayout) getChildAt(2);
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
                if (mActivePointerId == INVALIDE_POINTER_ID) {
                    mPreY = ev.getY();
                    mActivePointerId = MotionEventCompat.getPointerId(ev, ev.getActionIndex());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALIDE_POINTER_ID) {
                    mActivePointerId = MotionEventCompat.getPointerId(ev, ev.getActionIndex());
                }
                int actionIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                if (actionIndex != -1) {
                    float nowY = MotionEventCompat.getY(ev, actionIndex);
                    if (!canRecycerViewScrollDown() || !canRecycerViewScrollUp()) {
                        if (!canRecycerViewScrollDown()) {
                            if (nowY - initialY >= 0 || mState == REFRESHING) {
                                isIntercepted = true;
                                if (initialY == -1) {
                                    initialY = MotionEventCompat.getY(ev, actionIndex);
                                }
                            } else {
                                isIntercepted = false;
                                initialY = -1;
                            }
                        }
                        if (!canRecycerViewScrollUp() && canLoadMore) {
                            if (initialY == -1) {
                                initialY = MotionEventCompat.getY(ev, actionIndex);
                            }
                            if (initialY - nowY >= 0 || mState == LOADINGMORE) {
                                isIntercepted = true;

                            } else {
                                isIntercepted = false;
                                initialY = -1;
                            }
                        }
                    } else {
                        initialY = mPreY = ev.getY();
                    }
                }
                break;
        }
        return isIntercepted ? onTouchEvent(ev) : super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                int actionIndex = MotionEventCompat.findPointerIndex(event, mActivePointerId);
                initialY = MotionEventCompat.getY(event, actionIndex);
                break;
            case MotionEvent.ACTION_MOVE:
                actionIndex = MotionEventCompat.findPointerIndex(event, mActivePointerId);
                if (actionIndex == -1) {
                    return true;
                }
                float nowY = MotionEventCompat.getY(event, actionIndex);
                if (mState != REFRESHING && mState != LOADINGMORE) {
                    int iDes = (int) ((initialY - nowY) * radio);
                    if (iDes >= -maxDragDistance && iDes <= maxDragDistance) {
                        this.scrollTo(0, iDes);
                    } else {
                        if (iDes > 0) {
                            this.scrollTo(0, maxDragDistance);
                        } else {
                            this.scrollTo(0, -maxDragDistance);
                        }
                    }
                } else {
                    if (mState == REFRESHING) {
                        int dy = (int) ((mPreY - nowY) * radio);
                        if (getScrollY() + dy <= 0) {
                            if (!canRecycerViewScrollDown()) {
                                if (getScrollY() + dy >= -maxDragDistance) {
                                    this.scrollBy(0, dy);
                                } else {
                                    this.scrollTo(0, -maxDragDistance);
                                }
                            } else {
                                dispatchEventToRecyclerView(event);
                            }
                        } else {
                            this.scrollTo(0, 0);
                            dispatchEventToRecyclerView(event);
                        }
                    } else if (mState == LOADINGMORE) {
                        int dy = (int) ((mPreY - nowY) * radio);
                        if (getScrollY() + dy >= 0) {
                            if (!canRecycerViewScrollUp()) {
                                if (getScrollY() + dy <= maxDragDistance) {
                                    this.scrollBy(0, dy);
                                } else {
                                    this.scrollTo(0, maxDragDistance);
                                }
                            } else {
                                dispatchEventToRecyclerView(event);
                            }
                        } else {
                            this.scrollTo(0, 0);
                            dispatchEventToRecyclerView(event);
                        }
                    }
                }
                if (mState != REFRESHING && !canRecycerViewScrollDown()) {
                    if (-getScrollY() >= Dp2Px(getContext(), 50)) {
                        mTvHeader.setText("松开刷新");
                        mState = PREPARE_TO_REFRESH;
                        if (!hasRefreshUpAnimPlayed) {
                            playRefreshArrowUpAnimation();
                        }
                    } else {
                        mTvHeader.setText("下拉刷新");
                        mState = PULL_TO_REFRESH;
                        playRefreshArrowDownAnimation();
                    }
                }
                if (mState != LOADINGMORE && !canRecycerViewScrollUp() && canLoadMore) {
                    if (getScrollY() >= Dp2Px(getContext(), 50)) {
                        mTvFooter.setText("松开加载更多");
                        mState = PREPARE_TO_LOADMORE;
                        if (!hasLoadMoreDownAnimPlayed) {
                            playLoadMoreArrowDownAnimation();
                        }
                    } else {
                        mTvFooter.setText("上拉加载更多");
                        mState = PULL_TO_LOADMORE;
                        playLoadMoreArrowUpAnimation();
                    }
                }
                mPreY = nowY;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                actionIndex = MotionEventCompat.findPointerIndex(event, mActivePointerId);
                if (actionIndex == -1) {
                    return true;
                }
                if (mState == PREPARE_TO_REFRESH) {
                    smoothScrollToPositon(-Dp2Px(getContext(), 50));
                    mTvHeader.setText("正在刷新...");
                    mState = REFRESHING;
                    startRefresh();
                    ivHeader.clearAnimation();
                    ivHeader.setVisibility(GONE);
                    progressBarHeader.setVisibility(VISIBLE);
                } else if (mState == PULL_TO_REFRESH) {
                    smoothScrollToPositon(0);
                } else if (mState == REFRESHING) {
                    if (getScrollY() <= -Dp2Px(getContext(), 50)) {
                        smoothScrollToPositon(-Dp2Px(getContext(), 50));
                    }
                }
                if (mState == PREPARE_TO_LOADMORE) {
                    smoothScrollToPositon(Dp2Px(getContext(), 50));
                    mTvFooter.setText("正在加载更多...");
                    mState = LOADINGMORE;
                    startLoadMore();
                    ivFooter.clearAnimation();
                    ivFooter.setVisibility(GONE);
                    progressBarFooter.setVisibility(VISIBLE);
                } else if (mState == PULL_TO_LOADMORE) {
                    smoothScrollToPositon(0);
                } else if (mState == LOADINGMORE) {
                    if (getScrollY() >= Dp2Px(getContext(), 50)) {
                        smoothScrollToPositon(Dp2Px(getContext(), 50));
                    }
                }
                isIntercepted = false;
                initialY = -1;
                mPreY = -1;
                mActivePointerId = INVALIDE_POINTER_ID;
                break;
        }
        return true;
    }

    private void dispatchEventToRecyclerView(MotionEvent event) {
        MotionEvent newEvent = MotionEvent.obtain(event);
        newEvent.setAction(MotionEvent.ACTION_MOVE);
        mRecyclerView.dispatchTouchEvent(newEvent);
        initialY = event.getY();
    }

    private void playRefreshArrowDownAnimation() {
//        if (event.getY() < mPreY) {
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
//        }
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
        if (mState == REFRESHING) {
            smoothScrollToPositon(0);
            mTvHeader.setText("下拉刷新");
            mState = PULL_TO_REFRESH;
            ivHeader.setVisibility(VISIBLE);
            progressBarHeader.setVisibility(GONE);
            initialY = -1;
            hasRefreshUpAnimPlayed = false;
            hasRefreshBackAnimPlayed = false;
        }
    }

    public void stopLoadMore() {
        if (mState == LOADINGMORE) {
            smoothScrollToPositon(0);
            mTvFooter.setText("上拉加载更多");
            mState = PULL_TO_LOADMORE;
            ivFooter.setVisibility(VISIBLE);
            progressBarFooter.setVisibility(GONE);
            initialY = -1;
            hasLoadMoreDownAnimPlayed = false;
            hasLoadMoreUpAnimPlayed = false;
        }
    }

    public void canLoadMore(boolean can) {
        canLoadMore = can;
    }

    private void smoothScrollToPositon(int toY) {
        mScroller.startScroll(0, getScrollY(), 0, toY - getScrollY());
        invalidate();
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            this.scrollTo(0, mScroller.getCurrY());
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
