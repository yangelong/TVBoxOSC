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
    /** 分类名里出现这些词，就要密码、并排到最后（含繁体、带空格写法） */
    private static final String[] PROTECTED_CATEGORY_KEYWORDS = {"伦理", "倫理"};

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
}
