package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.MovieSort;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 记住「某个数据源下哪些栏目确实是空的」。
 * 用户点开一次发现是空的，就记下来，下次加载首页时直接不给它建 tab，
 * 避免「点一下栏目就消失、重新加载又冒出来」这种反复闪烁。
 */
public class EmptyCategoryStore {
    private static final String KEY = "empty_category_map_v1";

    private static JSONObject loadMap() {
        try {
            return new JSONObject(Hawk.get(KEY, "{}"));
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private static void saveMap(JSONObject map) {
        try {
            Hawk.put(KEY, map.toString());
        } catch (Throwable ignored) {
        }
    }

    public static Set<String> getForSource(String sourceKey) {
        Set<String> ids = new HashSet<>();
        if (sourceKey == null) {
            return ids;
        }
        try {
            JSONObject map = loadMap();
            JSONArray array = map.optJSONArray(sourceKey);
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String id = array.optString(i, "");
                    if (id != null && !id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return ids;
    }

    public static void mark(String sourceKey, String sortId) {
        if (sourceKey == null || sortId == null || sortId.isEmpty()) {
            return;
        }
        try {
            JSONObject map = loadMap();
            JSONArray array = map.optJSONArray(sourceKey);
            if (array == null) {
                array = new JSONArray();
            }
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "");
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
            if (ids.contains(sortId)) {
                return;
            }
            ids.add(sortId);
            JSONArray newArray = new JSONArray();
            for (String id : ids) {
                newArray.put(id);
            }
            map.put(sourceKey, newArray);
            saveMap(map);
        } catch (Throwable ignored) {
        }
    }

    /** 栏目又有数据了（内容更新了），把它从「空栏目」名单里去掉 */
    public static void unmark(String sourceKey, String sortId) {
        if (sourceKey == null || sortId == null || sortId.isEmpty()) {
            return;
        }
        try {
            JSONObject map = loadMap();
            JSONArray array = map.optJSONArray(sourceKey);
            if (array == null) {
                return;
            }
            JSONArray kept = new JSONArray();
            boolean changed = false;
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "");
                if (sortId.equals(id)) {
                    changed = true;
                    continue;
                }
                kept.put(id);
            }
            if (changed) {
                map.put(sourceKey, kept);
                saveMap(map);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 加载 tab 之前，把已知是空栏目的一律剔掉 */
    public static List<MovieSort.SortData> removeKnownEmpty(String sourceKey, List<MovieSort.SortData> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        Set<String> ids = getForSource(sourceKey);
        if (ids.isEmpty()) {
            return data;
        }
        List<MovieSort.SortData> kept = new ArrayList<>();
        for (MovieSort.SortData item : data) {
            if (item == null) {
                continue;
            }
            // 第一个（换片源/推荐）不参与剔除
            if (!"my0".equals(item.id) && item.id != null && ids.contains(item.id)) {
                continue;
            }
            kept.add(item);
        }
        return kept;
    }

    public static void clearAll() {
        try {
            Hawk.put(KEY, "{}");
        } catch (Throwable ignored) {
        }
    }
}
