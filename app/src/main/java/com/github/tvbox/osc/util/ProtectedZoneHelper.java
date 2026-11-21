package com.github.tvbox.osc.util;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

/**
 * 「受保护区域」判定。
 * 除了名字带「伦理」的分类，整个「国外频道」（不管是仓还是里面的数据源）也算：
 * 不写播放历史、看过的排最前。
 */
public class ProtectedZoneHelper {
    public static final String FOREIGN_WAREHOUSE_KEYWORD = "国外频道";
    /** 分类名里出现这些词，就要密码、并排到最后（含繁体、带空格写法；有的源把它写成「理论片」） */
    private static final String[] PROTECTED_CATEGORY_KEYWORDS = {"伦理", "倫理", "理论", "理論"};
    /** 站点/频道名里出现这些词，整站内容不进搜索结果 */
    private static final String[] PROTECTED_SOURCE_NAME_KEYWORDS = {"国外频道", "伦理", "倫理", "理论", "理論"};

    /** 保护区统一口令（伦理栏目、国外频道、成人搜索都用这个） */
    public static final String PROTECTED_PASSWORD = "104";

    /**
     * 搜索词里出现这些词，说明是在找成人/伦理类内容：先要密码，密码对了才真正发起搜索。
     * 只放「一眼就能确定」的词，避免误伤正常片名。
     */
    private static final String[] ADULT_QUERY_KEYWORDS = {
            "伦理", "倫理", "理论", "理論",
            "成人", "成人片", "情色", "色情", "黄色", "黃色",
            "三级", "三級", "限制级", "限制級",
            "无码", "無碼", "有码", "有碼",
            "里番", "番号", "番號",
            "麻豆", "果冻传媒", "果凍傳媒", "天美传媒", "天美傳媒",
            "星空传媒", "星空傳媒", "蜜桃传媒", "蜜桃傳媒",
            "swag", "jvid", "onlyfans", "porn", "hentai", "r18", "18禁", "18+",
            "a片", "禁片", "av女优", "av女優"
    };

    /** 本次进程里已经输对密码的成人搜索关键词，不用每次再问 */
    private static final java.util.HashSet<String> ADULT_QUERY_UNLOCKED = new java.util.HashSet<>();

    /**
     * 搜索词命中的成人关键词；不命中返回 null。
     * 去空格（含全角）＋小写后再比较，避免「伦 理」「R18」这类写法绕过。
     */
    public static String matchedAdultQuery(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String normalized = text.replace(" ", "").replace("\u3000", "").replace("\t", "")
                .toLowerCase(java.util.Locale.ROOT);
        for (String keyword : ADULT_QUERY_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    public static boolean isAdultQueryUnlocked(String keyword) {
        return keyword != null && ADULT_QUERY_UNLOCKED.contains(keyword);
    }

    public static void unlockAdultQuery(String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            ADULT_QUERY_UNLOCKED.add(keyword);
        }
    }

    /** 重新上锁（退出 App / 换源时调） */
    public static void lockAdultQueries() {
        ADULT_QUERY_UNLOCKED.clear();
    }

    /** 命中的保护关键字，没命中返回 null；名字做了去空格/全角空格处理 */
    public static String matchedProtectedCategoryKeyword(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.replace(" ", "").replace("\u3000", "").replace("\t", "");
        for (String keyword : PROTECTED_CATEGORY_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    public static boolean isProtectedCategoryName(String name) {
        return matchedProtectedCategoryKeyword(name) != null;
    }

    public static boolean isForeignWarehouseName(String name) {
        return name != null && name.contains(FOREIGN_WAREHOUSE_KEYWORD);
    }

    /** 当前首页数据源（站点）名字 */
    public static String currentHomeSourceName() {
        try {
            SourceBean bean = ApiConfig.get().getHomeSourceBean();
            return bean == null ? null : bean.getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 当前所在的仓（多仓线路）名字 */
    public static String currentWarehouseName() {
        try {
            String current = Hawk.get(HawkConfig.API_URL, "");
            if (current == null || current.isEmpty()) {
                return null;
            }
            ArrayList<String> lines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
            if (lines == null) {
                return null;
            }
            for (String line : lines) {
                if (current.equals(HistoryHelper.getApiLineUrl(line))) {
                    return HistoryHelper.getApiLineName(line);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 当前是不是处在「国外频道」（仓名或站点名命中即可） */
    public static boolean isInForeignWarehouse() {
        if (isForeignWarehouseName(currentHomeSourceName())) {
            return true;
        }
        return isForeignWarehouseName(currentWarehouseName());
    }

    /** 当前整仓是不是「国外频道」这种保护区（里面的所有站点都算） */
    public static boolean isCurrentWarehouseProtected() {
        return isForeignWarehouseName(currentWarehouseName())
                || isForeignWarehouseName(currentHomeSourceName());
    }

    /** 站点/频道名是否带保护区字样（国外频道、伦理、理论片…） */
    public static boolean isProtectedSourceName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String normalized = name.replace(" ", "").replace("\u3000", "").replace("\t", "");
        for (String keyword : PROTECTED_SOURCE_NAME_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这个搜索源是否要排除在搜索之外：
     * 1、当前整仓是「国外频道」→ 里面所有站点都不搜；
     * 2、站点自己名字带「国外频道/伦理/理论片」→ 不搜。
     */
    public static boolean isSearchBlockedSource(String sourceKey) {
        if (sourceKey == null || sourceKey.isEmpty()) {
            return false;
        }
        if (isCurrentWarehouseProtected()) {
            return true;
        }
        try {
            SourceBean bean = ApiConfig.get().getSource(sourceKey);
            return bean != null && isProtectedSourceName(bean.getName());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
