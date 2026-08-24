package com.kasi.backend.provider.goodshort;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.goodshort.dto.GoodShortConnectionProbeRequest;
import com.kasi.backend.provider.goodshort.dto.GoodShortFilingData;
import com.kasi.backend.provider.goodshort.dto.GoodShortResponse;
import com.kasi.backend.provider.spi.AccountFilingProviderAdapter;
import com.kasi.backend.provider.spi.AccountFilingQuery;
import com.kasi.backend.provider.spi.AccountFilingResult;
import com.kasi.backend.provider.spi.AccountFilingSubmission;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.PromotionLinkProviderAdapter;
import com.kasi.backend.provider.spi.PromotionLinkRequest;
import com.kasi.backend.provider.spi.PromotionLinkResult;
import com.kasi.backend.provider.spi.DramaCatalogFetchRequest;
import com.kasi.backend.provider.spi.DramaCatalogPage;
import com.kasi.backend.provider.spi.DramaCatalogProviderAdapter;
import com.kasi.backend.provider.spi.ProviderDramaContentRecord;
import com.kasi.backend.provider.spi.ProviderDramaRecord;
import com.kasi.backend.provider.goodshort.dto.GoodShortBookData;
import com.kasi.backend.provider.goodshort.dto.GoodShortCatalogResponse;
import com.kasi.backend.provider.goodshort.dto.GoodShortEpisodeData;
import com.kasi.backend.provider.goodshort.dto.GoodShortPromotionLinkResponse;
import com.kasi.backend.provider.goodshort.dto.GoodShortOrderResponse;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import com.kasi.backend.provider.spi.OrderSyncProviderAdapter;
import com.kasi.backend.provider.spi.OrderSyncRequest;
import com.kasi.backend.provider.spi.ProviderOrderPage;
import com.kasi.backend.provider.spi.ProviderOrderRecord;
import com.kasi.backend.provider.spi.ProviderOrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class GoodShortAdapter implements AccountFilingProviderAdapter, DramaCatalogProviderAdapter,
        PromotionLinkProviderAdapter, OrderSyncProviderAdapter {

    private static final String PROVIDER_CODE = "GOODSHORT";
    private static final String CONNECTION_PROBE_PATH = "/open/book/initBooks";
    private static final String FILING_REPORT_PATH = "/open/filing/report";
    private static final String FILING_QUERY_PATH = "/open/filing/query";
    private static final String FULL_CATALOG_PATH = "/open/book/initBooks";
    private static final String INCREMENTAL_CATALOG_PATH = "/open/book/incrementBooks";
    private static final String ORDER_PATH = "/open/partner/orders";
    private static final Set<ProviderCapability> CAPABILITIES = Set.of(
            ProviderCapability.FULL_DRAMA_SYNC,
            ProviderCapability.INCREMENTAL_DRAMA_SYNC,
            ProviderCapability.FREE_CONTENT_PREVIEW,
            ProviderCapability.SINGLE_DOWNLOAD,
            ProviderCapability.BATCH_DOWNLOAD,
            ProviderCapability.ACCOUNT_FILING,
            ProviderCapability.FILING_STATUS_QUERY,
            ProviderCapability.PROMOTION_LINK,
            ProviderCapability.PROMOTION_CODE,
            ProviderCapability.ORDER_SYNC);
    private static final DateTimeFormatter REMOTE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final DateTimeFormatter REMOTE_DATE_FORMAT_WITHOUT_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final DateTimeFormatter ORDER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;
    private final GoodShortSigner signer;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    public GoodShortAdapter(@Qualifier("goodShortRestClient") RestClient restClient,
                            GoodShortSigner signer, Clock clock,
                            tools.jackson.databind.ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.signer = signer;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    GoodShortAdapter(RestClient restClient, GoodShortSigner signer, Clock clock) {
        this(restClient, signer, clock, tools.jackson.databind.json.JsonMapper.builder().build());
    }

    @Override
    public String providerCode() { return PROVIDER_CODE; }

    @Override
    public Set<ProviderCapability> capabilities() { return CAPABILITIES; }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.TIKTOK, MediaType.FACEBOOK, MediaType.YOUTUBE, MediaType.INSTAGRAM);
    }

    @Override
    public ProviderConnectionTestVO testConnection(ProviderConnectionSecret connection) {
        long timestamp = clock.millis();
        GoodShortConnectionProbeRequest request = GoodShortConnectionProbeRequest.builder()
                .pageNo(1).pageSize(1).language("ENGLISH").pid(connection.getPartnerId())
                .timestamp(timestamp).build();
        String signature = signer.sign(signatureParameters(request), connection.getApiKey());
        GoodShortResponse response;
        try {
            response = restClient.mutate().baseUrl(connection.getBaseUrl()).build().post()
                    .uri(CONNECTION_PROBE_PATH).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("sign", signature).body(request).retrieve().body(GoodShortResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_UNAVAILABLE);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new BusinessException(ErrorCode.PROVIDER_REMOTE_UNAVAILABLE);
            }
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_REJECTED);
        }
        if (!successful(response)) throw new BusinessException(ErrorCode.PROVIDER_REMOTE_REJECTED);
        return ProviderConnectionTestVO.builder().reachable(true).message(response.getMessage())
                .testedAt(Instant.ofEpochMilli(timestamp)).build();
    }

    @Override
    public void submitAccountFiling(ProviderConnectionSecret connection, AccountFilingSubmission submission) {
        Map<String, Object> parameters = filingParameters(connection, submission);
        GoodShortResponse response = post(connection, FILING_REPORT_PATH, parameters);
        if (!successful(response)) throw new ProviderRemoteRejectedException("GoodShort 报备请求被拒绝");
    }

    @Override
    public AccountFilingResult queryAccountFiling(ProviderConnectionSecret connection, AccountFilingQuery query) {
        long timestamp = clock.millis();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pid", connection.getPartnerId());
        parameters.put("timestamp", timestamp);
        parameters.put("type", "ACCOUNT");
        parameters.put("media", query.mediaType().name());
        parameters.put("accountId", query.externalAccountId());
        GoodShortResponse response = post(connection, FILING_QUERY_PATH, parameters);
        if (!successful(response) || response.getData() == null) {
            throw new ProviderRemoteRejectedException("GoodShort 报备查询被拒绝");
        }
        GoodShortFilingData data = response.getData();
        FilingStatus status = switch (data.getStatus() == null ? -1 : data.getStatus()) {
            case 0 -> FilingStatus.PENDING;
            case 1 -> FilingStatus.APPROVED;
            case 2 -> FilingStatus.FAILED;
            default -> throw new ProviderRemoteRejectedException("GoodShort 返回未知报备状态");
        };
        return new AccountFilingResult(status, String.valueOf(data.getStatus()),
                firstNonBlank(data.getExternalFilingId(), data.getFilingId()),
                parseRemoteTime(data.getFilingTime()), parseRemoteTime(data.getOperateTime()));
    }

    @Override
    public DramaCatalogPage fetchFullDramas(ProviderConnectionSecret connection, DramaCatalogFetchRequest request) {
        return fetchCatalog(connection, request, FULL_CATALOG_PATH, false);
    }

    @Override
    public DramaCatalogPage fetchIncrementalDramas(ProviderConnectionSecret connection,
                                                   DramaCatalogFetchRequest request) {
        return fetchCatalog(connection, request, INCREMENTAL_CATALOG_PATH, true);
    }

    @Override
    public PromotionLinkResult generatePromotionLink(ProviderConnectionSecret connection,
                                                      PromotionLinkRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pid", connection.getPartnerId());
        parameters.put("bookId", request.externalDramaId());
        parameters.put("customParams", request.trackingNo());
        parameters.put("shareUrlType", "ONELINK".equalsIgnoreCase(request.landingType()) ? 2 : 1);
        parameters.put("codeMedia", mapCodeMedia(request.mediaType()));
        parameters.put("timestamp", clock.millis());
        GoodShortPromotionLinkResponse response = postPromotionLink(connection,
                "/open/inviteCode/generate/partner/code", parameters);
        if (!successful(response) || response.getData() == null
                || response.getData().getCode() == null || response.getData().getShareUrl() == null) {
            throw new ProviderRemoteRejectedException("GoodShort推广链接生成被拒绝");
        }
        return new PromotionLinkResult(response.getData().getCode(), response.getData().getShareUrl(),
                response.getData().getCustomParams());
    }

    @Override
    public ProviderOrderPage fetchOrders(ProviderConnectionSecret connection, OrderSyncRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pid", connection.getPartnerId());
        parameters.put("timestamp", clock.millis());
        parameters.put("pageNo", request.pageNo());
        parameters.put("pageSize", request.pageSize());
        parameters.put("startDate", request.startDate().format(ORDER_DATE_FORMAT));
        parameters.put("endDate", request.endDate().format(ORDER_DATE_FORMAT));

        GoodShortOrderResponse response = postOrders(connection, parameters);
        if (!successful(response) || response.getData() == null || response.getData().getRecords() == null) {
            throw new ProviderRemoteRejectedException("GoodShort order response is incomplete");
        }
        var data = response.getData();
        List<ProviderOrderRecord> records = data.getRecords().stream()
                .map(record -> mapOrder(record, connection.getCurrency()))
                .toList();
        int pageNo = data.getPageNo() == null ? request.pageNo() : data.getPageNo();
        int pageSize = data.getPageSize() == null ? request.pageSize() : data.getPageSize();
        int pages = data.getPages() == null ? pageNo : data.getPages();
        long total = data.getTotal() == null ? records.size() : data.getTotal();
        boolean hasNext = !records.isEmpty() && pageNo < pages && (long) pageNo * pageSize < total;
        return new ProviderOrderPage(records, pageNo, pageSize, pages, total, hasNext);
    }

    private ProviderOrderRecord mapOrder(Map<String, Object> record, String currency) {
        String externalOrderId = stringValue(record.get("orderId"));
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new ProviderRemoteRejectedException("GoodShort orderId is missing");
        }
        long amountMinor = longValue(record.get("payMoney"));
        String rawStatus = stringValue(record.get("payStatus"));
        ProviderOrderStatus status = switch (rawStatus == null ? "" : rawStatus) {
            case "0" -> ProviderOrderStatus.UNPAID;
            case "1" -> ProviderOrderStatus.PAID;
            case "3" -> ProviderOrderStatus.REFUNDED;
            default -> ProviderOrderStatus.UNKNOWN;
        };
        return new ProviderOrderRecord(
                externalOrderId,
                stringValue(record.get("userId")),
                amountMinor,
                BigDecimal.valueOf(amountMinor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY),
                currency,
                parseOrderTime(stringValue(record.get("payTime"))),
                status,
                rawStatus,
                stringValue(record.get("customParams")),
                stringValue(record.get("bookId")),
                stringValue(record.get("searchCode")),
                stringValue(record.get("channelCode")),
                stringValue(record.get("pid")),
                parseOrderTime(stringValue(record.get("utime"))),
                writeJson(record));
    }

    private GoodShortOrderResponse postOrders(ProviderConnectionSecret connection,
                                               Map<String, Object> parameters) {
        String signature = signer.sign(parameters, connection.getApiKey());
        try {
            return restClient.mutate().baseUrl(connection.getBaseUrl()).build().post().uri(ORDER_PATH)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("sign", signature)
                    .body(parameters).retrieve().body(GoodShortOrderResponse.class);
        } catch (ResourceAccessException exception) {
            throw new ProviderTransientException("GoodShort order service is temporarily unavailable");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError() || exception.getStatusCode().value() == 429) {
                throw new ProviderTransientException("GoodShort order service is temporarily unavailable");
            }
            throw new ProviderRemoteRejectedException("GoodShort order request was rejected");
        } catch (RestClientException exception) {
            throw new ProviderRemoteRejectedException("GoodShort order response is malformed");
        }
    }

    private boolean successful(GoodShortOrderResponse response) {
        return response != null && Integer.valueOf(0).equals(response.getStatus())
                && Boolean.TRUE.equals(response.getSuccess());
    }

    private LocalDateTime parseOrderTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value, ORDER_DATE_FORMAT);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new ProviderRemoteRejectedException("GoodShort order payload cannot be preserved");
        }
    }

    private DramaCatalogPage fetchCatalog(ProviderConnectionSecret connection,
                                          DramaCatalogFetchRequest request,
                                          String path,
                                          boolean incremental) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pageNo", request.pageNo());
        parameters.put("pageSize", request.pageSize());
        parameters.put("language", request.language());
        parameters.put("pid", connection.getPartnerId());
        parameters.put("timestamp", clock.millis());
        if (incremental && request.updateTime() != null) {
            parameters.put("updateTime", request.updateTime());
        }
        GoodShortCatalogResponse response = postCatalog(connection, path, parameters);
        if (!successful(response) || response.getData() == null) {
            throw new ProviderRemoteRejectedException("GoodShort catalog request rejected");
        }
        GoodShortCatalogResponse.GoodShortCatalogData data = response.getData();
        var books = data.getItems();
        if (books == null) {
            throw new ProviderRemoteRejectedException("GoodShort鐩綍鍝嶅簲缂哄皯data.items");
        }
        int pageNo = data.getPageNo() == null ? request.pageNo() : data.getPageNo();
        int pageSize = data.getPageSize() == null ? request.pageSize() : data.getPageSize();
        long total = data.getTotal() == null ? books.size() : data.getTotal();
        boolean hasNext = data.getHasNext() != null
                ? data.getHasNext()
                : (pageSize > 0 && ((long) pageNo * pageSize < total));
        return new DramaCatalogPage(books.stream().map(book -> mapBook(book, request.language())).toList(),
                pageNo, pageSize, total, hasNext,
                data.getNextUpdateTime() == null ? data.getUpdateTime() : data.getNextUpdateTime());
    }

    private GoodShortCatalogResponse postCatalog(ProviderConnectionSecret connection,
                                                  String path,
                                                  Map<String, Object> parameters) {
        String signature = signer.sign(parameters, connection.getApiKey());
        try {
            return restClient.mutate().baseUrl(connection.getBaseUrl()).build().post().uri(path)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("sign", signature)
                    .body(parameters).retrieve().body(GoodShortCatalogResponse.class);
        } catch (ResourceAccessException exception) {
            throw new ProviderTransientException("GoodShort network temporarily unavailable");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError() || exception.getStatusCode().value() == 429) {
                throw new ProviderTransientException("GoodShort service temporarily unavailable");
            }
            throw new ProviderRemoteRejectedException("GoodShort request rejected");
        } catch (RestClientException exception) {
            throw new ProviderRemoteRejectedException("GoodShort response is malformed");
        }
    }

    private ProviderDramaRecord mapBook(GoodShortBookData book, String requestedLanguage) {
        if (book.getBookId() == null || book.getBookId().isBlank()) {
            throw new ProviderRemoteRejectedException("GoodShort鐭墽缂哄皯bookId");
        }
        var episodes = book.getEpisodes() == null ? java.util.List.<GoodShortEpisodeData>of() : book.getEpisodes();
        return new ProviderDramaRecord(book.getBookId(), book.getBookName(), book.getOriginalBookName(),
                book.getIntroduction(), book.getCover(),
                firstNonBlank(book.getLanguage(), requestedLanguage), book.getType(), book.getShowStatus(),
                parseRemoteTimeFlexible(book.getUpdateTime()),
                episodes.stream().map(this::mapEpisode).toList());
    }

    private ProviderDramaContentRecord mapEpisode(GoodShortEpisodeData episode) {
        if (episode == null || episode.getEpisodeNo() == null || episode.getEpisodeNo() < 1) {
            throw new ProviderRemoteRejectedException("GoodShort鍓ч泦缂哄皯episodeNo");
        }
        return new ProviderDramaContentRecord(episode.getEpisodeId(), episode.getEpisodeNo(), episode.getTitle(),
                Boolean.TRUE.equals(episode.getIsFree()), episode.getDuration(),
                parseRemoteTimeFlexible(episode.getUpdateTime()));
    }

    private Map<String, Object> filingParameters(ProviderConnectionSecret connection,
                                                  AccountFilingSubmission submission) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pid", connection.getPartnerId());
        parameters.put("timestamp", clock.millis());
        parameters.put("type", "ACCOUNT");
        parameters.put("media", submission.mediaType().name());
        parameters.put("accountId", submission.externalAccountId());
        putIfNotBlank(parameters, "accountName", submission.accountName());
        putIfNotBlank(parameters, "accountLink", submission.accountLink());
        return parameters;
    }

    private GoodShortResponse post(ProviderConnectionSecret connection, String path,
                                   Map<String, Object> parameters) {
        String signature = signer.sign(parameters, connection.getApiKey());
        try {
            return restClient.mutate().baseUrl(connection.getBaseUrl()).build().post().uri(path)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("sign", signature)
                    .body(parameters).retrieve().body(GoodShortResponse.class);
        } catch (ResourceAccessException exception) {
            throw new ProviderTransientException("GoodShort 网络暂时不可用");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError() || exception.getStatusCode().value() == 429) {
                throw new ProviderTransientException("GoodShort 服务暂时不可用");
            }
            throw new ProviderRemoteRejectedException("GoodShort 请求被拒绝");
        }
    }

    private Map<String, Object> signatureParameters(GoodShortConnectionProbeRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pageNo", request.getPageNo());
        parameters.put("pageSize", request.getPageSize());
        parameters.put("language", request.getLanguage());
        parameters.put("pid", request.getPid());
        parameters.put("timestamp", request.getTimestamp());
        return parameters;
    }

    private boolean successful(GoodShortResponse response) {
        return response != null && Integer.valueOf(0).equals(response.getStatus())
                && Boolean.TRUE.equals(response.getSuccess());
    }

    private GoodShortPromotionLinkResponse postPromotionLink(ProviderConnectionSecret connection,
                                                              String path,
                                                              Map<String, Object> parameters) {
        String signature = signer.sign(parameters, connection.getApiKey());
        try {
            return restClient.mutate().baseUrl(connection.getBaseUrl()).build().post().uri(path)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("sign", signature)
                    .body(parameters).retrieve().body(GoodShortPromotionLinkResponse.class);
        } catch (ResourceAccessException exception) {
            throw new ProviderTransientException("GoodShort网络暂时不可用");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError() || exception.getStatusCode().value() == 429) {
                throw new ProviderTransientException("GoodShort服务暂时不可用");
            }
            throw new ProviderRemoteRejectedException("GoodShort推广链接请求被拒绝");
        } catch (RestClientException exception) {
            throw new ProviderRemoteRejectedException("GoodShort推广链接响应格式错误");
        }
    }

    private boolean successful(GoodShortCatalogResponse response) {
        return response != null && Integer.valueOf(0).equals(response.getStatus())
                && Boolean.TRUE.equals(response.getSuccess());
    }

    private boolean successful(GoodShortPromotionLinkResponse response) {
        return response != null && Integer.valueOf(0).equals(response.getStatus())
                && Boolean.TRUE.equals(response.getSuccess());
    }

    private String mapCodeMedia(MediaType mediaType) {
        if (mediaType == null) return "UNKNOWN";
        return switch (mediaType) {
            case TIKTOK -> "TIKTOK";
            case FACEBOOK -> "FACEBOOK";
            case YOUTUBE -> "YOUTUBE";
            case INSTAGRAM -> "INSTAGRAM";
        };
    }

    private void putIfNotBlank(Map<String, Object> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value);
    }

    private LocalDateTime parseRemoteTime(String value) {
        if (value == null || value.isBlank()) return null;
        return OffsetDateTime.parse(value, REMOTE_DATE_FORMAT).toLocalDateTime();
    }

    private LocalDateTime parseRemoteTimeFlexible(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return parseRemoteTime(value);
        } catch (RuntimeException ignored) {
            try {
                return OffsetDateTime.parse(value, REMOTE_DATE_FORMAT_WITHOUT_MILLIS).toLocalDateTime();
            } catch (RuntimeException ignoredCompactOffsetDateTime) {
                try {
                    return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
                } catch (RuntimeException ignoredOffsetDateTime) {
                    try {
                        return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (RuntimeException ignoredLocalDateTime) {
                        try {
                            return Instant.ofEpochMilli(Long.parseLong(value)).atZone(clock.getZone()).toLocalDateTime();
                        } catch (RuntimeException exception) {
                            throw new ProviderRemoteRejectedException("GoodShort time format is invalid");
                        }
                    }
                }
            }
        }
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
