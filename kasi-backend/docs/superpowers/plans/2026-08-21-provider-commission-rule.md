# Provider Commission Rule Implementation Plan

> 历史实施计划。本文保留当时的测试驱动记录，但时间段、状态、PATCH 提前结束和 DELETE 规则已不属于当前契约；当前行为以仓库 README 和 AGENTS.md 为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现按短剧平台统一配置、按时间版本化的五项分佣规则，并提供管理员 API、并发安全写入和可复用的预计佣金计算器。

**Architecture:** 在现有 `drama` 模块增加 `provider_commission_rule` 单表模型，规则直接关联 `short_drama_provider`，不关联单部短剧或接入账号。Service 使用现有 UTC `Clock` 计算派生状态，平台行锁串行化同平台写入；管理员 API 使用百分比，持久层和计算器使用 `0..1` 的 `BigDecimal` 比例。

**Tech Stack:** Java 25、Spring Boot、Spring Security、MyBatis XML、Flyway、MySQL、H2 MySQL mode、JUnit 5、AssertJ、Mockito、MockMvc

---

## File Map

**Database and test bootstrap**

- Create `src/main/resources/db/migration/V8__provider_commission_rule.sql`: production rule table, indexes and platform foreign key.
- Create `src/test/java/com/kasi/backend/ProviderCommissionRuleMigrationTest.java`: execute all production migrations on isolated H2 and assert the V8 contract.
- Modify `src/test/resources/test-schema.sql`: mirror the new table for `BaseAuthTest` integration tests.
- Modify `src/test/java/com/kasi/backend/BaseAuthTest.java`: delete commission rules before deleting providers.

**Domain and persistence**

- Create `src/main/java/com/kasi/backend/drama/entity/ProviderCommissionRule.java`: pure table entity.
- Create `src/main/java/com/kasi/backend/drama/enums/CommissionRuleStatus.java`: `PENDING`, `ACTIVE`, `ENDED` response status.
- Create `src/main/java/com/kasi/backend/drama/mapper/ProviderCommissionRuleMapper.java`: CRUD, effective-time and overlap queries for one table.
- Create `src/main/resources/mapper/ProviderCommissionRuleMapper.xml`: MyBatis result map and SQL.
- Create `src/test/java/com/kasi/backend/drama/mapper/ProviderCommissionRulePersistenceTest.java`: precision, effective lookup, overlap and CRUD integration tests.
- Modify `src/main/java/com/kasi/backend/provider/mapper/ShortDramaProviderMapper.java`: add provider row-lock query.
- Modify `src/main/resources/mapper/ShortDramaProviderMapper.xml`: implement `SELECT ... FOR UPDATE`.

**Calculation and business service**

- Create `src/main/java/com/kasi/backend/drama/calculator/ProviderCommissionCalculator.java`: deterministic five-rate `BigDecimal` formula.
- Create `src/test/java/com/kasi/backend/drama/calculator/ProviderCommissionCalculatorTest.java`: formula and rounding unit tests.
- Create `src/main/java/com/kasi/backend/drama/dto/CreateCommissionRuleDTO.java`: create request validation.
- Create `src/main/java/com/kasi/backend/drama/dto/UpdateCommissionRuleDTO.java`: future-rule update validation.
- Create `src/main/java/com/kasi/backend/drama/dto/EndCommissionRuleDTO.java`: active-rule end-time validation.
- Create `src/main/java/com/kasi/backend/drama/vo/ProviderCommissionRuleVO.java`: percentage-based admin response.
- Create `src/main/java/com/kasi/backend/drama/service/ProviderCommissionRuleService.java`: admin lifecycle and internal effective-rule lookup contract.
- Create `src/main/java/com/kasi/backend/drama/service/impl/ProviderCommissionRuleServiceImpl.java`: transaction, interval, state and mapping rules.
- Create `src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleServiceTest.java`: fixed-clock unit tests.
- Create `src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleConcurrencyTest.java`: simultaneous overlapping create integration test.
- Modify `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`: reachable 6xxx commission errors.

**HTTP and security**

- Create `src/main/java/com/kasi/backend/drama/controller/ProviderCommissionRuleController.java`: nested platform rule endpoints.
- Create `src/test/java/com/kasi/backend/drama/controller/ProviderCommissionRuleControllerTest.java`: validation, lifecycle and role isolation.
- Modify `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`: restrict all rule writes to `ROLE_SUPER_ADMIN`; retain admin reads.

**Documentation**

- Modify `README.md`: V8, implemented package/API and remaining scope.
- Modify `AGENTS.md`: current migration and `drama` module truth.
- Modify `docs/superpowers/specs/2026-08-21-provider-commission-rule-design.md`: implementation result and verification evidence.
- Modify `docs/superpowers/plans/2026-08-17-multi-drama-provider-roadmap.md`: mark module 4 complete only after full verification.
- Modify `docs/superpowers/specs/2026-08-17-multi-drama-provider-promotion-design.md`: update current-state paragraph without marking later modules complete.

## Conventions Used In Every Task

- Run Maven with JDK 25 in the same PowerShell process:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -v
```

- Every new test method uses a Chinese `@DisplayName` and a camelCase method name.
- Controller tests extend `BaseAuthTest`; the isolated migration test does not.
- Use `AuthContextHolder.getAdminId()` only in the Controller and pass `operatorId` into the Service.
- API percentage values use `0..100`; entities and calculator inputs use `0..1` ratios.
- Intervals are left-closed/right-open: `[effectiveFrom, effectiveTo)`.

### Task 1: Add The V8 Schema Contract

**Files:**

- Create: `src/test/java/com/kasi/backend/ProviderCommissionRuleMigrationTest.java`
- Create: `src/main/resources/db/migration/V8__provider_commission_rule.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`

- [ ] **Step 1: Write the failing migration test**

Create `ProviderCommissionRuleMigrationTest` with this contract:

```java
package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCommissionRuleMigrationTest {

    @Test
    @DisplayName("V8创建短剧平台分佣规则表并保存高精度费率")
    void migrateCreatesProviderCommissionRuleSchema() {
        JdbcTemplate jdbc = migrateAllMigrations();
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);

        jdbc.update("INSERT INTO provider_commission_rule "
                        + "(provider_id,channel_fee_rate,principal_fee_rate,principal_commission_rate,"
                        + "downstream_fee_rate,downstream_commission_rate,effective_from,created_by,updated_by) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                providerId, new BigDecimal("0.3000000000"), BigDecimal.ZERO,
                new BigDecimal("0.8000000000"), BigDecimal.ZERO,
                new BigDecimal("0.7000000000"), LocalDateTime.of(2026, 9, 1, 0, 0), 1L, 1L);

        assertThat(jdbc.queryForObject(
                "SELECT channel_fee_rate FROM provider_commission_rule", BigDecimal.class))
                .isEqualByComparingTo("0.3000000000");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME='PROVIDER_COMMISSION_RULE'", Integer.class))
                .isEqualTo(13);
    }

    private JdbcTemplate migrateAllMigrations() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:provider_commission_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }
}
```

- [ ] **Step 2: Run the migration test and verify red**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleMigrationTest test
```

Expected: FAIL because `provider_commission_rule` does not exist.

- [ ] **Step 3: Add the production migration**

Create `V8__provider_commission_rule.sql`:

```sql
-- 短剧平台分佣规则版本
CREATE TABLE `provider_commission_rule`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id`                BIGINT UNSIGNED NOT NULL COMMENT '短剧平台ID',
    `channel_fee_rate`           DECIMAL(12, 10) NOT NULL COMMENT '渠道费率，0到1',
    `principal_fee_rate`         DECIMAL(12, 10) NOT NULL COMMENT '甲方手续费率，0到1',
    `principal_commission_rate`  DECIMAL(12, 10) NOT NULL COMMENT '甲方给我方分佣比例，0到1',
    `downstream_fee_rate`        DECIMAL(12, 10) NOT NULL COMMENT '我方手续费率，0到1',
    `downstream_commission_rate` DECIMAL(12, 10) NOT NULL COMMENT '我方给下游分佣比例，0到1',
    `effective_from`             DATETIME        NOT NULL COMMENT '开始生效时间',
    `effective_to`               DATETIME                 DEFAULT NULL COMMENT '结束时间，空表示长期有效',
    `created_by`                 BIGINT UNSIGNED NOT NULL COMMENT '创建管理员ID',
    `updated_by`                 BIGINT UNSIGNED NOT NULL COMMENT '最后修改管理员ID',
    `created_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_provider_commission_time` (`provider_id`, `effective_from`, `effective_to`),
    CONSTRAINT `fk_provider_commission_provider`
        FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧平台分佣规则版本';
```

- [ ] **Step 4: Mirror V8 in the test schema and cleanup order**

Append the equivalent H2 table after `short_drama_provider` is defined in `test-schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS provider_commission_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    channel_fee_rate DECIMAL(12, 10) NOT NULL,
    principal_fee_rate DECIMAL(12, 10) NOT NULL,
    principal_commission_rate DECIMAL(12, 10) NOT NULL,
    downstream_fee_rate DECIMAL(12, 10) NOT NULL,
    downstream_commission_rate DECIMAL(12, 10) NOT NULL,
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_test_provider_commission_provider
        FOREIGN KEY (provider_id) REFERENCES short_drama_provider (id)
);
CREATE INDEX idx_test_provider_commission_time
    ON provider_commission_rule (provider_id, effective_from, effective_to);
```

In `BaseAuthTest.baseSetUp()`, add this before `DELETE FROM short_drama_provider`:

```java
jdbcTemplate.execute("DELETE FROM provider_commission_rule");
```

- [ ] **Step 5: Run schema tests and verify green**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleMigrationTest,ProviderPersistenceTest test
```

Expected: BUILD SUCCESS; both tests have zero failures and zero errors.

- [ ] **Step 6: Commit the schema contract**

```powershell
git add src/main/resources/db/migration/V8__provider_commission_rule.sql src/test/resources/test-schema.sql src/test/java/com/kasi/backend/BaseAuthTest.java src/test/java/com/kasi/backend/ProviderCommissionRuleMigrationTest.java
git commit -m "feat: add provider commission rule schema"
```

### Task 2: Add The Rule Entity And Mapper

**Files:**

- Create: `src/test/java/com/kasi/backend/drama/mapper/ProviderCommissionRulePersistenceTest.java`
- Create: `src/main/java/com/kasi/backend/drama/entity/ProviderCommissionRule.java`
- Create: `src/main/java/com/kasi/backend/drama/mapper/ProviderCommissionRuleMapper.java`
- Create: `src/main/resources/mapper/ProviderCommissionRuleMapper.xml`

- [ ] **Step 1: Write the failing persistence tests**

The test extends `BaseAuthTest`, autowires `ProviderCommissionRuleMapper`, inserts two adjacent rules, and asserts precision, effective lookup and overlap behavior:

```java
@Test
@DisplayName("平台规则按时间匹配并保留十位小数费率")
void ruleCanBeStoredAndResolvedByTime() {
    Long providerId = providerId();
    ProviderCommissionRule first = rule(providerId,
            LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
    ProviderCommissionRule second = rule(providerId,
            LocalDateTime.of(2026, 9, 1, 0, 0), null);
    assertThat(mapper.insert(first)).isEqualTo(1);
    assertThat(mapper.insert(second)).isEqualTo(1);

    assertThat(mapper.findEffective(providerId, LocalDateTime.of(2026, 8, 31, 23, 59)).getId())
            .isEqualTo(first.getId());
    assertThat(mapper.findEffective(providerId, LocalDateTime.of(2026, 9, 1, 0, 0)).getId())
            .isEqualTo(second.getId());
    assertThat(mapper.findByIdAndProviderId(first.getId(), providerId).getChannelFeeRate())
            .isEqualByComparingTo("0.3000000000");
}

@Test
@DisplayName("重叠查询允许相邻区间并拒绝交叉区间")
void overlapQueryUsesHalfOpenIntervals() {
    Long providerId = providerId();
    mapper.insert(rule(providerId, LocalDateTime.of(2026, 8, 1, 0, 0),
            LocalDateTime.of(2026, 9, 1, 0, 0)));

    assertThat(mapper.countOverlapping(providerId, null,
            LocalDateTime.of(2026, 9, 1, 0, 0), null)).isZero();
    assertThat(mapper.countOverlapping(providerId, null,
            LocalDateTime.of(2026, 8, 15, 0, 0), LocalDateTime.of(2026, 9, 15, 0, 0)))
            .isEqualTo(1);
}
```

Add these exact fields and helpers to the test class:

```java
@Autowired
private ProviderCommissionRuleMapper mapper;

@Autowired
private ShortDramaProviderMapper providerMapper;

private Long providerId() {
    return providerMapper.findByCode("GOODSHORT").getId();
}

private ProviderCommissionRule rule(Long providerId, LocalDateTime from, LocalDateTime to) {
    ProviderCommissionRule rule = new ProviderCommissionRule();
    rule.setProviderId(providerId);
    rule.setChannelFeeRate(new BigDecimal("0.3000000000"));
    rule.setPrincipalFeeRate(BigDecimal.ZERO);
    rule.setPrincipalCommissionRate(new BigDecimal("0.8000000000"));
    rule.setDownstreamFeeRate(BigDecimal.ZERO);
    rule.setDownstreamCommissionRate(new BigDecimal("0.7000000000"));
    rule.setEffectiveFrom(from);
    rule.setEffectiveTo(to);
    rule.setCreatedBy(1L);
    rule.setUpdatedBy(1L);
    return rule;
}
```

- [ ] **Step 2: Run the mapper test and verify red**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRulePersistenceTest test
```

Expected: test compilation fails because the entity and mapper do not exist.

- [ ] **Step 3: Create the entity**

```java
package com.kasi.backend.drama.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProviderCommissionRule {
    private Long id;
    private Long providerId;
    private BigDecimal channelFeeRate;
    private BigDecimal principalFeeRate;
    private BigDecimal principalCommissionRate;
    private BigDecimal downstreamFeeRate;
    private BigDecimal downstreamCommissionRate;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: Create the mapper contract**

```java
package com.kasi.backend.drama.mapper;

import com.kasi.backend.drama.entity.ProviderCommissionRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProviderCommissionRuleMapper {
    int insert(ProviderCommissionRule rule);
    List<ProviderCommissionRule> findAllByProviderId(@Param("providerId") Long providerId);
    ProviderCommissionRule findByIdAndProviderId(@Param("id") Long id,
                                                   @Param("providerId") Long providerId);
    ProviderCommissionRule findEffective(@Param("providerId") Long providerId,
                                          @Param("at") LocalDateTime at);
    long countOverlapping(@Param("providerId") Long providerId,
                          @Param("excludeId") Long excludeId,
                          @Param("effectiveFrom") LocalDateTime effectiveFrom,
                          @Param("effectiveTo") LocalDateTime effectiveTo);
    int update(ProviderCommissionRule rule);
    int updateEffectiveTo(@Param("id") Long id,
                          @Param("providerId") Long providerId,
                          @Param("effectiveTo") LocalDateTime effectiveTo,
                          @Param("updatedBy") Long updatedBy);
    int delete(@Param("id") Long id, @Param("providerId") Long providerId);
}
```

- [ ] **Step 5: Implement the mapper XML**

Create `ProviderCommissionRuleMapper.xml` with this result map and the exact read predicates:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.kasi.backend.drama.mapper.ProviderCommissionRuleMapper">
<resultMap id="RuleMap" type="com.kasi.backend.drama.entity.ProviderCommissionRule">
    <id column="id" property="id"/>
    <result column="provider_id" property="providerId"/>
    <result column="channel_fee_rate" property="channelFeeRate"/>
    <result column="principal_fee_rate" property="principalFeeRate"/>
    <result column="principal_commission_rate" property="principalCommissionRate"/>
    <result column="downstream_fee_rate" property="downstreamFeeRate"/>
    <result column="downstream_commission_rate" property="downstreamCommissionRate"/>
    <result column="effective_from" property="effectiveFrom"/>
    <result column="effective_to" property="effectiveTo"/>
    <result column="created_by" property="createdBy"/>
    <result column="updated_by" property="updatedBy"/>
    <result column="created_at" property="createdAt"/>
    <result column="updated_at" property="updatedAt"/>
</resultMap>

<select id="findAllByProviderId" resultMap="RuleMap">
    SELECT * FROM provider_commission_rule
    WHERE provider_id = #{providerId}
    ORDER BY effective_from DESC
</select>

<select id="findByIdAndProviderId" resultMap="RuleMap">
    SELECT * FROM provider_commission_rule
    WHERE id = #{id} AND provider_id = #{providerId}
</select>

<select id="findEffective" resultMap="RuleMap">
    SELECT * FROM provider_commission_rule
    WHERE provider_id = #{providerId}
      AND effective_from &lt;= #{at}
      AND (effective_to IS NULL OR effective_to &gt; #{at})
    ORDER BY effective_from DESC
    LIMIT 1
</select>

<select id="countOverlapping" resultType="long">
    SELECT COUNT(*) FROM provider_commission_rule
    WHERE provider_id = #{providerId}
      <if test="excludeId != null">AND id != #{excludeId}</if>
      AND (#{effectiveTo} IS NULL OR effective_from &lt; #{effectiveTo})
      AND (effective_to IS NULL OR effective_to &gt; #{effectiveFrom})
</select>
```

Add the write statements below the read statements:

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO provider_commission_rule
    (provider_id,channel_fee_rate,principal_fee_rate,principal_commission_rate,
     downstream_fee_rate,downstream_commission_rate,effective_from,effective_to,created_by,updated_by)
    VALUES
    (#{providerId},#{channelFeeRate},#{principalFeeRate},#{principalCommissionRate},
     #{downstreamFeeRate},#{downstreamCommissionRate},#{effectiveFrom},#{effectiveTo},#{createdBy},#{updatedBy})
</insert>

<update id="update">
    UPDATE provider_commission_rule
    SET channel_fee_rate=#{channelFeeRate},principal_fee_rate=#{principalFeeRate},
        principal_commission_rate=#{principalCommissionRate},downstream_fee_rate=#{downstreamFeeRate},
        downstream_commission_rate=#{downstreamCommissionRate},effective_from=#{effectiveFrom},
        effective_to=#{effectiveTo},updated_by=#{updatedBy},updated_at=CURRENT_TIMESTAMP
    WHERE id=#{id} AND provider_id=#{providerId}
</update>

<update id="updateEffectiveTo">
    UPDATE provider_commission_rule
    SET effective_to=#{effectiveTo},updated_by=#{updatedBy},updated_at=CURRENT_TIMESTAMP
    WHERE id=#{id} AND provider_id=#{providerId}
</update>

<delete id="delete">
    DELETE FROM provider_commission_rule WHERE id=#{id} AND provider_id=#{providerId}
</delete>
</mapper>
```

- [ ] **Step 6: Run the persistence tests and verify green**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRulePersistenceTest test
```

Expected: BUILD SUCCESS; effective lookup changes exactly at the shared boundary and the overlap assertions pass.

- [ ] **Step 7: Commit persistence**

```powershell
git add src/main/java/com/kasi/backend/drama/entity/ProviderCommissionRule.java src/main/java/com/kasi/backend/drama/mapper/ProviderCommissionRuleMapper.java src/main/resources/mapper/ProviderCommissionRuleMapper.xml src/test/java/com/kasi/backend/drama/mapper/ProviderCommissionRulePersistenceTest.java
git commit -m "feat: persist provider commission rules"
```

### Task 3: Add The BigDecimal Calculator

**Files:**

- Create: `src/test/java/com/kasi/backend/drama/calculator/ProviderCommissionCalculatorTest.java`
- Create: `src/main/java/com/kasi/backend/drama/calculator/ProviderCommissionCalculator.java`

- [ ] **Step 1: Write the failing formula tests**

```java
package com.kasi.backend.drama.calculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("平台分佣计算器")
class ProviderCommissionCalculatorTest {
    private final ProviderCommissionCalculator calculator = new ProviderCommissionCalculator();

    @Test
    @DisplayName("五项费率按顺序计算并最终四舍五入两位")
    void calculateAppliesFiveRatesAndRoundsOnce() {
        assertThat(calculator.calculate(new BigDecimal("100"),
                new BigDecimal("0.30"), BigDecimal.ZERO, new BigDecimal("0.80"),
                BigDecimal.ZERO, new BigDecimal("0.70")))
                .isEqualByComparingTo("39.20");
        assertThat(calculator.calculate(new BigDecimal("10.01"),
                new BigDecimal("0.003"), new BigDecimal("0.001"), BigDecimal.ONE,
                BigDecimal.ZERO, new BigDecimal("0.3333")))
                .isEqualByComparingTo("3.32");
    }

    @Test
    @DisplayName("百分百扣费返回零且零扣费保留完整金额")
    void calculateHandlesZeroAndFullFees() {
        assertThat(calculator.calculate(new BigDecimal("12.34"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
                .isEqualByComparingTo("12.34");
        assertThat(calculator.calculate(new BigDecimal("12.34"),
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
                .isEqualByComparingTo("0.00");
    }
}
```

- [ ] **Step 2: Run the calculator test and verify red**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionCalculatorTest test
```

Expected: compilation fails because `ProviderCommissionCalculator` does not exist.

- [ ] **Step 3: Implement the minimal calculator**

```java
package com.kasi.backend.drama.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

public class ProviderCommissionCalculator {
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public BigDecimal calculate(BigDecimal amount,
                                BigDecimal channelFeeRate,
                                BigDecimal principalFeeRate,
                                BigDecimal principalCommissionRate,
                                BigDecimal downstreamFeeRate,
                                BigDecimal downstreamCommissionRate) {
        BigDecimal result = Objects.requireNonNull(amount)
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(channelFeeRate)), MATH_CONTEXT)
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(principalFeeRate)), MATH_CONTEXT)
                .multiply(Objects.requireNonNull(principalCommissionRate), MATH_CONTEXT)
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(downstreamFeeRate)), MATH_CONTEXT)
                .multiply(Objects.requireNonNull(downstreamCommissionRate), MATH_CONTEXT);
        return result.setScale(2, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 4: Run the calculator test and verify green**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionCalculatorTest test
```

Expected: BUILD SUCCESS with both test methods passing.

- [ ] **Step 5: Commit the calculator**

```powershell
git add src/main/java/com/kasi/backend/drama/calculator/ProviderCommissionCalculator.java src/test/java/com/kasi/backend/drama/calculator/ProviderCommissionCalculatorTest.java
git commit -m "feat: calculate provider commissions"
```

### Task 4: Implement Rule Validation And Lifecycle Service

**Files:**

- Create: `src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleServiceTest.java`
- Create: `src/main/java/com/kasi/backend/drama/enums/CommissionRuleStatus.java`
- Create: `src/main/java/com/kasi/backend/drama/dto/CreateCommissionRuleDTO.java`
- Create: `src/main/java/com/kasi/backend/drama/dto/UpdateCommissionRuleDTO.java`
- Create: `src/main/java/com/kasi/backend/drama/dto/EndCommissionRuleDTO.java`
- Create: `src/main/java/com/kasi/backend/drama/vo/ProviderCommissionRuleVO.java`
- Create: `src/main/java/com/kasi/backend/drama/service/ProviderCommissionRuleService.java`
- Create: `src/main/java/com/kasi/backend/drama/service/impl/ProviderCommissionRuleServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`

- [ ] **Step 1: Write fixed-clock failing service tests**

Use Mockito for both mappers and a fixed UTC clock. Add this setup and the following tests:

```java
private ProviderCommissionRuleMapper ruleMapper;
private ShortDramaProviderMapper providerMapper;
private ProviderCommissionRuleService service;

@BeforeEach
void setUp() {
    ruleMapper = mock(ProviderCommissionRuleMapper.class);
    providerMapper = mock(ShortDramaProviderMapper.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-21T08:00:00Z"), ZoneOffset.UTC);
    service = new ProviderCommissionRuleServiceImpl(ruleMapper, providerMapper, clock);
    when(providerMapper.findById(7L)).thenReturn(provider(7L));
    when(ruleMapper.insert(any(ProviderCommissionRule.class))).thenReturn(1);
    when(ruleMapper.update(any(ProviderCommissionRule.class))).thenReturn(1);
    when(ruleMapper.updateEffectiveTo(anyLong(), anyLong(), any(), anyLong())).thenReturn(1);
    when(ruleMapper.delete(anyLong(), anyLong())).thenReturn(1);
}

@Test
@DisplayName("新增规则把百分比转换为比例并返回生效状态")
void createConvertsPercentAndReturnsStatus() {
    when(ruleMapper.countOverlapping(eq(7L), isNull(), any(), isNull())).thenReturn(0L);
    CreateCommissionRuleDTO request = createRequest(
            LocalDateTime.of(2026, 8, 1, 0, 0), null);

    ProviderCommissionRuleVO result = service.create(1L, 7L, request);

    ArgumentCaptor<ProviderCommissionRule> captor = ArgumentCaptor.forClass(ProviderCommissionRule.class);
    verify(ruleMapper).insert(captor.capture());
    assertThat(captor.getValue().getChannelFeeRate()).isEqualByComparingTo("0.3000");
    assertThat(result.getChannelFeeRate()).isEqualByComparingTo("30");
    assertThat(result.getStatus()).isEqualTo(CommissionRuleStatus.ACTIVE);
}

@Test
@DisplayName("重叠规则和非法时间被拒绝")
void createRejectsOverlapAndInvalidTime() {
    when(ruleMapper.countOverlapping(anyLong(), isNull(), any(), any())).thenReturn(1L);
    assertBusinessCode(() -> service.create(1L, 7L, createRequest(
            LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0))), 6013);
    assertBusinessCode(() -> service.create(1L, 7L, createRequest(
            LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0))), 6012);
}

@Test
@DisplayName("未来规则可以修改和删除")
void futureRuleCanBeUpdatedAndDeleted() {
    ProviderCommissionRule futureForUpdate = storedRule(11L,
            LocalDateTime.of(2026, 9, 1, 0, 0), null);
    ProviderCommissionRule futureForDelete = storedRule(12L,
            LocalDateTime.of(2026, 10, 1, 0, 0), null);
    when(ruleMapper.findByIdAndProviderId(11L, 7L)).thenReturn(futureForUpdate);
    when(ruleMapper.findByIdAndProviderId(12L, 7L)).thenReturn(futureForDelete);
    when(ruleMapper.countOverlapping(eq(7L), eq(11L), any(), any())).thenReturn(0L);

    UpdateCommissionRuleDTO update = updateRequest(
            LocalDateTime.of(2026, 9, 2, 0, 0), null);
    assertThat(service.update(1L, 7L, 11L, update).getStatus())
            .isEqualTo(CommissionRuleStatus.PENDING);
    service.delete(1L, 7L, 12L);

    verify(ruleMapper).update(futureForUpdate);
    verify(ruleMapper).delete(12L, 7L);
}

@Test
@DisplayName("当前规则只能提前结束而历史规则完全只读")
void activeCanOnlyEndAndEndedIsReadOnly() {
    ProviderCommissionRule active = storedRule(21L,
            LocalDateTime.of(2026, 8, 1, 0, 0), null);
    ProviderCommissionRule ended = storedRule(31L,
            LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));
    when(ruleMapper.findByIdAndProviderId(21L, 7L)).thenReturn(active);
    when(ruleMapper.findByIdAndProviderId(31L, 7L)).thenReturn(ended);
    when(ruleMapper.countOverlapping(eq(7L), eq(21L), any(), any())).thenReturn(0L);
    EndCommissionRuleDTO end = new EndCommissionRuleDTO();
    end.setEffectiveTo(LocalDateTime.of(2026, 8, 22, 0, 0));

    assertThat(service.end(1L, 7L, 21L, end).getEffectiveTo())
            .isEqualTo(LocalDateTime.of(2026, 8, 22, 0, 0));
    assertBusinessCode(() -> service.update(1L, 7L, 21L,
            updateRequest(LocalDateTime.of(2026, 9, 1, 0, 0), null)), 6014);
    assertBusinessCode(() -> service.delete(1L, 7L, 21L), 6014);
    assertBusinessCode(() -> service.delete(1L, 7L, 31L), 6014);
}
```

Add these deterministic helpers to the test class:

```java
private ShortDramaProvider provider(Long id) {
    ShortDramaProvider provider = new ShortDramaProvider();
    provider.setId(id);
    provider.setProviderCode("GOODSHORT");
    return provider;
}

private ProviderCommissionRule storedRule(Long id, LocalDateTime from, LocalDateTime to) {
    ProviderCommissionRule rule = new ProviderCommissionRule();
    rule.setId(id); rule.setProviderId(7L);
    rule.setChannelFeeRate(new BigDecimal("0.30"));
    rule.setPrincipalFeeRate(BigDecimal.ZERO);
    rule.setPrincipalCommissionRate(new BigDecimal("0.80"));
    rule.setDownstreamFeeRate(BigDecimal.ZERO);
    rule.setDownstreamCommissionRate(new BigDecimal("0.70"));
    rule.setEffectiveFrom(from); rule.setEffectiveTo(to);
    return rule;
}

private CreateCommissionRuleDTO createRequest(LocalDateTime from, LocalDateTime to) {
    CreateCommissionRuleDTO request = new CreateCommissionRuleDTO();
    request.setChannelFeeRate(new BigDecimal("30"));
    request.setPrincipalFeeRate(BigDecimal.ZERO);
    request.setPrincipalCommissionRate(new BigDecimal("80"));
    request.setDownstreamFeeRate(BigDecimal.ZERO);
    request.setDownstreamCommissionRate(new BigDecimal("70"));
    request.setEffectiveFrom(from); request.setEffectiveTo(to);
    return request;
}

private UpdateCommissionRuleDTO updateRequest(LocalDateTime from, LocalDateTime to) {
    UpdateCommissionRuleDTO request = new UpdateCommissionRuleDTO();
    request.setChannelFeeRate(new BigDecimal("30"));
    request.setPrincipalFeeRate(BigDecimal.ZERO);
    request.setPrincipalCommissionRate(new BigDecimal("80"));
    request.setDownstreamFeeRate(BigDecimal.ZERO);
    request.setDownstreamCommissionRate(new BigDecimal("70"));
    request.setEffectiveFrom(from); request.setEffectiveTo(to);
    return request;
}

private void assertBusinessCode(ThrowingCallable call, int code) {
    assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code));
}
```

- [ ] **Step 2: Run the service test and verify red**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleServiceTest test
```

Expected: test compilation fails because DTO, VO, status and service types do not exist.

- [ ] **Step 3: Add reachable error codes**

Append after `DRAMA_LOCAL_STATUS_INVALID`:

```java
PROVIDER_COMMISSION_RULE_NOT_FOUND(6011, "平台分佣规则不存在"),
PROVIDER_COMMISSION_RULE_TIME_INVALID(6012, "平台分佣规则生效时间无效"),
PROVIDER_COMMISSION_RULE_TIME_OVERLAP(6013, "平台分佣规则生效时间重叠"),
PROVIDER_COMMISSION_RULE_STATE_INVALID(6014, "当前状态不允许修改平台分佣规则"),
```

- [ ] **Step 4: Add request validation and response types**

Create `CreateCommissionRuleDTO`:

```java
package com.kasi.backend.drama.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateCommissionRuleDTO {
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal channelFeeRate;
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal principalFeeRate;
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal principalCommissionRate;
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal downstreamFeeRate;
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal downstreamCommissionRate;
    @NotNull
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
```

Create `UpdateCommissionRuleDTO` as a separate request contract:

```java
package com.kasi.backend.drama.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateCommissionRuleDTO {
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal channelFeeRate;
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal principalFeeRate;
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal principalCommissionRate;
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal downstreamFeeRate;
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal downstreamCommissionRate;
    @NotNull
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
```

Create `EndCommissionRuleDTO`:

```java
package com.kasi.backend.drama.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EndCommissionRuleDTO {
    @NotNull
    private LocalDateTime effectiveTo;
}
```

Create the status enum:

```java
package com.kasi.backend.drama.enums;

public enum CommissionRuleStatus {
    PENDING, ACTIVE, ENDED
}
```

Create `ProviderCommissionRuleVO`; do not expose `createdBy` or `updatedBy`:

```java
package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.CommissionRuleStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProviderCommissionRuleVO {
    private Long id;
    private Long providerId;
    private BigDecimal channelFeeRate;
    private BigDecimal principalFeeRate;
    private BigDecimal principalCommissionRate;
    private BigDecimal downstreamFeeRate;
    private BigDecimal downstreamCommissionRate;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private CommissionRuleStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 5: Add the Service interface**

```java
package com.kasi.backend.drama.service;

import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.EndCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProviderCommissionRuleService {
    List<ProviderCommissionRuleVO> getRules(Long providerId);
    ProviderCommissionRuleVO create(Long operatorId, Long providerId, CreateCommissionRuleDTO request);
    ProviderCommissionRuleVO update(Long operatorId, Long providerId, Long ruleId,
                                    UpdateCommissionRuleDTO request);
    ProviderCommissionRuleVO end(Long operatorId, Long providerId, Long ruleId,
                                 EndCommissionRuleDTO request);
    void delete(Long operatorId, Long providerId, Long ruleId);
    Optional<ProviderCommissionRule> findEffectiveRule(Long providerId, LocalDateTime at);
}
```

- [ ] **Step 6: Implement the Service with ordinary provider reads first**

Create the implementation below. Task 5 changes only `requireProviderForWrite` to use a row lock.

```java
package com.kasi.backend.drama.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.EndCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.enums.CommissionRuleStatus;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleMapper;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCommissionRuleServiceImpl implements ProviderCommissionRuleService {
    private final ProviderCommissionRuleMapper ruleMapper;
    private final ShortDramaProviderMapper providerMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ProviderCommissionRuleVO> getRules(Long providerId) {
        requireProviderForRead(providerId);
        LocalDateTime now = LocalDateTime.now(clock);
        return ruleMapper.findAllByProviderId(providerId).stream()
                .map(rule -> toVO(rule, now)).toList();
    }

    @Override
    @Transactional
    public ProviderCommissionRuleVO create(Long operatorId, Long providerId,
                                            CreateCommissionRuleDTO request) {
        requireProviderForWrite(providerId);
        validateWindow(request.getEffectiveFrom(), request.getEffectiveTo());
        ensureNoOverlap(providerId, null, request.getEffectiveFrom(), request.getEffectiveTo());
        ProviderCommissionRule rule = new ProviderCommissionRule();
        rule.setProviderId(providerId);
        applyRates(rule, request.getChannelFeeRate(), request.getPrincipalFeeRate(),
                request.getPrincipalCommissionRate(), request.getDownstreamFeeRate(),
                request.getDownstreamCommissionRate());
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        rule.setCreatedBy(operatorId);
        rule.setUpdatedBy(operatorId);
        if (ruleMapper.insert(rule) != 1) {
            throw new IllegalStateException("平台分佣规则创建失败");
        }
        return toVO(reloadIfPossible(rule), LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public ProviderCommissionRuleVO update(Long operatorId, Long providerId, Long ruleId,
                                            UpdateCommissionRuleDTO request) {
        requireProviderForWrite(providerId);
        ProviderCommissionRule existing = requireRule(providerId, ruleId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (statusOf(existing, now) != CommissionRuleStatus.PENDING) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_STATE_INVALID);
        }
        validateWindow(request.getEffectiveFrom(), request.getEffectiveTo());
        ensureNoOverlap(providerId, ruleId, request.getEffectiveFrom(), request.getEffectiveTo());
        applyRates(existing, request.getChannelFeeRate(), request.getPrincipalFeeRate(),
                request.getPrincipalCommissionRate(), request.getDownstreamFeeRate(),
                request.getDownstreamCommissionRate());
        existing.setEffectiveFrom(request.getEffectiveFrom());
        existing.setEffectiveTo(request.getEffectiveTo());
        existing.setUpdatedBy(operatorId);
        if (ruleMapper.update(existing) != 1) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        return toVO(reloadIfPossible(existing), now);
    }

    @Override
    @Transactional
    public ProviderCommissionRuleVO end(Long operatorId, Long providerId, Long ruleId,
                                         EndCommissionRuleDTO request) {
        requireProviderForWrite(providerId);
        ProviderCommissionRule existing = requireRule(providerId, ruleId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime newEnd = request.getEffectiveTo();
        if (statusOf(existing, now) != CommissionRuleStatus.ACTIVE
                || !newEnd.isAfter(now)
                || (existing.getEffectiveTo() != null && !newEnd.isBefore(existing.getEffectiveTo()))) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_STATE_INVALID);
        }
        ensureNoOverlap(providerId, ruleId, existing.getEffectiveFrom(), newEnd);
        if (ruleMapper.updateEffectiveTo(ruleId, providerId, newEnd, operatorId) != 1) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        existing.setEffectiveTo(newEnd);
        existing.setUpdatedBy(operatorId);
        return toVO(reloadIfPossible(existing), now);
    }

    @Override
    @Transactional
    public void delete(Long operatorId, Long providerId, Long ruleId) {
        requireProviderForWrite(providerId);
        ProviderCommissionRule existing = requireRule(providerId, ruleId);
        if (statusOf(existing, LocalDateTime.now(clock)) != CommissionRuleStatus.PENDING) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_STATE_INVALID);
        }
        if (ruleMapper.delete(ruleId, providerId) != 1) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        log.info("管理员删除未来平台分佣规则: operatorId={}, providerId={}, ruleId={}",
                operatorId, providerId, ruleId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderCommissionRule> findEffectiveRule(Long providerId, LocalDateTime at) {
        return Optional.ofNullable(ruleMapper.findEffective(providerId, at));
    }

    private void requireProviderForRead(Long providerId) {
        if (providerMapper.findById(providerId) == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
    }

    private void requireProviderForWrite(Long providerId) {
        if (providerMapper.findById(providerId) == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
    }

    private ProviderCommissionRule requireRule(Long providerId, Long ruleId) {
        ProviderCommissionRule rule = ruleMapper.findByIdAndProviderId(ruleId, providerId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        return rule;
    }

    private void validateWindow(LocalDateTime from, LocalDateTime to) {
        if (to != null && !from.isBefore(to)) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_TIME_INVALID);
        }
    }

    private void ensureNoOverlap(Long providerId, Long excludeId,
                                 LocalDateTime from, LocalDateTime to) {
        if (ruleMapper.countOverlapping(providerId, excludeId, from, to) > 0) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_TIME_OVERLAP);
        }
    }

    private void applyRates(ProviderCommissionRule rule, BigDecimal channel,
                            BigDecimal principalFee, BigDecimal principalCommission,
                            BigDecimal downstreamFee, BigDecimal downstreamCommission) {
        rule.setChannelFeeRate(toRatio(channel));
        rule.setPrincipalFeeRate(toRatio(principalFee));
        rule.setPrincipalCommissionRate(toRatio(principalCommission));
        rule.setDownstreamFeeRate(toRatio(downstreamFee));
        rule.setDownstreamCommissionRate(toRatio(downstreamCommission));
    }

    private ProviderCommissionRule reloadIfPossible(ProviderCommissionRule rule) {
        if (rule.getId() == null) return rule;
        ProviderCommissionRule stored = ruleMapper.findByIdAndProviderId(rule.getId(), rule.getProviderId());
        return stored == null ? rule : stored;
    }

    private BigDecimal toRatio(BigDecimal percent) {
        return percent.movePointLeft(2);
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        return ratio.movePointRight(2).stripTrailingZeros();
    }

    private CommissionRuleStatus statusOf(ProviderCommissionRule rule, LocalDateTime now) {
        if (rule.getEffectiveFrom().isAfter(now)) return CommissionRuleStatus.PENDING;
        if (rule.getEffectiveTo() == null || rule.getEffectiveTo().isAfter(now)) {
            return CommissionRuleStatus.ACTIVE;
        }
        return CommissionRuleStatus.ENDED;
    }

    private ProviderCommissionRuleVO toVO(ProviderCommissionRule rule, LocalDateTime now) {
        return ProviderCommissionRuleVO.builder()
                .id(rule.getId()).providerId(rule.getProviderId())
                .channelFeeRate(toPercent(rule.getChannelFeeRate()))
                .principalFeeRate(toPercent(rule.getPrincipalFeeRate()))
                .principalCommissionRate(toPercent(rule.getPrincipalCommissionRate()))
                .downstreamFeeRate(toPercent(rule.getDownstreamFeeRate()))
                .downstreamCommissionRate(toPercent(rule.getDownstreamCommissionRate()))
                .effectiveFrom(rule.getEffectiveFrom()).effectiveTo(rule.getEffectiveTo())
                .status(statusOf(rule, now)).createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt()).build();
    }
}
```

- [ ] **Step 7: Run unit tests and verify green**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleServiceTest test
```

Expected: BUILD SUCCESS with create, overlap, status and lifecycle cases passing.

- [ ] **Step 8: Commit the service lifecycle**

```powershell
git add src/main/java/com/kasi/backend/common/exception/ErrorCode.java src/main/java/com/kasi/backend/drama/dto src/main/java/com/kasi/backend/drama/enums/CommissionRuleStatus.java src/main/java/com/kasi/backend/drama/vo/ProviderCommissionRuleVO.java src/main/java/com/kasi/backend/drama/service/ProviderCommissionRuleService.java src/main/java/com/kasi/backend/drama/service/impl/ProviderCommissionRuleServiceImpl.java src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleServiceTest.java
git commit -m "feat: manage provider commission rule lifecycle"
```

### Task 5: Serialize Concurrent Platform Writes

**Files:**

- Create: `src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleConcurrencyTest.java`
- Modify: `src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleServiceTest.java`
- Modify: `src/main/java/com/kasi/backend/provider/mapper/ShortDramaProviderMapper.java`
- Modify: `src/main/resources/mapper/ShortDramaProviderMapper.xml`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/ProviderCommissionRuleServiceImpl.java`

- [ ] **Step 1: Write the failing lock-order unit test**

In `ProviderCommissionRuleServiceTest`, use Mockito `InOrder`:

```java
@Test
@DisplayName("平台写入先锁定平台再检查重叠并插入")
void createLocksProviderBeforeOverlapCheck() {
    when(providerMapper.findByIdForUpdate(7L)).thenReturn(provider(7L));
    when(ruleMapper.countOverlapping(eq(7L), isNull(), any(), any())).thenReturn(0L);

    service.create(1L, 7L, createRequest(
            LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0)));

    InOrder order = inOrder(providerMapper, ruleMapper);
    order.verify(providerMapper).findByIdForUpdate(7L);
    order.verify(ruleMapper).countOverlapping(eq(7L), isNull(), any(), any());
    order.verify(ruleMapper).insert(any(ProviderCommissionRule.class));
}
```

- [ ] **Step 2: Run the unit test and verify red**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleServiceTest#createLocksProviderBeforeOverlapCheck test
```

Expected: compilation or verification failure because `findByIdForUpdate` is absent and writes still call `findById`.

- [ ] **Step 3: Add the provider row-lock mapper method**

Add to `ShortDramaProviderMapper`:

```java
ShortDramaProvider findByIdForUpdate(@Param("id") Long id);
```

Add to its XML:

```xml
<select id="findByIdForUpdate" resultType="com.kasi.backend.provider.entity.ShortDramaProvider">
    SELECT * FROM short_drama_provider WHERE id = #{id} FOR UPDATE
</select>
```

Replace `requireProviderForWrite` with:

```java
private void requireProviderForWrite(Long providerId) {
    if (providerMapper.findByIdForUpdate(providerId) == null) {
        throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
    }
}
```

The existing `create`, `update`, `end`, and `delete` methods already call this helper before any rule query. Keep `requireProviderForRead` on `findById`. In `ProviderCommissionRuleServiceTest.setUp()`, add:

```java
when(providerMapper.findByIdForUpdate(7L)).thenReturn(provider(7L));
```

- [ ] **Step 4: Write the concurrent integration test**

Create a `BaseAuthTest` subclass that autowires the Service. Start two workers with one `CountDownLatch`, submit identical future intervals, and collect outcomes:

```java
@Autowired
private ProviderCommissionRuleService service;

@Test
@DisplayName("两个并发请求只能创建一条重叠平台规则")
void concurrentCreatesAllowOnlyOneRule() throws Exception {
    Long providerId = jdbcTemplate.queryForObject(
            "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
    CreateCommissionRuleDTO request = request(
            LocalDateTime.of(2099, 9, 1, 0, 0), LocalDateTime.of(2099, 10, 1, 0, 0));
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
        Callable<Integer> action = () -> {
            start.await();
            try {
                service.create(1L, providerId, request);
                return 0;
            } catch (BusinessException exception) {
                return exception.getCode();
            }
        };
        Future<Integer> first = executor.submit(action);
        Future<Integer> second = executor.submit(action);
        start.countDown();
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(0, 6013);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM provider_commission_rule WHERE provider_id=?",
                Long.class, providerId)).isEqualTo(1L);
    } finally {
        executor.shutdownNow();
    }
}

private CreateCommissionRuleDTO request(LocalDateTime from, LocalDateTime to) {
    CreateCommissionRuleDTO request = new CreateCommissionRuleDTO();
    request.setChannelFeeRate(new BigDecimal("30"));
    request.setPrincipalFeeRate(BigDecimal.ZERO);
    request.setPrincipalCommissionRate(new BigDecimal("80"));
    request.setDownstreamFeeRate(BigDecimal.ZERO);
    request.setDownstreamCommissionRate(new BigDecimal("70"));
    request.setEffectiveFrom(from);
    request.setEffectiveTo(to);
    return request;
}
```

- [ ] **Step 5: Run service and concurrency tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleServiceTest,ProviderCommissionRuleConcurrencyTest test
```

Expected: BUILD SUCCESS; the concurrent result contains one success and one `6013` overlap result.

- [ ] **Step 6: Commit concurrency control**

```powershell
git add src/main/java/com/kasi/backend/provider/mapper/ShortDramaProviderMapper.java src/main/resources/mapper/ShortDramaProviderMapper.xml src/main/java/com/kasi/backend/drama/service/impl/ProviderCommissionRuleServiceImpl.java src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleServiceTest.java src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleConcurrencyTest.java
git commit -m "fix: serialize provider commission rule writes"
```

### Task 6: Expose Admin APIs And Enforce Roles

**Files:**

- Create: `src/test/java/com/kasi/backend/drama/controller/ProviderCommissionRuleControllerTest.java`
- Create: `src/main/java/com/kasi/backend/drama/controller/ProviderCommissionRuleController.java`
- Modify: `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`

- [ ] **Step 1: Write failing full-stack controller tests**

Extend `BaseAuthTest` and cover:

```java
@Test
@DisplayName("超级管理员可新增规则且普通管理员只能查看")
void superAdminWritesAndOrdinaryAdminReads() throws Exception {
    Long providerId = providerId();
    String superToken = loginAsAdmin();
    mockMvc.perform(post("/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + superToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validFutureRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.channelFeeRate").value(30))
            .andExpect(jsonPath("$.data.status").value("PENDING"));

    String operatorToken = loginAsAdmin("operator", ADMIN_PASSWORD);
    mockMvc.perform(get("/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].principalCommissionRate").value(80));
    mockMvc.perform(post("/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + operatorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validFutureRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(1003));
}

@Test
@DisplayName("非法百分比和重叠时间返回稳定错误码")
void invalidRatesAndOverlapReturnStableCodes() throws Exception {
    String token = loginAsAdmin();
    Long providerId = providerId();
    mockMvc.perform(post("/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validFutureRequest().replace("\"channelFeeRate\":30", "\"channelFeeRate\":101")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
    mockMvc.perform(post("/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(validFutureRequest()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    mockMvc.perform(post("/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(validFutureRequest()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(6013));
}

@Test
@DisplayName("匿名和推广用户不能读取平台分佣规则")
void anonymousAndUserCannotReadRules() throws Exception {
    Long providerId = providerId();
    mockMvc.perform(get("/api/admin/drama/providers/{providerId}/commission-rules", providerId))
            .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + loginAsUser()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1003));
}

@Test
@DisplayName("未来规则可修改删除而当前规则只能提前结束")
void lifecycleEndpointsEnforceDerivedState() throws Exception {
    Long providerId = providerId();
    String token = loginAsAdmin();
    String created = mockMvc.perform(post(
                    "/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(validFutureRequest()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    long futureId = objectMapper.readTree(created).get("data").get("id").longValue();
    mockMvc.perform(put("/api/admin/drama/providers/{providerId}/commission-rules/{ruleId}",
                    providerId, futureId).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validFutureRequest().replace("\"channelFeeRate\":30", "\"channelFeeRate\":25")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.channelFeeRate").value(25));
    mockMvc.perform(delete("/api/admin/drama/providers/{providerId}/commission-rules/{ruleId}",
                    providerId, futureId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

    String activeRequest = validFutureRequest()
            .replace("2099-01-01T00:00:00", "2026-01-01T00:00:00")
            .replace("2099-02-01T00:00:00", "2098-01-01T00:00:00");
    String activeCreated = mockMvc.perform(post(
                    "/api/admin/drama/providers/{providerId}/commission-rules", providerId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(activeRequest))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn().getResponse().getContentAsString();
    long activeId = objectMapper.readTree(activeCreated).get("data").get("id").longValue();
    mockMvc.perform(put("/api/admin/drama/providers/{providerId}/commission-rules/{ruleId}",
                    providerId, activeId).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(activeRequest))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(6014));
    String endTime = LocalDateTime.now(Clock.systemUTC()).plusDays(1).toString();
    mockMvc.perform(patch("/api/admin/drama/providers/{providerId}/commission-rules/{ruleId}/end-time",
                    providerId, activeId).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"effectiveTo\":\"" + endTime + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
}

private Long providerId() {
    return jdbcTemplate.queryForObject(
            "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
}

private String validFutureRequest() {
    return "{\"channelFeeRate\":30,\"principalFeeRate\":0,"
            + "\"principalCommissionRate\":80,\"downstreamFeeRate\":0,"
            + "\"downstreamCommissionRate\":70,"
            + "\"effectiveFrom\":\"2099-01-01T00:00:00\","
            + "\"effectiveTo\":\"2099-02-01T00:00:00\"}";
}
```

- [ ] **Step 2: Run the controller test and verify red**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleControllerTest test
```

Expected: 404 or compilation failure because the controller endpoints do not exist.

- [ ] **Step 3: Create the Controller**

```java
package com.kasi.backend.drama.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.EndCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/drama/providers/{providerId}/commission-rules")
@RequiredArgsConstructor
public class ProviderCommissionRuleController {
    private final ProviderCommissionRuleService service;

    @GetMapping
    public ApiResponse<List<ProviderCommissionRuleVO>> getRules(
            @PathVariable @Positive Long providerId) {
        return ApiResponse.success(service.getRules(providerId));
    }

    @PostMapping
    public ApiResponse<ProviderCommissionRuleVO> create(
            @PathVariable @Positive Long providerId,
            @Valid @RequestBody CreateCommissionRuleDTO request) {
        return ApiResponse.success(service.create(AuthContextHolder.getAdminId(), providerId, request));
    }

    @PutMapping("/{ruleId}")
    public ApiResponse<ProviderCommissionRuleVO> update(
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long ruleId,
            @Valid @RequestBody UpdateCommissionRuleDTO request) {
        return ApiResponse.success(service.update(
                AuthContextHolder.getAdminId(), providerId, ruleId, request));
    }

    @PatchMapping("/{ruleId}/end-time")
    public ApiResponse<ProviderCommissionRuleVO> end(
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long ruleId,
            @Valid @RequestBody EndCommissionRuleDTO request) {
        return ApiResponse.success(service.end(
                AuthContextHolder.getAdminId(), providerId, ruleId, request));
    }

    @DeleteMapping("/{ruleId}")
    public ApiResponse<Void> delete(
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long ruleId) {
        service.delete(AuthContextHolder.getAdminId(), providerId, ruleId);
        return ApiResponse.successMessage("平台分佣规则删除成功");
    }
}
```

- [ ] **Step 4: Add write matchers before the broad drama matcher**

In `SecurityConfig`, before `.requestMatchers("/api/admin/drama/**").hasRole("ADMIN")`, add:

```java
.requestMatchers(HttpMethod.POST,
        "/api/admin/drama/providers/*/commission-rules").hasRole("SUPER_ADMIN")
.requestMatchers(HttpMethod.PUT,
        "/api/admin/drama/providers/*/commission-rules/*").hasRole("SUPER_ADMIN")
.requestMatchers(HttpMethod.PATCH,
        "/api/admin/drama/providers/*/commission-rules/*/end-time").hasRole("SUPER_ADMIN")
.requestMatchers(HttpMethod.DELETE,
        "/api/admin/drama/providers/*/commission-rules/*").hasRole("SUPER_ADMIN")
```

Do not add a GET-specific matcher; GET continues through the existing `ROLE_ADMIN` drama rule.

- [ ] **Step 5: Run the focused HTTP and security tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleControllerTest,ProviderAdminControllerTest,SecurityPermissionTest test
```

Expected: BUILD SUCCESS; ordinary admin GET is 200, ordinary admin writes are 403, super-admin lifecycle calls are 200 with app code 0.

- [ ] **Step 6: Commit HTTP and security behavior**

```powershell
git add src/main/java/com/kasi/backend/drama/controller/ProviderCommissionRuleController.java src/main/java/com/kasi/backend/security/config/SecurityConfig.java src/test/java/com/kasi/backend/drama/controller/ProviderCommissionRuleControllerTest.java
git commit -m "feat: expose provider commission rule APIs"
```

### Task 7: Synchronize Documentation And Verify The Feature

**Files:**

- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-08-21-provider-commission-rule-design.md`
- Modify: `docs/superpowers/plans/2026-08-17-multi-drama-provider-roadmap.md`
- Modify: `docs/superpowers/specs/2026-08-17-multi-drama-provider-promotion-design.md`

- [ ] **Step 1: Run focused feature verification on JDK 25**

Run:

```powershell
.\mvnw.cmd -Dtest=ProviderCommissionRuleMigrationTest,ProviderCommissionRulePersistenceTest,ProviderCommissionCalculatorTest,ProviderCommissionRuleServiceTest,ProviderCommissionRuleConcurrencyTest,ProviderCommissionRuleControllerTest test
```

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 2: Run complete verification**

Run each command separately:

```powershell
.\mvnw.cmd test
```

```powershell
.\mvnw.cmd -DskipTests compile
```

```powershell
git diff --check
```

Expected: full suite and compile both report BUILD SUCCESS; the complete test summary has zero failures and zero errors; `git diff --check` prints no errors.

- [ ] **Step 3: Update current-truth documentation using the fresh evidence**

Record all of these verified facts and no later-module claims:

- V8 creates `provider_commission_rule`.
- One platform shares one versioned five-rate rule across all dramas and current/future connection accounts.
- Ordinary admins can read; only super admins can write.
- Future rules can be edited/deleted, active rules can only be ended early, ended rules are read-only.
- The five admin endpoints and exact paths are implemented.
- Promotion links, orders, fee snapshots, exports and analytics remain unimplemented.

In the dedicated design spec, change status to `已实现并通过自动化验证` and include the exact focused/full test counts and zero-failure/zero-error results from Steps 1 and 2.

- [ ] **Step 4: Review the final scope**

Run:

```powershell
git diff --check
git status --short
git diff --stat
git diff -- src/main src/test README.md AGENTS.md docs/superpowers
```

Confirm the diff contains only V8, commission-rule backend/tests and the five related documentation files. Confirm there is no `promotion_link`, order synchronization, export, wallet or analytics implementation.

- [ ] **Step 5: Commit documentation and verified status**

```powershell
git add README.md AGENTS.md docs/superpowers/specs/2026-08-21-provider-commission-rule-design.md docs/superpowers/plans/2026-08-17-multi-drama-provider-roadmap.md docs/superpowers/specs/2026-08-17-multi-drama-provider-promotion-design.md
git commit -m "docs: document provider commission rules"
```

- [ ] **Step 6: Record final evidence**

Run:

```powershell
git status --short --branch
git log -7 --oneline
```

Expected: clean working tree on the implementation branch and seven bounded commits for schema, persistence, calculator, service, concurrency, HTTP/security and documentation.
