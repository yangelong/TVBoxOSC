package com.github.tvbox.osc.bean;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;

import android.text.TextUtils;

import com.github.tvbox.osc.util.DefaultConfig;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
public class VodInfo implements Serializable {
    public String last;//时间
    //内容id
    public String id;
    //父级id
    public int tid;
    //影片名称 <![CDATA[老爸当家]]>
    public String name;
    //类型名称
    public String type;
    //视频分类zuidam3u8,zuidall
    public String dt;
    //图片
    public String pic;
    //语言
    public String lang;
    //地区
    public String area;
    //年份
    public int year;
    public String state;
    //描述集数或者影片信息<![CDATA[共40集]]>
    public String note;
    //演员<![CDATA[张国立,蒋欣,高鑫,曹艳艳,王维维,韩丹彤,孟秀,王新]]>
    public String actor;
    //导演<![CDATA[陈国星]]>
    public String director;
    public ArrayList<VodSeriesFlag> seriesFlags;
    public LinkedHashMap<String, List<VodSeries>> seriesMap;
    public String des;// <![CDATA[权来]
    public String playFlag = null;
    public int playIndex = 0;
    public String playNote = "";
    public String sourceKey;
    public String playerCfg = "";
    public boolean reverseSort = false;

    public void setVideo(Movie.Video video) {
        last = video.last;
        id = video.id;
        tid = video.tid;
        name = video.name;
        type = video.type;
        // dt = video.dt;
        pic = video.pic;
        lang = video.lang;
        area = video.area;
        year = video.year;
        state = video.state;
        note = video.note;
        actor = video.actor;
        director = video.director;
        des = video.des;
        if (video.urlBean != null && video.urlBean.infoList != null && video.urlBean.infoList.size() > 0) {
            LinkedHashMap<String, List<VodSeries>> tempSeriesMap = new LinkedHashMap<>();
            seriesFlags = new ArrayList<>();
            for (Movie.Video.UrlBean.UrlInfo urlInfo : video.urlBean.infoList) {
                if (urlInfo.beanList != null && urlInfo.beanList.size() > 0) {
                    List<VodSeries> seriesList = new ArrayList<>();
                    for (Movie.Video.UrlBean.UrlInfo.InfoBean infoBean : urlInfo.beanList) {
                        seriesList.add(new VodSeries(infoBean.name, infoBean.url));
                    }
                    tempSeriesMap.put(urlInfo.flag, seriesList);
                    seriesFlags.add(new VodSeriesFlag(urlInfo.flag));
                }
            }

            seriesMap = new LinkedHashMap<>();
            for (VodSeriesFlag flag : seriesFlags) {
                List<VodSeries> list = tempSeriesMap.get(flag.name);
                assert list != null;
                if(seriesFlags.size()<=5){
                    if(isReverse(list))Collections.reverse(list);
                }
                seriesMap.put(flag.name, list);
            }

            // 把可直接播放（直播流/直链）的线路排到前面，需要嗅探的排后面
            prioritizeDirectLines();
        }
    }

    /**
     * 线路排序：能直接播放的（.m3u8/.mp4 等直链、直播流）优先，需要网页嗅探的排后面。
     * 两类线路内部各自保持源站原有顺序；若全都可以直连或全都需嗅探则不动。
     */
    public void prioritizeDirectLines() {
        if (seriesMap == null || seriesFlags == null || seriesMap.size() < 2) return;
        List<String> direct = new ArrayList<>();
        List<String> needSniff = new ArrayList<>();
        for (String flag : seriesMap.keySet()) {
            if (isDirectLine(flag)) {
                direct.add(flag);
            } else {
                needSniff.add(flag);
            }
        }
        if (direct.isEmpty() || needSniff.isEmpty()) return;

        LinkedHashMap<String, List<VodSeries>> orderedMap = new LinkedHashMap<>();
        for (String flag : direct) {
            orderedMap.put(flag, seriesMap.get(flag));
        }
        for (String flag : needSniff) {
            orderedMap.put(flag, seriesMap.get(flag));
        }

        ArrayList<VodSeriesFlag> orderedFlags = new ArrayList<>();
        for (VodSeriesFlag flag : seriesFlags) {
            if (orderedMap.containsKey(flag.name)) {
                orderedFlags.add(flag);
            }
        }
        seriesMap = orderedMap;
        if (orderedFlags.size() == seriesMap.size()) {
            seriesFlags = orderedFlags;
        }
    }

    /** 这条线路是否可以直接播放（不需要嗅探） */
    public boolean isDirectLine(String flag) {
        if (flag == null || seriesMap == null) return false;
        List<VodSeries> list = seriesMap.get(flag);
        if (list == null || list.isEmpty()) return false;
        if (isDirectPlayable(list.get(0).url)) return true;
        // 兜底：线路名里带 m3u8 的（如 ffm3u8）视为直连线路
        return flag.toLowerCase().contains("m3u8");
    }

    /** 第一条可直接播放的线路名，没有则返回 null */
    public String firstDirectLine() {
        if (seriesMap == null) return null;
        for (String flag : seriesMap.keySet()) {
            if (isDirectLine(flag)) return flag;
        }
        return null;
    }

    /**
     * 判断这条地址能不能直接交给播放器。
     * 说明：嗅探线路给出的通常是一个网页地址，直连线路给的是 .m3u8/.mp4 这类流地址。
     */
    private boolean isDirectPlayable(String url) {
        if (TextUtils.isEmpty(url)) return false;
        if (DefaultConfig.isVideoFormat(url)) return true;
        String target = url.toLowerCase();
        if (target.contains("url=")) {
            try {
                target = java.net.URLDecoder.decode(url, "UTF-8").toLowerCase();
            } catch (Throwable ignored) {
            }
        }
        return target.contains(".m3u8") || target.contains(".mp4") || target.contains(".flv")
                || target.contains(".mkv") || target.contains(".mpd") || target.contains(".mov");
    }

    private int extractNumber(String name) {
        java.util.regex.Matcher matcher = getPattern("\\d+").matcher(name);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return 0;
    }
    private boolean isReverse(List<VodInfo.VodSeries> list) {
        int ascCount = 0, descCount = 0;
        // 比较最多前 6 个相邻元素对
        int limit = Math.min(list.size() - 1, 6);
        for (int i = 0; i < limit; i++) {
            int current = extractNumber(list.get(i).name);
            int next = extractNumber(list.get(i + 1).name);
            if (current < next) {
                ascCount++;
                if (ascCount == 2) return false;
            } else if (current > next) {
                descCount++;
                if (descCount == 2) return true;
            }
        }
        return false;
    }

    public void reverse() {
        Set<String> flags = seriesMap.keySet();
        for (String flag : flags) {
            Collections.reverse(seriesMap.get(flag));
        }
    }

    public static class VodSeriesFlag implements Serializable {

        public String name;
        public boolean selected;

        public VodSeriesFlag() {

        }

        public VodSeriesFlag(String name) {
            this.name = name;
        }
    }

    public static class VodSeries implements Serializable {

        public String name;
        public String url;
        public boolean selected;

        public VodSeries() {
        }

        public VodSeries(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}