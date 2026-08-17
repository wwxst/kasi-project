package com.kasi.backend.provider.goodshort;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.goodshort.dto.GoodShortConnectionProbeRequest;
import com.kasi.backend.provider.goodshort.dto.GoodShortResponse;
import com.kasi.backend.provider.spi.ProviderAdapter;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class GoodShortAdapter implements ProviderAdapter {

    private static final String PROVIDER_CODE = "GOODSHORT";
    private static final String CONNECTION_PROBE_PATH = "/open/book/initBooks";
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
            ProviderCapability.ORDER_SYNC,
            ProviderCapability.ANALYTICS_SYNC);

    private final RestClient restClient;
    private final GoodShortSigner signer;
    private final Clock clock;

    public GoodShortAdapter(@Qualifier("goodShortRestClient") RestClient restClient,
                            GoodShortSigner signer, Clock clock) {
        this.restClient = restClient;
        this.signer = signer;
        this.clock = clock;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public Set<ProviderCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ProviderConnectionTestVO testConnection(ProviderConnectionSecret connection) {
        long timestamp = clock.millis();
        GoodShortConnectionProbeRequest request = GoodShortConnectionProbeRequest.builder()
                .pageNo(1)
                .pageSize(1)
                .language("ENGLISH")
                .pid(connection.getPartnerId())
                .timestamp(timestamp)
                .build();
        String signature = signer.sign(signatureParameters(request), connection.getApiKey());

        GoodShortResponse response;
        try {
            response = restClient.post()
                    .uri(CONNECTION_PROBE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("sign", signature)
                    .body(request)
                    .retrieve()
                    .body(GoodShortResponse.class);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_UNAVAILABLE);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new BusinessException(ErrorCode.PROVIDER_REMOTE_UNAVAILABLE);
            }
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_REJECTED);
        }

        if (response == null || !Integer.valueOf(0).equals(response.getStatus())
                || !Boolean.TRUE.equals(response.getSuccess())) {
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_REJECTED);
        }
        return ProviderConnectionTestVO.builder()
                .reachable(true)
                .message(response.getMessage())
                .testedAt(Instant.ofEpochMilli(timestamp))
                .build();
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
}
