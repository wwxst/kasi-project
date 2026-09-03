package com.kasi.backend.provider.goodshort.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class GoodShortEpisodeData {
    @JsonAlias({"episodeId", "id"})
    private String episodeId;
    @JsonAlias({"episodeNo", "episodeNumber", "sequenceNo", "seq"})
    private Integer episodeNo;
    private String title;
    @JsonAlias({"isFree", "free"})
    private Boolean isFree;
    @JsonAlias({"duration", "durationSeconds"})
    private Integer duration;
    @JsonAlias({"updateTime", "updatedAt"})
    private String updateTime;
}
