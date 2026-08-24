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
    @JsonAlias({"originalBookName", "originalName", "originalTitle"})
    private String originalBookName;
    @JsonAlias({"introduction", "description", "intro"})
    private String introduction;
    @JsonAlias({"cover", "coverUrl", "coverImage"})
    private String cover;
    private String language;
    @JsonAlias({"type", "dramaType"})
    private String type;
    @JsonAlias({"showStatus", "status"})
    private String showStatus;
    @JsonAlias({"updateTime", "updatedAt"})
    private String updateTime;
    @JsonAlias({"episodes", "contents", "episodeList"})
    private List<GoodShortEpisodeData> episodes;
}
