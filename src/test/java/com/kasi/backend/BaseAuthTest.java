package com.kasi.backend;

import tools.jackson.databind.ObjectMapper;
import com.kasi.backend.config.TestRedisConfig;
import com.kasi.backend.auth.service.TestVerificationCodeSender;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * 认证模块测试基类
 * <p>
 * 使用H2内存数据库 + 嵌入式Redis，每个测试方法前清理数据并插入基础测试数据。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
public abstract class BaseAuthTest {

    @Autowired
    protected WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected TestVerificationCodeSender testVerificationCodeSender;

    /** 测试超级管理员账号 */
    protected static final String ADMIN_USERNAME = "kasiadmin";
    /** 测试管理员密码（明文） */
    protected static final String ADMIN_PASSWORD = "kasi123456";
    /** 测试用户密码（明文） */
    protected static final String USER_PASSWORD = "user123456";

    @BeforeEach
    void baseSetUp() {
        // 手动构建MockMvc
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        testVerificationCodeSender.clear();

        // 清理所有Redis键（验证码、Token等）
        Set<String> redisKeys = redisTemplate.keys("vc:*");
        if (redisKeys != null && !redisKeys.isEmpty()) {
            redisTemplate.delete(redisKeys);
        }
        redisKeys = redisTemplate.keys("pwd:*");
        if (redisKeys != null && !redisKeys.isEmpty()) {
            redisTemplate.delete(redisKeys);
        }
        redisKeys = redisTemplate.keys("auth:*");
        if (redisKeys != null && !redisKeys.isEmpty()) {
            redisTemplate.delete(redisKeys);
        }

        // 清理数据库表
        jdbcTemplate.execute("DELETE FROM promotion_user");
        jdbcTemplate.execute("DELETE FROM sys_admin_user");

        // 插入测试管理员
        jdbcTemplate.update(
                "INSERT INTO sys_admin_user (username, password, real_name, status, is_super_admin) VALUES (?, ?, ?, ?, ?)",
                ADMIN_USERNAME, passwordEncoder.encode(ADMIN_PASSWORD), "系统管理员", 1, 1);

        // 插入禁用管理员
        jdbcTemplate.update(
                "INSERT INTO sys_admin_user (username, password, real_name, status) VALUES (?, ?, ?, ?)",
                "disabledadmin", passwordEncoder.encode(ADMIN_PASSWORD), "已禁用管理员", 0);

        // 插入普通管理员
        jdbcTemplate.update(
                "INSERT INTO sys_admin_user (username, password, real_name, status, is_super_admin) VALUES (?, ?, ?, ?, ?)",
                "operator", passwordEncoder.encode(ADMIN_PASSWORD), "运营管理员", 1, 0);

        // 插入测试普通用户
        jdbcTemplate.update(
                "INSERT INTO promotion_user (user_no, password, nickname, mobile, email, status) VALUES (?, ?, ?, ?, ?, ?)",
                "KS000001", passwordEncoder.encode(USER_PASSWORD), "测试用户", "13800138000", "test@example.com", 1);

        // 插入一个仅有手机号的用户（用于忘记密码测试）
        jdbcTemplate.update(
                "INSERT INTO promotion_user (user_no, password, nickname, mobile, status) VALUES (?, ?, ?, ?, ?)",
                "KS000002", passwordEncoder.encode(USER_PASSWORD), "手机用户", "13900139000", 1);

        // 插入禁用用户
        jdbcTemplate.update(
                "INSERT INTO promotion_user (user_no, password, nickname, mobile, status) VALUES (?, ?, ?, ?, ?)",
                "KS000003", passwordEncoder.encode(USER_PASSWORD), "已禁用用户", "13700137000", 0);
    }

    /**
     * 发送管理员登录请求并返回Token
     */
    protected String loginAsAdmin() throws Exception {
        return loginAsAdmin(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    /**
     * 以指定管理员身份登录
     */
    protected String loginAsAdmin(String account, String password) throws Exception {
        var requestBody = String.format("{\"account\":\"%s\",\"password\":\"%s\"}", account, password);
        var result = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content(requestBody)
        ).andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("data").get("accessToken").stringValue();
    }

    /**
     * 发送用户登录请求并返回Token
     */
    protected String loginAsUser() throws Exception {
        return loginAsUser("13800138000", USER_PASSWORD);
    }

    /**
     * 以指定用户身份登录
     */
    protected String loginAsUser(String account, String password) throws Exception {
        var requestBody = String.format("{\"account\":\"%s\",\"password\":\"%s\"}", account, password);
        var result = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content(requestBody)
        ).andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("data").get("accessToken").stringValue();
    }
}
