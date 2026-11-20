package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

/**
 * 搜索前的「保护区排除名单预热」。
 * 在真正发起搜索之前，静默把这个源伦理 / 理论片栏目的前几页拉一遍，
 * 把影片 id 补进 {@link ProtectedIdStore}，这样连没点开过的伦理片也搜不出来。
 * 抓完（或超时）再开始搜索，只多花一次栏目页的请求时间。
 */
public class ProtectedSearchWarmup {
    /** 预热翻几页 */
    public static final int PAGES = 3;
    /** 最多等这么久，站点慢就不再等，直接开始搜索 */
    private static final long TIMEOUT_MS = 2500;

    private final SourceViewModel sourceViewModel;
    private final Runnable onDone;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MovieSort.SortData sortData;
    private String sourceKey;
    private int page = 1;
    private boolean running;

    public ProtectedSearchWarmup(SourceViewModel sourceViewModel, Runnable onDone) {
        this.sourceViewModel = sourceViewModel;
        this.onDone = onDone;
    }

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    /** 需要预热就启动并返回 true（调用方要等 onDone 再开搜）；不需要或已在跑返回 false */
    public boolean startIfNeeded() {
        if (running) {
            return true;
        }
        try {
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            sourceKey = home == null ? null : home.getKey();
            if (sourceKey == null || sourceKey.isEmpty()) {
                return false;
            }
            // 整仓都在保护区里，搜索本身就整仓跳过，不用预热
            if (ProtectedZoneHelper.isCurrentWarehouseProtected()) {
                return false;
            }
            if (ProtectedIdStore.isWarmed(sourceKey)) {
                return false;
            }
            sortData = ProtectedZoneHelper.getProtectedSort(sourceKey);
            if (sortData == null || sortData.id == null) {
                return false;
            }
            running = true;
            page = 1;
            handler.postDelayed(timeout, TIMEOUT_MS);
            sourceViewModel.getList(sortData, page);
            return true;
        } catch (Throwable ignored) {
            running = false;
            return false;
        }
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 把观察到的分类页结果喂进来。
     * 返回 true 表示这条结果已被预热用掉了，不要拿去更新搜索界面。
     */
    public boolean handleResult(AbsXml absXml) {
        if (!running) {
            return false;
        }
        try {
            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null
                    && !absXml.movie.videoList.isEmpty()) {
                ProtectedIdStore.markAll(sourceKey, absXml.movie.videoList);
                if (page < PAGES) {
                    page++;
                    sourceViewModel.getList(sortData, page);
                    return true;
                }
                return consume();
            }
            // 这一页空/失败，就不再往后翻了
            return consume();
        } catch (Throwable ignored) {
            return consume();
        }
    }

    private boolean consume() {
        finish();
        return true;
    }

    private void finish() {
        if (!running) {
            return;
        }
        running = false;
        handler.removeCallbacks(timeout);
        ProtectedIdStore.markWarmed(sourceKey);
        try {
            if (onDone != null) {
                onDone.run();
            }
        } catch (Throwable ignored) {
        }
    }
}
