package com.kasi.backend.sms.gateway;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import tools.jackson.databind.ObjectMapper;
import com.kasi.backend.common.exception.VerificationDeliveryUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AliyunSmsGateway implements SmsGateway {
    private final ObjectMapper objectMapper;
    @Override
    public SmsSendResult send(SmsSendCommand command) {
        try {
            Client client = new Client(new Config().setAccessKeyId(command.accessKeyId())
                    .setAccessKeySecret(command.accessKeySecret()).setEndpoint("dysmsapi.aliyuncs.com"));
            SendSmsRequest request = new SendSmsRequest().setPhoneNumbers(command.mobile())
                    .setSignName(command.signName()).setTemplateCode(command.templateCode())
                    .setTemplateParam(objectMapper.writeValueAsString(Map.of("code", command.code())));
            SendSmsResponse response = client.sendSms(request);
            return new SmsSendResult(response.getBody().getCode(), response.getBody().getRequestId());
        } catch (Exception exception) {
            throw new VerificationDeliveryUnavailableException(exception);
        }
    }
}
