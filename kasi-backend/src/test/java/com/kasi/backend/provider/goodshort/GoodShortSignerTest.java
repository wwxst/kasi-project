package com.kasi.backend.provider.goodshort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoodShort签名器")
class GoodShortSignerTest {

    private final GoodShortSigner signer = new GoodShortSigner();

    @Test
    @DisplayName("按官方固定向量生成大写MD5签名")
    void signMatchesOfficialVector() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pid", "123456");
        params.put("timestamp", 1681810530092L);
        params.put("pageNo", 1);
        params.put("pageSize", 10);

        assertThat(signer.sign(params, "aaabbbccc"))
                .isEqualTo("973FB9A689D3924CAC1967EF6E0BD012");
    }

    @Test
    @DisplayName("签名排除sign空值和空白字符串")
    void signExcludesSignatureAndEmptyValues() {
        Map<String, Object> withEmptyValues = new LinkedHashMap<>();
        withEmptyValues.put("pid", "123456");
        withEmptyValues.put("pageNo", 1);
        withEmptyValues.put("sign", "ignored");
        withEmptyValues.put("nullable", null);
        withEmptyValues.put("blank", "   ");

        Map<String, Object> expectedValues = new LinkedHashMap<>();
        expectedValues.put("pageNo", 1);
        expectedValues.put("pid", "123456");

        assertThat(signer.sign(withEmptyValues, "aaabbbccc"))
                .isEqualTo(signer.sign(expectedValues, "aaabbbccc"));
    }

    @Test
    @DisplayName("参数名大小写参与签名且不进行URL编码")
    void signKeepsCaseAndRawValues() {
        assertThat(signer.sign(Map.of("pageNo", "a b&c"), "key"))
                .isNotEqualTo(signer.sign(Map.of("pageno", "a b&c"), "key"));
    }
}
