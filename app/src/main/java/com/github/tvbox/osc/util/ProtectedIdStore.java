package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.Movie;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 保护片区（伦理 / 理论片栏目、国外频道）里出现过的影片 id 名单。
 * 只要在保护区里刷到过一条，就把它记下来；以后搜索时这些片子一律不出现在结果里，
 * 即便搜索接口本身没法区分「这片属于哪个栏目」。
 */
public class ProtectedIdStore {
    private static final String KEY = "protected_vod_ids_v1";
    /** 每个数据源最多记这么多条，防止 Hawk 越存越大 */
    private static final int MAX_PER_SOURCE = 2000;
    /** 本次进程里已经做过「搜索前预热」的源，避免每搜一次都再去抓几页 */
    private static final java.util.HashSet<String> WARMED_SOURCES = new java.util.HashSet<>();

    public static boolean isWarmed(String sourceKey) {
        return sourceKey != null && WARMED_SOURCES.contains(sourceKey);
    }

    public static void markWarmed(String sourceKey) {
        if (sourceKey != null && !sourceKey.isEmpty()) {
            WARMED_SOURCES.add(sourceKey);
        }
    }

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

    /** 视频 id 归一化：有些站点 id 带空格/前后缀，统一 trim */
    private static String cleanId(String id) {
        return id == null ? "" : id.trim();
    }

    public static void mark(String sourceKey, String id) {
        String vid = cleanId(id);
        if (sourceKey == null || sourceKey.isEmpty() || vid.isEmpty()) {
            return;
        }
        try {
            JSONObject map = loadMap();
            JSONArray array = map.optJSONArray(sourceKey);
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String saved = array.optString(i, "");
                    if (saved != null && !saved.isEmpty()) {
                        ids.add(saved);
                    }
                }
            }
            if (ids.contains(vid)) {
                return;
            }
            ids.add(vid);
            while (ids.size() > MAX_PER_SOURCE) {
                Iterator<String> iterator = ids.iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
            JSONArray newArray = new JSONArray();
            for (String one : ids) {
                newArray.put(one);
            }
            map.put(sourceKey, newArray);
            saveMap(map);
        } catch (Throwable ignored) {
        }
    }

    /** 一次性把一批保护区影片记下来 */
    public static void markAll(String fallbackSourceKey, List<Movie.Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return;
        }
        for (Movie.Video video : videos) {
            if (video == null) {
                continue;
            }
            String key = video.sourceKey == null || video.sourceKey.isEmpty() ? fallbackSourceKey : video.sourceKey;
            mark(key, video.id);
        }
    }

    public static boolean contains(String sourceKey, String id) {
        String vid = cleanId(id);
        if (sourceKey == null || sourceKey.isEmpty() || vid.isEmpty()) {
            return false;
        }
        try {
            JSONArray array = loadMap().optJSONArray(sourceKey);
            if (array == null) {
                return false;
            }
            for (int i = 0; i < array.length(); i++) {
                if (vid.equals(array.optString(i, ""))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 一共记了多少条，调试或设置页展示用 */
    public static int size() {
        try {
            JSONObject map = loadMap();
            int total = 0;
            Iterator<String> keys = map.keys();
            while (keys.hasNext()) {
                JSONArray array = map.optJSONArray(keys.next());
                if (array != null) {
                    total += array.length();
                }
            }
            return total;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void clearAll() {
        try {
            Hawk.put(KEY, "{}");
        } catch (Throwable ignored) {
        }
        WARMED_SOURCES.clear();
    }
}
