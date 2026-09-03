package com.kasi.backend.sms.mapper;

import com.kasi.backend.sms.entity.SmsConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsConfigMapper {

    SmsConfig findSingleton();

    int insert(SmsConfig config);

    int update(SmsConfig config);
}
