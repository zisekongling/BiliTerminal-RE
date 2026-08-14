package com.RobinNotBad.BiliClient.model;

import java.io.Serializable;
import java.util.List;

public class AudioStream implements Serializable {
    public long sid;
    public int type;
    public int timeout;
    public long size;
    public List<String> cdns;
    public String title;
    public String cover;
    public List<AudioQuality> qualities;

    public AudioStream() {}

    public static class AudioQuality implements Serializable {
        public int type;
        public String desc;
        public long size;
        public String bps;
        public String tag;
        public int require;
        public String requireDesc;
    }
}