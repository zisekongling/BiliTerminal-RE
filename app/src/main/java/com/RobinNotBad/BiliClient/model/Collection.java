package com.RobinNotBad.BiliClient.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Collection implements Parcelable, Serializable {
    public int id;
    public String title;
    public String cover;
    public String intro;
    public long mid;
    public List<Section> sections = new ArrayList<>();
    public List<VideoCard> cards = new ArrayList<>();
    public String view;

    public static class Section implements Parcelable, Serializable {
        public int season_id;
        public int id;
        public String title;
        public int type;
        public List<Episode> episodes = new ArrayList<>();

        public Section() {
        }

        protected Section(Parcel in) {
            season_id = in.readInt();
            id = in.readInt();
            title = in.readString();
            type = in.readInt();
            episodes = in.createTypedArrayList(Episode.CREATOR);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(season_id);
            dest.writeInt(id);
            dest.writeString(title);
            dest.writeInt(type);
            dest.writeTypedList(episodes);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Section> CREATOR = new Creator<>() {
            @Override
            public Section createFromParcel(Parcel in) {
                return new Section(in);
            }

            @Override
            public Section[] newArray(int size) {
                return new Section[size];
            }
        };
    }

    public static class Episode implements Parcelable, Serializable {
        public int season_id;
        public int section_id;
        public long id;
        public long aid;
        public long cid;
        public String title;
        public String bvid;
        public VideoInfo arc;

        public Episode() {
        }

        protected Episode(Parcel in) {
            season_id = in.readInt();
            section_id = in.readInt();
            id = in.readLong();
            aid = in.readLong();
            cid = in.readLong();
            title = in.readString();
            bvid = in.readString();
            arc = in.readParcelable(VideoInfo.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(season_id);
            dest.writeInt(section_id);
            dest.writeLong(id);
            dest.writeLong(aid);
            dest.writeLong(cid);
            dest.writeString(title);
            dest.writeString(bvid);
            dest.writeParcelable(arc, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Episode> CREATOR = new Creator<>() {
            @Override
            public Episode createFromParcel(Parcel in) {
                return new Episode(in);
            }

            @Override
            public Episode[] newArray(int size) {
                return new Episode[size];
            }
        };
    }

    public Collection() {
    }

    protected Collection(Parcel in) {
        id = in.readInt();
        title = in.readString();
        cover = in.readString();
        intro = in.readString();
        mid = in.readLong();
        sections = in.createTypedArrayList(Section.CREATOR);
        cards = in.createTypedArrayList(VideoCard.CREATOR);
        view = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(title);
        dest.writeString(cover);
        dest.writeString(intro);
        dest.writeLong(mid);
        dest.writeTypedList(sections);
        dest.writeTypedList(cards);
        dest.writeString(view);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Collection> CREATOR = new Creator<>() {
        @Override
        public Collection createFromParcel(Parcel in) {
            return new Collection(in);
        }

        @Override
        public Collection[] newArray(int size) {
            return new Collection[size];
        }
    };

    /**
     * 交给播放器的轻量副本：**剥掉每集的 {@link Episode#arc}**，其余原样保留。
     *
     * <p>每一集的 arc 都是一份完整 {@code VideoInfo}，几百上千集的合集光是这一项就上兆，
     * 塞进 Intent 会直接撞 binder 事务上限（崩溃 #201）。播放器只要 aid / cid / 标题
     * （见 {@link #toPlaylist}），arc 是合集详情页画卡片用的，走不到播放器那条路。
     * 改完不原地修改入参，返回新对象——同一个 Collection 实例详情页还要接着用。
     */
    public Collection slimForTransfer() {
        Collection copy = new Collection();
        copy.id = id;
        copy.title = title;
        copy.cover = cover;
        copy.intro = intro;
        copy.mid = mid;
        copy.view = view;
        // cards 是「收藏的合集」那种来源，条目本身就是轻量 VideoCard，直接共用即可
        copy.cards = cards;
        if (sections != null) {
            copy.sections = new ArrayList<>(sections.size());
            for (Section section : sections) {
                if (section == null) continue;
                Section slimSection = new Section();
                slimSection.season_id = section.season_id;
                slimSection.id = section.id;
                slimSection.title = section.title;
                slimSection.type = section.type;
                if (section.episodes != null) {
                    slimSection.episodes = new ArrayList<>(section.episodes.size());
                    for (Episode episode : section.episodes) {
                        if (episode == null) continue;
                        Episode slimEpisode = new Episode();
                        slimEpisode.season_id = episode.season_id;
                        slimEpisode.section_id = episode.section_id;
                        slimEpisode.id = episode.id;
                        slimEpisode.aid = episode.aid;
                        slimEpisode.cid = episode.cid;
                        slimEpisode.title = episode.title;
                        slimEpisode.bvid = episode.bvid;
                        // arc 刻意不带：它才是体积的全部来源
                        slimSection.episodes.add(slimEpisode);
                    }
                }
                copy.sections.add(slimSection);
            }
        }
        return copy;
    }

    /**
     * 合集里的一集，只留"当播放列表条目"要用的三个字段。
     *
     * 独立于 [Episode] 是因为它要跨两种来源：`ugc_season` 来的走 `sections[].episodes[]`，
     * 收藏的合集来的走 `cards`（见 [toPlaylist]）。
     */
    public static class PlaylistItem {
        public final long aid;
        public final long cid;
        public final String title;

        public PlaylistItem(long aid, long cid, String title) {
            this.aid = aid;
            this.cid = cid;
            this.title = title == null ? "" : title;
        }
    }

    /**
     * 把合集展平成一条有序的播放列表（**纯函数，有 JVM 单测**）。
     *
     * <p>顺序 = 节的自然顺序、节内按集顺序（B 站合集页就是这么展示的）。这正是不做展平就
     * 拿不到的东西：收藏夹里的顺序是"你什么时候收藏的"，与合集官方顺序无关。
     *
     * <p>两种来源都要吃：`x/web-interface/view` 的 `ugc_season` 带 `sections`；
     * 而"收藏的合集"（`getFavoritedCollections`）只填了 `cards`、`sections` 为空。
     *
     * <p>aid 或 cid 缺失（脏数据/未过审）的条目直接跳过——留着只会让播放器取流出错。
     */
    public static List<PlaylistItem> toPlaylist(Collection collection) {
        List<PlaylistItem> items = new ArrayList<>();
        if (collection == null) return items;

        if (collection.sections != null) {
            for (Section section : collection.sections) {
                if (section == null || section.episodes == null) continue;
                for (Episode episode : section.episodes) {
                    if (episode == null) continue;
                    if (episode.aid <= 0 || episode.cid <= 0) continue;
                    items.add(new PlaylistItem(episode.aid, episode.cid, episode.title));
                }
            }
        }

        if (items.isEmpty() && collection.cards != null) {
            for (VideoCard card : collection.cards) {
                if (card == null) continue;
                if (card.aid <= 0 || card.cid <= 0) continue;
                items.add(new PlaylistItem(card.aid, card.cid, card.title));
            }
        }
        return items;
    }
}
