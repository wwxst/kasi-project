package com.kasi.backend.scheduledtask.enums;

public enum ScheduledTaskCode {
    GOODSHORT_DRAMA_INCREMENTAL_SYNC("GoodShort 短剧增量同步"),
    GOODSHORT_DRAMA_CONTENT_SYNC("GoodShort 免费剧集同步"),
    GOODSHORT_ORDER_SYNC("GoodShort 订单同步"),
    GOODSHORT_ANALYTICAL_REPORT_SYNC("GoodShort analytical report sync");

    private final String title;
    ScheduledTaskCode(String title) { this.title = title; }
    public String title() { return title; }
}
