package com.github.tvbox.osc.util;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchHelper {

    private static final Pattern SEASON_SUFFIX_PATTERN = Pattern.compile(
            "^(.*?)[\\s._-]*(?:(第\\s*)?([0-9]{1,2}|[零一二三四五六七八九十百千万两]+)(\\s*季)|([0-9]{1,2}))$");

    private static final String[] CHINESE_NUMBERS = {
            "零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十"
    };

    public static HashMap<String, String> getSourcesForSearch() {
        HashMap<String, String> mCheckSources;
        try {
            String api = Hawk.get(HawkConfig.API_URL, "");
            if(api.isEmpty())return null;
            HashMap<String, HashMap<String, String>> mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH, new HashMap<>());
            mCheckSources = mCheckSourcesForApi.get(api);
        } catch (Exception e) {
            return null;
        }
        if (mCheckSources == null || mCheckSources.isEmpty()) {
            mCheckSources = getSources();
        }
//        else {
//            HashMap<String, String> newSources = getSources();
//            for (Map.Entry<String, String> entry : newSources.entrySet()) {
//                String newKey = entry.getKey();
//                String newValue = entry.getValue();
//                if (!mCheckSources.containsKey(newKey)) {
//                    mCheckSources.put(newKey, newValue);
//                }
//            }
//            Iterator<Map.Entry<String, String>> iterator = mCheckSources.entrySet().iterator();
//            while (iterator.hasNext()) {
//                Map.Entry<String, String> oldEntry = iterator.next();
//                String oldKey = oldEntry.getKey();
//                if (!newSources.containsKey(oldKey)) {
//                    iterator.remove();
//                }
//            }
//        }
        return mCheckSources;
    }

    public static void putCheckedSources(HashMap<String, String> mCheckSources,boolean isAll) {
        String api = Hawk.get(HawkConfig.API_URL, "");
        if (api.isEmpty()) {
            return;
        }
        HashMap<String, HashMap<String, String>> mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH,null);

        if(isAll){
            if (mCheckSourcesForApi == null) return;
            if (mCheckSourcesForApi.containsKey(api)) mCheckSourcesForApi.remove(api);
        }else {
            if (mCheckSourcesForApi == null) mCheckSourcesForApi = new HashMap<>();
            mCheckSourcesForApi.put(api, mCheckSources);
        }
        SearchActivity.setCheckedSourcesForSearch(mCheckSources);
        Hawk.put(HawkConfig.SOURCES_FOR_SEARCH, mCheckSourcesForApi);
    }

    public static HashMap<String, String> getSources(){
        HashMap<String, String> mCheckSources = new HashMap<>();
        for (SourceBean bean : ApiConfig.get().getSourceBeanList()) {
            if (!bean.isSearchable()) {
                continue;
            }
            mCheckSources.put(bean.getKey(), "1");
        }
        return mCheckSources;
    }

    public static List<Movie.Video> sortSearchResults(List<Movie.Video> videos, String searchTitle) {
        List<Movie.Video> results = new ArrayList<>(videos);
        final String keyword = normalizeSearchText(searchTitle);
        if (keyword.isEmpty() || results.size() < 2) return results;
        final HashMap<Movie.Video, Integer> scores = new HashMap<>();
        for (Movie.Video video : results) {
            String name = normalizeSearchText(video == null ? null : video.name);
            int score = name.equals(keyword) ? 3 : name.startsWith(keyword) ? 2 : name.contains(keyword) ? 1 : 0;
            scores.put(video, score);
        }
        // 稳定排序仅调整当前站点内的顺序，所有结果均保留。
        Collections.sort(results, new Comparator<Movie.Video>() {
            @Override
            public int compare(Movie.Video left, Movie.Video right) {
                return scores.get(right) - scores.get(left);
            }
        });
        return results;
    }

    /**
     * 搜索匹配用的归一化：让「第 12 集」「第十二集」「12季」「十二部」这类写法互相等效。
     * 规则：1、去空白；2、中文数字与阿拉伯数字等效；3、去掉数字前的「第」；4、集/季/部 等效为「季」。
     */
    private static String normalizeSearchText(String text) {
        if (text == null) {
            return "";
        }
        String result = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        result = chineseNumberToArabic(result);
        result = result.replace("第", "");
        return result.replace("集", "季").replace("部", "季");
    }

    private static boolean isChineseNumberChar(char c) {
        switch (c) {
            case '零': case '〇': case '一': case '二': case '两': case '三': case '四':
            case '五': case '六': case '七': case '八': case '九': case '十':
            case '百': case '千': case '万':
                return true;
            default:
                return false;
        }
    }

    /**
     * 把连续的中文数字片段转成阿拉伯数字（「十二」→12、「二十」→20、「一百零一」→101）。
     */
    private static String chineseNumberToArabic(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (!isChineseNumberChar(c)) {
                builder.append(c);
                index++;
                continue;
            }
            int end = index;
            while (end < text.length() && isChineseNumberChar(text.charAt(end))) {
                end++;
            }
            String run = text.substring(index, end);
            builder.append(runToArabic(run));
            index = end;
        }
        return builder.toString();
    }

    private static String runToArabic(String run) {
        if (run.isEmpty()) {
            return run;
        }
        if (run.length() == 1 && run.charAt(0) != '十' && run.charAt(0) != '百' && run.charAt(0) != '千' && run.charAt(0) != '万') {
            int digit = chineseDigitValue(run.charAt(0));
            return digit >= 0 ? String.valueOf(digit) : run;
        }
        long total = 0;
        long section = 0;
        long unit = 0;      // 当前正在累积的数字
        boolean hasNumber = false;
        for (int i = 0; i < run.length(); i++) {
            char c = run.charAt(i);
            int digit = chineseDigitValue(c);
            if (digit >= 0) {
                unit = digit;
                hasNumber = true;
                continue;
            }
            long multiplier = 0;
            if (c == '十') multiplier = 10;
            else if (c == '百') multiplier = 100;
            else if (c == '千') multiplier = 1000;
            else if (c == '万') multiplier = 10000;
            if (multiplier == 0) {
                continue;
            }
            if (multiplier == 10000) {
                total = (total + section + (hasNumber ? unit : 0)) * 10000;
                section = 0;
            } else {
                section += (hasNumber ? unit : 1) * multiplier;
            }
            unit = 0;
            hasNumber = false;
        }
        section += hasNumber ? unit : 0;
        total += section;
        // 单个单位字符（如「十」）也要得到 10
        return total > 0 ? String.valueOf(total) : run;
    }

    private static int chineseDigitValue(char c) {
        switch (c) {
            case '零': case '〇': return 0;
            case '一': return 1;
            case '二': case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    public static List<String> splitWords(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        result.add(text);
        String[] parts = text.split("\\W+");
        if (parts.length > 1) {
            result.addAll(Arrays.asList(parts));
        }
        Matcher matcher = SEASON_SUFFIX_PATTERN.matcher(text.trim());
        if (matcher.matches()) {
            String baseName = matcher.group(1).trim();
            if (!baseName.isEmpty()
                    && !Character.isDigit(baseName.charAt(baseName.length() - 1))
                    && !result.contains(baseName)) {
                result.add(baseName);
            }
            String season = matcher.group(3) == null ? matcher.group(5) : matcher.group(3);
            if (!baseName.isEmpty() && season != null) {
                String nextSeason = nextSeason(season);
                if (nextSeason != null) {
                    String prefix = matcher.group(3) == null || matcher.group(2) == null ? "" : matcher.group(2);
                    String suffix = matcher.group(3) == null || matcher.group(4) == null ? "" : matcher.group(4);
                    String nextTitle = baseName + prefix + nextSeason + suffix;
                    if (!result.contains(nextTitle)) {
                        result.add(nextTitle);
                    }
                }
            }
        }
        return result;
    }

    private static String nextSeason(String season) {
        String value = season.trim();
        try {
            return String.valueOf(Integer.parseInt(value) + 1);
        } catch (NumberFormatException ignored) {
            if ("两".equals(value)) {
                return "三";
            }
            for (int i = 0; i < CHINESE_NUMBERS.length - 1; i++) {
                if (CHINESE_NUMBERS[i].equals(value)) {
                    return CHINESE_NUMBERS[i + 1];
                }
            }
            return null;
        }
    }

}
