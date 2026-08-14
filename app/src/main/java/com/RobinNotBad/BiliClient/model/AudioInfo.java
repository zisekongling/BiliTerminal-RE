package com.RobinNotBad.BiliClient.model;

import java.io.Serializable;

public class AudioInfo implements Serializable {
    public long sid;
    public String title;
    public String author;
    public String cover;
    public String intro;
    public int duration;
    public long passtime;
    public long coinNum;
    public long playCount;
    public long collectCount;
    public long commentCount;
    public long shareCount;
    public String lyricUrl;

    public AudioInfo() {}

    public AudioInfo(long sid, String title, String author, String cover, int duration) {
        this.sid = sid;
        this.title = title;
        this.author = author;
        this.cover = cover;
        this.duration = duration;
    }
}