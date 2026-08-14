package com.RobinNotBad.BiliClient.model;

public class SettingSection {
    public final String type;
    public final String id;
    public final String name;
    public final String desc;
    public final String defaultValue;
    public Object extra;
    public String oppositeKey;

    public SettingSection(String type, String name, String key, String desc, String defaultValue) {
        this.type = type;
        this.id = key;
        this.name = name;
        this.desc = desc;
        this.defaultValue = defaultValue;
    }

    public SettingSection(String type, String name, String key, String desc, String defaultValue, Object extra) {
        this.type = type;
        this.id = key;
        this.name = name;
        this.desc = desc;
        this.defaultValue = defaultValue;
        this.extra = extra;
    }

    public SettingSection(String type, String name, String key, String desc, String defaultValue, Object extra, String oppositeKey) {
        this.type = type;
        this.id = key;
        this.name = name;
        this.desc = desc;
        this.defaultValue = defaultValue;
        this.extra = extra;
        this.oppositeKey = oppositeKey;
    }
}
