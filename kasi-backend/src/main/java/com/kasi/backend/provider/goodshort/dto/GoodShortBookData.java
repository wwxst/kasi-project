package com.kasi.backend.provider.goodshort.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class GoodShortBookData {
    @JsonAlias({"bookId", "id"})
    private String bookId;
    @JsonAlias({"bookName", "name", "title"})
    private String bookName;
    private String bookNameZh;
    @JsonAlias({"originalBookName", "originalName", "originalTitle"})
    private String originalBookName;
    @JsonAlias({"introduce", "introduction", "description", "intro"})
    private String introduction;
    @JsonAlias({"bookCover", "cover", "coverUrl", "coverImage"})
    private String cover;
    private List<String> labelNames;
    private String typeTwoName;
    private String language;
    private Integer rank;
    @JsonAlias({"type", "dramaType"})
    private String type;
    private String novelType;
    private Integer novelSubType;
    @JsonAlias({"showStatus", "status"})
    private String showStatus;
    private String ctime;
    private String utime;
    @JsonAlias({"updateTime", "updatedAt"})
    private String updateTime;
    @JsonAlias({"episodes", "contents", "episodeList"})
    private List<GoodShortEpisodeData> episodes;
}
