package com.RobinNotBad.BiliClient.model;

import java.io.Serializable;
import java.util.List;

public class Playlist implements Serializable {
    public long id;
    public long uid;
    public String uname;
    public String title;
    public int type;
    public int published;
    public String cover;
    public long ctime;
    public int songCount;
    public String desc;
    public List<Long> sids;
    public long menuId;
    public long playCount;
    public long collectCount;
    public long shareCount;

    public Playlist() {}
}