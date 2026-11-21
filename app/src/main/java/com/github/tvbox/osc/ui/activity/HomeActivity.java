package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.NumericPasswordDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.EmptyCategoryStore;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.ProtectedZoneHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity {
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvDate;
    private TextView tvName;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private View currentView;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private String loadingSourceKey;
    private String previousHomeName;
    private SourceBean previousHomeSource;
    private boolean homeSortLoading = false;
    private boolean refreshHomeRec = false;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    private boolean eventBusRegistered = false;
    // 需要密码才能进入的分类（按分类名关键字判断）；解锁状态按关键字记在进程内，
    // 切换首页数据源会重建 Activity，所以必须是 static 才不会重新弹密码
    private static final String[] PROTECTED_CATEGORY_KEYWORDS = {"伦理"};
    private static final String PROTECTED_CATEGORY_PASSWORD = "104";
    /** 需要额外密码才能切换的仓库/数据源名字关键字（多仓时的「国外频道」） */
    private static final String[] PROTECTED_SOURCE_KEYWORDS = {"国外频道"};
    /** 切换数据源时左上角显示的提示文字（闪烁） */
    private static final String SWITCHING_TEXT = "切换中...";
    /** 冷启动/首次加载时左上角显示的提示文字（闪烁），区别于「切换中」 */
    private static final String LOADING_TEXT = "加载中...";
    private static final Set<String> UNLOCKED_PROTECTED_KEYWORDS = new HashSet<>();
    // 当前正在显示的分类密码框；用引用而不是布尔标记，避免对话框被系统回收后标记卡在 true
    private NumericPasswordDialog categoryPasswordDialog = null;
    // 熄屏/锁屏后回来，伦理密码要重新输入
    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                lockProtectedCategories();
            }
        }
    };
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            Date date = new Date();
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy/MM/dd  E  HH:mm", Locale.CHINA);
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };
    private final Runnable refreshTopInfoTextSizeRunnable = new Runnable() {
        @Override
        public void run() {
            refreshTopInfoTextSize();
        }
    };
    private final Runnable refreshTopLayoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (topLayout == null || isActivityUnavailable() || currentSelected != 0 || topHide != 0) {
                return;
            }
            // OnePlus devices may finish applying immersive mode after the first measure.
            // Re-apply the visible top state once the final display metrics are available.
            hideSysBar();
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams();
            params.topMargin = AutoSizeUtils.mm2px(HomeActivity.this, 10.0f);
            params.height = AutoSizeUtils.mm2px(HomeActivity.this, 50.0f);
            topLayout.setLayoutParams(params);
            topLayout.setAlpha(1.0f);
            refreshTopInfoTextSize();
            topLayout.requestLayout();
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    boolean useCacheConfig = false;

    @Override
    protected void init() {
        // 每次进入首页（含重启 App、切换首页数据源后重建界面）都要重新输密码
        UNLOCKED_PROTECTED_KEYWORDS.clear();
        categoryPasswordDialog = null;
        try {
            unregisterReceiver(screenOffReceiver);
        } catch (Throwable ignored) {
        }
        try {
            registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        } catch (Throwable ignored) {
        }
        EventBus.getDefault().register(this);
        eventBusRegistered = true;
        ControlManager.get().startServer();
        initView();
        initViewModel();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        // 多仓时冷启动默认把仓切回「极速频道」（必须在加载配置之前）
        applyStartupWarehouse();
        initData();
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvDate = findViewById(R.id.tvDate);
        this.tvName = findViewById(R.id.tvName);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    View firstChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (firstChild != null) {
                        mGridView.setSelectedPosition(0);
                        firstChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = view.findViewById(R.id.tvTitle);
                            textView.getPaint().setFakeBoldText(false);
                            if (sortFocused == p) {
                                view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                            } else {
                                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_BBFFFFFF));
                                view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                                view.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
                            }
                            textView.invalidate();
                        }

                        public final int p = position;
                    }, 10);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    HomeActivity.this.currentView = view;
                    HomeActivity.this.isDownOrUp = false;
                    HomeActivity.this.sortChange = true;
                    view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (!sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    HomeActivity.this.sortFocusView = view;
                    HomeActivity.this.sortFocused = position;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if ((baseLazyFragment instanceof GridFragment) && !sortAdapter.getItem(position).filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    } else if (baseLazyFragment instanceof UserFragment) {
                        showSiteSwitch();
                    }
                }
            }
        });

        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                if (direction == View.FOCUS_UP) {
                    BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                    if (baseLazyFragment instanceof UserFragment) {
                        refreshHomeSort();
                        return true;
                    }
                    if (baseLazyFragment instanceof GridFragment) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                        return true;
                    }
                }
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                return !((GridFragment) baseLazyFragment).isLoad();
            }
        });
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if(dataInitOk && jarInitOk){
                    String jar=ApiConfig.get().getHomeSourceBean().getJar();
                    String jarUrl=!jar.isEmpty()?jar:ApiConfig.get().getSpider();
                    String jarSource = jarUrl.split(";md5;")[0];
                    File cspCacheDir = new File(FileUtils.getFilePath() + "/csp/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarFullCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarUrl) + ".jar");
                    Toast.makeText(mContext, "缓存已清除", Toast.LENGTH_LONG).show();
                    new Thread(() -> {
                        try {
                            FileUtils.deleteFile(cspCacheDir);
                            FileUtils.deleteFile(jarCacheDir);
                            FileUtils.deleteFile(jarFullCacheDir);
                            FileUtils.clearSpiderCacheFiles();
                            ApiConfig.get().clearSpiderCache();
                            refreshHome();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();

                }else {
                    jumpActivity(SettingActivity.class);
                }
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                jumpActivity(SettingActivity.class);
                return true;
            }
        });
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 500);
    }


    private boolean skipNextUpdate = false;

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                if (!homeSortLoading && loadingSourceKey == null) {
                    return;
                }
                if (absXml != null && absXml.sourceKey != null && loadingSourceKey != null && !loadingSourceKey.equals(absXml.sourceKey)) {
                    return;
                }
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                mHandler.removeCallbacks(sourceSwitchWatchdog);
                showSuccess();
                clearHomePages();
                List<MovieSort.SortData> newSortData;
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    newSortData = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true);
                } else {
                    newSortData = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true);
                }
                // 这个数据源一个栏目都没有（有些源本身是空的），自动换到下一个能用的
                if (newSortData.size() <= 1 && previousHomeSource != null) {
                    String failedKey = loadingSourceKey;
                    if (failedKey == null && home != null) {
                        failedKey = home.getKey();
                    }
                    skipBrokenSource(failedKey);
                    return;
                }
                if (home != null && home.getKey() != null) {
                    clearDeadHomeSource(home.getKey());
                }
                // 之前已经确认是空栏目的，加载 tab 之前就直接不给它位置（这次不再点一下才消失）
                List<MovieSort.SortData> visibleSortData = EmptyCategoryStore.removeKnownEmpty(
                        home != null ? home.getKey() : null, newSortData);
                // 伦理这类保护栏目，在塞进 adapter 之前就先排到最后，保证 tab 和页面顺序一致
                moveProtectedToEnd(visibleSortData);
                updateSortData(visibleSortData);
                initViewPager(absXml);
                updateHomeRec(absXml);
                if (home != null && home.getName() != null && !home.getName().isEmpty()) tvName.setText(home.getName());
                tvName.clearAnimation();
                homeSortLoading = false;
                loadingSourceKey = null;
                previousHomeName = null;
                previousHomeSource = null;
            }
        });
    }

    private boolean dataInitOk = false;
    private boolean jarInitOk = false;
    private TipDialog mConfigErrorDialog;

    private void initData() {
        if (dataInitOk && jarInitOk) {
            // 每次冷启动 App，首页数据源都回到「极速频道」
            applyStartupDefaultSource();
            loadHomeSort(false);
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            if (!useCacheConfig && Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) {
                jumpActivity(LivePlayActivity.class);
            }
            return;
        }
        tvNameAnimation(LOADING_TEXT);
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
//                                if (!useCacheConfig) Toast.makeText(HomeActivity.this, "自定义jar加载成功", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        }, 50);
                    }

                    @Override
                    public void notice(String msg) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        dataInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HomeActivity.this, msg+" jar load err", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        },50);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            @Override
            public void notice(String msg) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) {
                            return;
                        }
                        if (mConfigErrorDialog == null)
                            mConfigErrorDialog = new TipDialog(HomeActivity.this, msg, "重试", "取消", new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }
                            });
                        if (!mConfigErrorDialog.isShowing())
                            mConfigErrorDialog.show();
                    }
                });
            }
        }, this);
    }

    private static final long SOURCE_SWITCH_TIMEOUT_MS = 12000L;
    private static final String DEAD_HOME_SOURCE_KEY = "home_dead_source_map";
    private static final long DEAD_HOME_SOURCE_TTL_MS = 6L * 60 * 60 * 1000;
    /** 冷启动默认切到这个数据源 */
    private static final String STARTUP_DEFAULT_SOURCE_KEYWORD = "极速频道";
    private static boolean startupSourceApplied = false;
    private static boolean startupWarehouseApplied = false;

    /** 切换数据源卡住超过 12 秒，就当成这个源是坏的，自动换下一个 */
    private final Runnable sourceSwitchWatchdog = new Runnable() {
        @Override
        public void run() {
            if (!homeSortLoading) {
                return;
            }
            String key = loadingSourceKey;
            if (key == null) {
                return;
            }
            markDeadHomeSource(key);
            skipBrokenSource(key);
        }
    };

    private org.json.JSONObject loadDeadHomeSourceMap() {
        try {
            return new org.json.JSONObject(Hawk.get(DEAD_HOME_SOURCE_KEY, "{}"));
        } catch (Throwable ignored) {
            return new org.json.JSONObject();
        }
    }

    private boolean isDeadHomeSource(String key) {
        if (key == null) {
            return false;
        }
        try {
            org.json.JSONObject map = loadDeadHomeSourceMap();
            long at = map.optLong(key, 0L);
            if (at <= 0L) {
                return false;
            }
            if (System.currentTimeMillis() - at > DEAD_HOME_SOURCE_TTL_MS) {
                map.remove(key);
                Hawk.put(DEAD_HOME_SOURCE_KEY, map.toString());
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void markDeadHomeSource(String key) {
        if (key == null) {
            return;
        }
        try {
            org.json.JSONObject map = loadDeadHomeSourceMap();
            map.put(key, System.currentTimeMillis());
            Hawk.put(DEAD_HOME_SOURCE_KEY, map.toString());
        } catch (Throwable ignored) {
        }
    }

    private void clearDeadHomeSource(String key) {
        if (key == null) {
            return;
        }
        try {
            org.json.JSONObject map = loadDeadHomeSourceMap();
            if (map.has(key)) {
                map.remove(key);
                Hawk.put(DEAD_HOME_SOURCE_KEY, map.toString());
            }
        } catch (Throwable ignored) {
        }
    }

    /** 目标源打不开/没有栏目时，自动顺着列表找下一个能用的源 */
    private void skipBrokenSource(String failedKey) {
        mHandler.removeCallbacks(sourceSwitchWatchdog);
        homeSortLoading = false;
        loadingSourceKey = null;
        markDeadHomeSource(failedKey);
        SourceBean previous = previousHomeSource;
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        SourceBean current = ApiConfig.get().getHomeSourceBean();
        if (sites == null || sites.size() <= 1 || current == null) {
            // 只有一个数据源，没得替补，退回原来的
            Toast.makeText(HomeActivity.this, "该数据源暂无内容，已退回原数据源", Toast.LENGTH_SHORT).show();
            cancelHomeSortLoading();
            return;
        }
        int index = 0;
        for (int i = 0; i < sites.size(); i++) {
            SourceBean bean = sites.get(i);
            if (bean != null && bean.getKey() != null && bean.getKey().equals(current.getKey())) {
                index = i;
                break;
            }
        }
        for (int step = 1; step <= sites.size(); step++) {
            SourceBean next = sites.get((index + step) % sites.size());
            if (next == null || next.getKey() == null) {
                continue;
            }
            if (failedKey != null && next.getKey().equals(failedKey)) {
                continue;
            }
            if (isDeadHomeSource(next.getKey())) {
                continue;
            }
            if (needPasswordForSource(next)) {
                // 要密码的源不替用户自动跳
                continue;
            }
            String nextName = next.getName() == null ? "下一个数据源" : next.getName();
            Toast.makeText(HomeActivity.this, "该数据源暂无内容，已切到「" + nextName + "」", Toast.LENGTH_SHORT).show();
            previousHomeSource = previous;
            ApiConfig.get().setSourceBean(next);
            refreshHome(false);
            return;
        }
        cancelHomeSortLoading();
    }

    /** 冷启动（App 重新打开）默认切回「极速频道」 */
    private void applyStartupDefaultSource() {
        if (startupSourceApplied) {
            return;
        }
        startupSourceApplied = true;
        try {
            List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
            if (sites == null || sites.isEmpty()) {
                return;
            }
            SourceBean current = ApiConfig.get().getHomeSourceBean();
            for (SourceBean bean : sites) {
                if (bean == null || bean.getName() == null || bean.getKey() == null) {
                    continue;
                }
                if (!bean.getName().contains(STARTUP_DEFAULT_SOURCE_KEYWORD)) {
                    continue;
                }
                if (current != null && bean.getKey().equals(current.getKey())) {
                    return;
                }
                ApiConfig.get().setSourceBean(bean);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 多仓情况下，冷启动（App 重新打开）默认把线路切回「极速频道」那个仓。
     * 必须在加载配置之前调用，否则新仓的配置不会被读取。
     */
    private void applyStartupWarehouse() {
        if (startupWarehouseApplied) {
            return;
        }
        startupWarehouseApplied = true;
        try {
            ArrayList<String> lines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
            if (lines == null || lines.size() <= 1) {
                // 单仓，不需要切
                return;
            }
            String current = Hawk.get(HawkConfig.API_URL, "");
            for (String line : lines) {
                String name = HistoryHelper.getApiLineName(line);
                if (name == null || !name.contains(STARTUP_DEFAULT_SOURCE_KEYWORD)) {
                    continue;
                }
                String url = HistoryHelper.getApiLineUrl(line);
                if (url == null || url.isEmpty()) {
                    return;
                }
                if (url.equals(current)) {
                    return;
                }
                Hawk.put(HawkConfig.API_URL, url);
                Hawk.put(HawkConfig.LIVE_API_URL, url);
                HistoryHelper.setLiveApiHistory(url);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadHomeSort(boolean keepCurrentContent) {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        homeSortLoading = keepCurrentContent;
        if (keepCurrentContent && home != null && home.getName() != null && !home.getName().isEmpty()) {
            previousHomeName = tvName.getText() == null ? null : tvName.getText().toString();
        }
        // 只有「换片源」这种真正在切数据源时才提示切换中；冷启动/首次加载提示加载中
        boolean switching = keepCurrentContent && previousHomeSource != null;
        tvNameAnimation(switching ? SWITCHING_TEXT : LOADING_TEXT);
        if (home == null) {
            loadingSourceKey = null;
            if (!keepCurrentContent) showLoading();
            sourceViewModel.getSort(null);
            return;
        }
        loadingSourceKey = home.getKey();
        if (!keepCurrentContent) {
            showLoading();
        }
        if (switching) {
            // 切换数据源：跳过「首页推荐」那次额外请求，并把超时看门狗挂上
            mHandler.removeCallbacks(sourceSwitchWatchdog);
            mHandler.postDelayed(sourceSwitchWatchdog, SOURCE_SWITCH_TIMEOUT_MS);
            sourceViewModel.getSortSkipHomeRec(loadingSourceKey);
        } else {
            sourceViewModel.getSort(loadingSourceKey);
        }
    }

    private void initViewPager(AbsSortXml absXml) {
        moveProtectedCategoriesToEnd();
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                        fragments.add(UserFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(UserFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
        }
    }

    private void clearHomePages() {
        mHandler.removeCallbacks(mDataRunnable);
        currentSelected = 0;
        sortFocused = 0;
        sortChange = false;
        sortFocusView = null;
        currentView = null;
        if (pageAdapter != null) {
            mViewPager.setAdapter(null);
            pageAdapter.removeAll();
            pageAdapter = null;
        } else if (!fragments.isEmpty()) {
            fragments.clear();
        }
    }

    /**
     * 把需要密码的分类（伦理）统一挪到最后一个，避免它出现在中间位置被顺手点开。
     * must 在创建 fragments 之前调用，保证 tab 顺序和 pager 页面顺序一致。
     */
    private void moveProtectedCategoriesToEnd() {
        if (sortAdapter == null) {
            return;
        }
        List<MovieSort.SortData> data = sortAdapter.getData();
        if (moveProtectedToEnd(data)) {
            sortAdapter.notifyDataSetChanged();
        }
    }

    /** 就地调整列表，把保护分类挪到最后；返回是否真的移动过 */
    private boolean moveProtectedToEnd(List<MovieSort.SortData> data) {
        if (data == null || data.size() < 2) {
            return false;
        }
        List<MovieSort.SortData> protectedItems = new ArrayList<>();
        for (int i = data.size() - 1; i >= 1; i--) { // 第 0 个是首页/推荐，不参与移动
            MovieSort.SortData item = data.get(i);
            if (item != null && matchedProtectedKeyword(item.name) != null) {
                protectedItems.add(0, data.remove(i));
            }
        }
        if (protectedItems.isEmpty()) {
            return false;
        }
        data.addAll(protectedItems);
        return true;
    }

    private void updateSortData(List<MovieSort.SortData> newSortData) {
        if (newSortData == null) {
            newSortData = new ArrayList<>();
        }
        List<MovieSort.SortData> oldSortData = sortAdapter.getData();
        if (oldSortData.isEmpty()
                || newSortData.isEmpty()
                || oldSortData.get(0) == null
                || newSortData.get(0) == null
                || !"my0".equals(oldSortData.get(0).id)
                || !"my0".equals(newSortData.get(0).id)) {
            sortAdapter.setNewData(newSortData);
            return;
        }
        int oldTailCount = oldSortData.size() - 1;
        if (oldTailCount > 0) {
            oldSortData.subList(1, oldSortData.size()).clear();
            sortAdapter.notifyItemRangeRemoved(1, oldTailCount);
        }
        if (newSortData.size() > 1) {
            oldSortData.addAll(newSortData.subList(1, newSortData.size()));
            sortAdapter.notifyItemRangeInserted(1, newSortData.size() - 1);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBackPressed() {
        // 打断加载
        if (homeSortLoading) {
            cancelHomeSortLoading();
            return;
        }
        if (isLoading()) {
            refreshEmpty();
            return;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return;
        }

        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            doExit();
            return;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.setSelection(0);
            } else {
                doExit();
            }
        } else if (baseLazyFragment instanceof UserFragment && UserFragment.tvHotList.canScrollVertically(-1)) {
            // 如果 UserFragment 列表可以向上滚动，则滚动到顶部
            UserFragment.tvHotList.scrollToPosition(0);
            this.mGridView.setSelection(0);
        } else {
            doExit();
        }
    }

    private void doExit() {
        // 如果两次返回间隔小于 2000 毫秒，则退出应用
        if (System.currentTimeMillis() - mExitTime < 2000) {
            AppManager.getInstance().finishAllActivity();
            unregisterEventBus();
            ControlManager.get().stopServer();
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } else {
            // 否则仅提示用户，再按一次退出应用
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTopInfoTextSize();
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable);
        mHandler.postDelayed(refreshTopInfoTextSizeRunnable, 350);
        mHandler.removeCallbacks(refreshTopLayoutRunnable);
        mHandler.postDelayed(refreshTopLayoutRunnable, 450);
        mHandler.post(mRunnable);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable);
        mHandler.removeCallbacks(refreshTopLayoutRunnable);
        mHandler.removeCallbacks(mRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 熄屏/待机后回来（部分机型不发 SCREEN_OFF 广播），这里再兜一层
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                return;
            }
            boolean screenOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
                    ? pm.isInteractive() : pm.isScreenOn();
            if (!screenOn) {
                lockProtectedCategories();
            }
        } catch (Throwable ignored) {
        }
    }

    private void refreshTopInfoTextSize() {
        if (tvName == null || tvDate == null) {
            return;
        }
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_30));
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_26));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
                showFilterIcon((int) event.obj);
            }
        } else if (event.type == RefreshEvent.TYPE_HOME_SOURCE_CHANGE) {
            // 换了首页站源/点播源，密码重新输
            lockProtectedCategories();
            refreshHome(false);
        } else if (event.type == RefreshEvent.TYPE_CATEGORY_EMPTY) {
            hideEmptyCategory((String) event.obj);
        }
    }

    /**
     * 某个分类加载完是空的，就把首页对应的分类标签隐藏，避免用户点进去看「没找到数据」。
     */
    private void hideEmptyCategory(String sortId) {
        if (sortId == null || sortId.isEmpty() || sortAdapter == null) {
            return;
        }
        // 记下来：下次加载这个数据源时直接不给这个栏目位置
        SourceBean homeSource = ApiConfig.get().getHomeSourceBean();
        if (homeSource != null && homeSource.getKey() != null) {
            EmptyCategoryStore.mark(homeSource.getKey(), sortId);
        }
        List<MovieSort.SortData> data = sortAdapter.getData();
        for (int i = 1; i < data.size(); i++) { // 第 0 个一般是首页/推荐，保留
            MovieSort.SortData sortData = data.get(i);
            if (sortData == null || sortData.hidden || !sortId.equals(sortData.id)) {
                continue;
            }
            sortData.hidden = true;
            sortAdapter.notifyItemChanged(i);
            // 明确告诉用户「为什么点一下就没了」，否则会觉得莫名其妙
            String shownName = sortData.name == null || sortData.name.isEmpty() ? "该栏目" : sortData.name;
            Toast.makeText(HomeActivity.this, "「" + shownName + "」无片源，已隐藏", Toast.LENGTH_SHORT).show();
            // 如果用户正停在这个空栏目上，退回第一个栏目，避免停在一个「没找到数据」的空页
            if (i == currentSelected && currentSelected != 0) {
                currentSelected = 0;
                sortFocused = 0;
                mViewPager.setCurrentItem(0, false);
                changeTop(false);
                if (mGridView != null) {
                    mGridView.setSelectedPosition(0);
                }
            }
            return;
        }
    }

    private boolean isProtectedCategory(String name) {
        return matchedProtectedKeyword(name) != null;
    }

    /** 命中的保护关键字（如「伦理」，简繁/带空格都认），没命中返回 null */
    private String matchedProtectedKeyword(String name) {
        return ProtectedZoneHelper.matchedProtectedCategoryKeyword(name);
    }

    /**
     * 进入分类前的密码校验。返回 true 表示允许进入。
     * 解锁状态按「关键字」记，切了首页数据源后同一个「伦理」栏目不用重新输密码。
     */
    private boolean canEnterCategory(int position) {
        if (sortAdapter == null || position < 0 || position >= sortAdapter.getData().size()) {
            return true;
        }
        MovieSort.SortData sortData = sortAdapter.getData().get(position);
        if (sortData == null) {
            return true;
        }
        String keyword = matchedProtectedKeyword(sortData.name);
        if (keyword == null) {
            return true;
        }
        if (UNLOCKED_PROTECTED_KEYWORDS.contains(keyword)) {
            return true;
        }
        if (isCategoryPasswordShowing()) {
            // 已经在弹密码框了，别重复弹
            return false;
        }
        showCategoryPasswordDialog(sortData, keyword);
        return false;
    }

    private boolean isCategoryPasswordShowing() {
        return categoryPasswordDialog != null && categoryPasswordDialog.isShowing();
    }

    /** 关掉可能还挂着的密码框（锁屏、切源、页面销毁时用），并把标记一起复位 */
    private void dismissCategoryPasswordDialog() {
        NumericPasswordDialog dialog = categoryPasswordDialog;
        categoryPasswordDialog = null;
        if (dialog != null) {
            try {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 熄屏/锁屏/重新进 App 时的锁定动作：清掉解锁标记，并把当前停在伦理页的界面退回第一个栏目，
     * 保证回到前台时伦理内容不会直接显示出来。
     */
    private void lockProtectedCategories() {
        UNLOCKED_PROTECTED_KEYWORDS.clear();
        // 成人关键词的搜索解锁也一起清掉：熄屏回来、换源后要重新输密码
        ProtectedZoneHelper.lockAdultQueries();
        dismissCategoryPasswordDialog();
        try {
            if (sortAdapter == null || currentSelected <= 0) {
                return;
            }
            List<MovieSort.SortData> data = sortAdapter.getData();
            if (currentSelected >= data.size()) {
                return;
            }
            if (matchedProtectedKeyword(data.get(currentSelected).name) == null) {
                return;
            }
            sortChange = false;
            currentSelected = 0;
            sortFocused = 0;
            if (mViewPager != null) {
                mViewPager.setCurrentItem(0, false);
            }
            changeTop(false);
            if (mGridView != null) {
                mGridView.setSelectedPosition(0);
            }
        } catch (Throwable ignored) {
        }
    }

    private void showCategoryPasswordDialog(final MovieSort.SortData sortData, final String keyword) {
        if (isActivityUnavailable()) {
            return;
        }
        // 防御：先清掉残留的旧对话框，避免「点栏目只选中、密码框不弹」
        dismissCategoryPasswordDialog();
        NumericPasswordDialog dialog = new NumericPasswordDialog(this, sortData.name + " · 需要密码");
        dialog.setOnListener(new NumericPasswordDialog.OnListener() {
            @Override
            public boolean onSubmit(String password) {
                if (PROTECTED_CATEGORY_PASSWORD.equals(password)) {
                    categoryPasswordDialog = null;
                    UNLOCKED_PROTECTED_KEYWORDS.add(keyword);
                    int position = sortAdapter.getData().indexOf(sortData);
                    sortFocused = position < 0 ? currentSelected : position;
                    sortChange = true;
                    mHandler.removeCallbacks(mDataRunnable);
                    mDataRunnable.run();
                    return true;
                }
                // 密码不对：框不关（框内自己红字提示），用户直接在键盘上重输
                restoreCategorySelection();
                return false;
            }

            @Override
            public void onCancel() {
                categoryPasswordDialog = null;
                restoreCategorySelection();
            }
        });
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (categoryPasswordDialog == d) {
                    categoryPasswordDialog = null;
                }
            }
        });
        categoryPasswordDialog = dialog;
        try {
            dialog.show();
        } catch (Throwable th) {
            // 弹不出来（窗口已失效/页面正在销毁）时不能把标记留在 true，否则下次点伦理就彻底没反应
            if (categoryPasswordDialog == dialog) {
                categoryPasswordDialog = null;
            }
            restoreCategorySelection();
        }
    }

    private void restoreCategorySelection() {
        sortChange = false;
        sortFocused = currentSelected;
        if (mGridView != null) {
            mGridView.post(new Runnable() {
                @Override
                public void run() {
                    mGridView.setSelectedPosition(currentSelected);
                }
            });
        }
    }

    private void showFilterIcon(int count) {
        boolean visible = count > 0;
        currentView.findViewById(R.id.tvFilterColor).setVisibility(visible ? View.VISIBLE : View.GONE);
        currentView.findViewById(R.id.tvFilter).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (!canEnterCategory(sortFocused)) {
                    return;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    changeTop(sortFocused != 0);
                    if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                } else if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                    ((GridFragment) baseLazyFragment).forceRefresh();
                }
            }
        }
    };

    private long menuKeyDownTime = 0;
    private static final long LONG_PRESS_THRESHOLD = 2000; // 设置长按的阈值，单位是毫秒
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (topHide < 0)
            return false;
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                menuKeyDownTime = System.currentTimeMillis();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                long pressDuration = System.currentTimeMillis() - menuKeyDownTime;
                if (pressDuration >= LONG_PRESS_THRESHOLD) {
                    jumpActivity(SettingActivity.class);;
                }else {
                    showSiteSwitch();
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    byte topHide = 0;

    private void changeTop(boolean hide) {
        ViewObj viewObj = new ViewObj(topLayout, (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams());
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                topHide = (byte) (hide ? 1 : 0);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        if (hide && topHide == 0) {
            animatorSet.playTogether(ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            AutoSizeUtils.mm2px(this.mContext, 10.0f),
                            AutoSizeUtils.mm2px(this.mContext, 0.0f)),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            AutoSizeUtils.mm2px(this.mContext, 50.0f),
                            AutoSizeUtils.mm2px(this.mContext, 1.0f)),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", 1.0f, 0.0f));
            animatorSet.setDuration(200);
            animatorSet.start();
            return;
        }
        if (!hide && topHide == 1) {
            animatorSet.playTogether(ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            AutoSizeUtils.mm2px(this.mContext, 0.0f),
                            AutoSizeUtils.mm2px(this.mContext, 10.0f)),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            AutoSizeUtils.mm2px(this.mContext, 1.0f),
                            AutoSizeUtils.mm2px(this.mContext, 50.0f)),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", 0.0f, 1.0f));
            animatorSet.setDuration(200);
            animatorSet.start();
        }
    }

    @Override
    protected void onDestroy() {
        dismissHomeDialogs();
        mHandler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screenOffReceiver);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
        unregisterEventBus();
        if (isFinishing()) {
            ControlManager.get().stopServer();
        }
    }

    private void unregisterEventBus() {
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this);
            eventBusRegistered = false;
        }
    }

    private SelectDialog<SourceBean> mSiteSwitchDialog;

    void showSiteSwitch() {
        if (isActivityUnavailable()) return;
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (select < 0 || select >= sites.size()) select = 0;
        if (mSiteSwitchDialog == null) {
            mSiteSwitchDialog = new SelectDialog<>(HomeActivity.this);
            TvRecyclerView tvRecyclerView = mSiteSwitchDialog.findViewById(R.id.list);
            // 根据 sites 数量动态计算列数
            int spanCount = (int) Math.floor(sites.size() / 20.0);
            spanCount = Math.min(spanCount, 2);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(mSiteSwitchDialog.getContext(), spanCount + 1));
            // 设置对话框宽度
            ConstraintLayout cl_root = mSiteSwitchDialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            clp.width = AutoSizeUtils.mm2px(mSiteSwitchDialog.getContext(), 380 + 200 * spanCount);
            mSiteSwitchDialog.setTip("请选择首页数据源");
        }
        mSiteSwitchDialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
            @Override
            public void click(SourceBean value, int pos) {
                dismissSiteSwitchDialog();
                if (needPasswordForSource(value)) {
                    // 多仓下切到「国外频道」这种仓库，要跟伦理一样先输密码
                    showSourcePasswordDialog(value);
                    return;
                }
                applyHomeSource(value);
            }
            @Override
            public String getDisplay(SourceBean val) {
                return val.getName();
            }
        }, new DiffUtil.ItemCallback<SourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem == newItem;
            }
            @Override
            public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getKey().equals(newItem.getKey());
            }
        }, sites, select);
        if (!mSiteSwitchDialog.isShowing())
            mSiteSwitchDialog.show();
    }

    private void refreshHome()
    {
        refreshHome(true);
    }

    /** 多仓（可切换的数据源不止一个）且目标数据源名字命中保护关键字时，切换前要密码 */
    private boolean needPasswordForSource(SourceBean value) {
        if (value == null || value.getName() == null) {
            return false;
        }
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites == null || sites.size() <= 1) {
            // 单仓：不需要密码
            return false;
        }
        SourceBean current = ApiConfig.get().getHomeSourceBean();
        if (current != null && value.getName().equals(current.getName())
                && value.getKey() != null && value.getKey().equals(current.getKey())) {
            return false;
        }
        for (String keyword : PROTECTED_SOURCE_KEYWORDS) {
            if (value.getName().contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void applyHomeSource(SourceBean value) {
        if (value == null) {
            return;
        }
        // 换数据源/换仓一律重新要伦理密码，并且清掉「坏源」记录（用户手动点的就再试一次）
        lockProtectedCategories();
        clearDeadHomeSource(value.getKey());
        previousHomeSource = ApiConfig.get().getHomeSourceBean();
        ApiConfig.get().setSourceBean(value);
        refreshHome(false);
    }

    private void showSourcePasswordDialog(final SourceBean value) {
        if (isActivityUnavailable()) {
            return;
        }
        final NumericPasswordDialog dialog = new NumericPasswordDialog(this, value.getName() + " · 需要密码");
        dialog.setOnListener(new NumericPasswordDialog.OnListener() {
            @Override
            public boolean onSubmit(String password) {
                if (PROTECTED_CATEGORY_PASSWORD.equals(password)) {
                    applyHomeSource(value);
                    return true;
                }
                // 密码不对：框不关（框内自己红字提示），用户直接重输
                return false;
            }

            @Override
            public void onCancel() {
            }
        });
        dialog.show();
    }

    private void refreshHomeSort() {
        refreshHomeRec = true;
        if (UserFragment.homeHotVodAdapter != null) {
            UserFragment.homeHotVodAdapter.setNewData(new ArrayList<Movie.Video>());
        }
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home != null) {
            SourceViewModel.clearSortCache(home.getKey());
        }
        refreshHome(false);
    }

    private void updateHomeRec(AbsSortXml absXml) {
        if (!refreshHomeRec) return;
        refreshHomeRec = false;
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) != 1) return;
        if (absXml == null || absXml.videoList == null || UserFragment.homeHotVodAdapter == null) return;
        UserFragment.homeHotVodAdapter.setNewData(absXml.videoList);
    }

    private void refreshHome(final boolean restart)
    {
        if (Thread.currentThread() != android.os.Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    refreshHome(restart);
                }
            });
            return;
        }
        if (isActivityUnavailable()) {
            return;
        }
        dismissHomeDialogs();
        if (!restart) {
            loadHomeSort(true);
            return;
        }
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        HomeActivity.this.startActivity(intent);
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }

    private void dismissHomeDialogs() {
        dismissConfigErrorDialog();
        dismissSiteSwitchDialog();
        dismissCategoryPasswordDialog();
    }

    private void dismissConfigErrorDialog() {
        if (mConfigErrorDialog != null) {
            if (mConfigErrorDialog.isShowing()) {
                mConfigErrorDialog.dismiss();
            }
            mConfigErrorDialog = null;
        }
    }

    private void dismissSiteSwitchDialog() {
        if (mSiteSwitchDialog != null) {
            if (mSiteSwitchDialog.isShowing()) {
                mSiteSwitchDialog.dismiss();
            }
            mSiteSwitchDialog = null;
        }
    }

    private void refreshEmpty()
    {
        skipNextUpdate=true;
        mHandler.removeCallbacks(sourceSwitchWatchdog);
        showSuccess();
        cancelHomeSortLoading();
        clearHomePages();
        sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
        initViewPager(null);
        tvName.clearAnimation();
        restoreHomeNameText();
    }

    /** 取消加载后把左上角文字还原成当前数据源名字（不再停在「切换中...」） */
    private void restoreHomeNameText() {
        try {
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            if (home != null && home.getName() != null && !home.getName().isEmpty()) {
                tvName.setText(home.getName());
            }
        } catch (Throwable ignored) {
        }
    }

    private void cancelHomeSortLoading() {
        mHandler.removeCallbacks(sourceSwitchWatchdog);
        homeSortLoading = false;
        loadingSourceKey = null;
        tvName.clearAnimation();
        if (previousHomeSource != null) {
            ApiConfig.get().setSourceBean(previousHomeSource);
        }
        if (previousHomeName != null && !previousHomeName.isEmpty()) {
            tvName.setText(previousHomeName);
        } else {
            restoreHomeNameText();
        }
        previousHomeSource = null;
        previousHomeName = null;
    }

    private void tvNameAnimation(String tip)
    {
        tvName.clearAnimation();
        // 切换数据源时不再显示（闪烁的）数据源名字，改成闪烁的提示文字
        tvName.setText(tip == null ? LOADING_TEXT : tip);
        AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setStartOffset(20);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        tvName.startAnimation(blinkAnimation);
    }
}
