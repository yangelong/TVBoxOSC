package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.tvbox.osc.bean.Movie;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 需要密码的分类（伦理）专用的浏览记录。
 * 这类影片不写正式播放历史，但要让「刚看过的」在栏目里排到最前面，
 * 所以单独存一份轻量记录（源 key + 影片 id + 名称 + 封面 + 时间）。
 */
public class ProtectedRecentHelper {
    private static final String KEY = "protected_recent_vod_v1";
    private static final int MAX_ITEMS = 40;

    private static String makeKey(String sourceKey, String vodId) {
        return (sourceKey == null ? "" : sourceKey) + "|" + (vodId == null ? "" : vodId);
    }

    /** 记一条浏览记录（最新的一次排在最前面，同一个片子只留一条） */
    public static void record(String sourceKey, String vodId, String name, String pic) {
        if (TextUtils.isEmpty(vodId)) {
            return;
        }
        try {
            JSONArray old = loadArray();
            JSONArray kept = new JSONArray();
            String key = makeKey(sourceKey, vodId);
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (key.equals(item.optString("k"))) {
                    continue;
                }
                kept.put(item);
            }
            JSONObject current = new JSONObject();
            current.put("k", key);
            current.put("id", vodId);
            current.put("src", sourceKey == null ? "" : sourceKey);
            current.put("n", name == null ? "" : name);
            current.put("p", pic == null ? "" : pic);
            current.put("t", System.currentTimeMillis());
            JSONArray result = new JSONArray();
            result.put(current);
            for (int i = 0; i < kept.length() && result.length() < MAX_ITEMS; i++) {
                result.put(kept.get(i));
            }
            Hawk.put(KEY, result.toString());
        } catch (Throwable ignored) {
        }
    }

    private static JSONArray loadArray() {
        try {
            Object value = Hawk.get(KEY, "");
            if (value instanceof String && !((String) value).isEmpty()) {
                return new JSONArray((String) value);
            }
        } catch (Throwable ignored) {
        }
        return new JSONArray();
    }

    /** 按「源|id」和纯 id 两套索引记录最近看过的顺序，序号越小越靠前 */
    private static class RecentIndex {
        Map<String, Integer> byKey = new HashMap<>();
        Map<String, Integer> byId = new HashMap<>();
    }

    private static RecentIndex recentOrder() {
        RecentIndex index = new RecentIndex();
        JSONArray array = loadArray();
        int order = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String key = item.optString("k");
            String id = item.optString("id");
            if (!TextUtils.isEmpty(key) && !index.byKey.containsKey(key)) {
                index.byKey.put(key, order);
            }
            if (!TextUtils.isEmpty(id) && !index.byId.containsKey(id)) {
                index.byId.put(id, order);
            }
            order++;
        }
        return index;
    }

    /**
     * 把最近看过的影片挪到列表最前面（按最近一次观看时间倒序），其余保持原来的顺序。
     */
    public static List<Movie.Video> reorder(List<Movie.Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return videos;
        }
        try {
            RecentIndex index = recentOrder();
            if (index.byKey.isEmpty() && index.byId.isEmpty()) {
                return videos;
            }
            List<Movie.Video> recent = new ArrayList<>();
            List<Movie.Video> normal = new ArrayList<>();
            Map<Movie.Video, Integer> rank = new HashMap<>();
            for (Movie.Video video : videos) {
                if (video == null) {
                    continue;
                }
                Integer at = index.byKey.get(makeKey(video.sourceKey, video.id));
                if (at == null && video.id != null) {
                    at = index.byId.get(video.id);
                }
                if (at != null) {
                    recent.add(video);
                    rank.put(video, at);
                } else {
                    normal.add(video);
                }
            }
            if (recent.isEmpty()) {
                return videos;
            }
            Collections.sort(recent, new Comparator<Movie.Video>() {
                @Override
                public int compare(Movie.Video left, Movie.Video right) {
                    Integer a = rank.get(left);
                    Integer b = rank.get(right);
                    return (a == null ? Integer.MAX_VALUE : a) - (b == null ? Integer.MAX_VALUE : b);
                }
            });
            List<Movie.Video> result = new ArrayList<>(videos.size());
            result.addAll(recent);
            result.addAll(normal);
            return result;
        } catch (Throwable ignored) {
            return videos;
        }
    }
}
