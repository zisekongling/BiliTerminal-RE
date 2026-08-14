package com.RobinNotBad.BiliClient.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lyric implements Serializable {
    public long sid;
    public String rawLyric;
    public List<LyricLine> lines;

    public Lyric() {}

    public Lyric(long sid, String rawLyric) {
        this.sid = sid;
        this.rawLyric = rawLyric;
        this.lines = parseLrc(rawLyric);
    }

    public static List<LyricLine> parseLrc(String rawLrc) {
        List<LyricLine> list = new ArrayList<>();
        if (rawLrc == null || rawLrc.isEmpty()) return list;

        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
        for (String line : rawLrc.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                int ms = Integer.parseInt(matcher.group(3));
                if (matcher.group(3).length() == 2) ms *= 10;
                long time = (min * 60L + sec) * 1000L + ms;
                String text = matcher.group(4);
                list.add(new LyricLine(time, text));
            }
        }
        return list;
    }

    public static class LyricLine implements Serializable {
        public long time;
        public String text;

        public LyricLine(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }
}