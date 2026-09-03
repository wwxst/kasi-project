package com.kasi.backend.user.service.impl;

import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.user.dto.*;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import com.kasi.backend.user.service.PromotionUserCreationService;
import com.kasi.backend.user.service.UserManagementService;
import com.kasi.backend.promotion.service.MediaAccountOwnershipService;
import com.kasi.backend.user.vo.UserDetailVO;
import com.kasi.backend.user.vo.UserListItemVO;
import com.kasi.backend.user.vo.UserPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final PromotionUserMapper promotionUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final PromotionUserCreationService promotionUserCreationService;
    private final MediaAccountOwnershipService mediaAccountOwnershipService;

    @Override
    @Transactional(readOnly = true)
    public UserPageVO getPage(UserPageQueryDTO query) {
        String keyword = trimToNull(query.getKeyword());
        long total = promotionUserMapper.countByKeyword(keyword);
        long offset = ((long) query.getPage() - 1) * query.getSize();
        List<UserListItemVO> list = promotionUserMapper.findPage(keyword, offset, query.getSize()).stream()
                .map(this::toListItemVO)
                .toList();
        return UserPageVO.builder().list(list).page(query.getPage()).size(query.getSize()).total(total).build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailVO getById(Long id) {
        return toDetailVO(requireUser(id));
    }

    @Override
    @Transactional
    public UserDetailVO create(CreateUserDTO request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_MANAGEMENT_PASSWORD_NOT_MATCH);
        }
        String mobile = trimToNull(request.getMobile());
        String email = normalizeEmail(request.getEmail());
        requireContact(mobile, email);
        checkUniqueContacts(mobile, email, null);

        PromotionUser user = new PromotionUser();
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname().trim());
        user.setRealName(trimToNull(request.getRealName()));
        user.setMobile(mobile);
        user.setEmail(email);
        user.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        user.setRemark(trimToNull(request.getRemark()));
        user.setStatus(1);
        user.setRegisterSource("ADMIN");
        promotionUserCreationService.create(user);
        return toDetailVO(promotionUserMapper.findById(user.getId()));
    }

    @Override
    @Transactional
    public UserDetailVO update(Long id, UpdateUserDTO request) {
        PromotionUser user = requireUserForUpdate(id);
        String mobile = trimToNull(request.getMobile());
        String email = normalizeEmail(request.getEmail());
        requireContact(mobile, email);
        checkUniqueContacts(mobile, email, id);
        boolean contactChanged = !Objects.equals(mobile, user.getMobile()) || !Objects.equals(email, user.getEmail());
        SessionMutation mutation = contactChanged ? sessionService.beginMutation(SubjectType.USER, id) : null;
        registerCompletion(mutation);
        user.setMobile(mobile);
        user.setEmail(email);
        user.setNickname(request.getNickname().trim());
        user.setRealName(trimToNull(request.getRealName()));
        user.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        user.setRemark(trimToNull(request.getRemark()));
        try {
            if (promotionUserMapper.updateProfile(user) != 1) {
                throw new IllegalStateException("推广用户资料更新未生效");
            }
        } catch (DuplicateKeyException exception) {
            throw mapDuplicateContact(exception, mobile, email, id);
        }
        return toDetailVO(promotionUserMapper.findById(id));
    }

    @Override
    @Transactional
    public void updateStatus(Long id, UpdateUserStatusDTO request) {
        requireUserForUpdate(id);
        SessionMutation mutation = sessionService.beginMutation(SubjectType.USER, id);
        registerCompletion(mutation);
        if (promotionUserMapper.updateStatus(id, request.getStatus()) != 1) {
            throw new IllegalStateException("推广用户状态更新未生效");
        }
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetUserPasswordDTO request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_MANAGEMENT_PASSWORD_NOT_MATCH);
        }
        requireUserForUpdate(id);
        SessionMutation mutation = sessionService.beginMutation(SubjectType.USER, id);
        registerCompletion(mutation);
        if (promotionUserMapper.updatePassword(id, passwordEncoder.encode(request.getNewPassword())) != 1) {
            throw new IllegalStateException("推广用户密码更新未生效");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireUserForUpdate(id);
        if (mediaAccountOwnershipService != null && mediaAccountOwnershipService.hasBoundAccount(id)) {
            throw new BusinessException(ErrorCode.USER_MEDIA_ACCOUNT_BOUND);
        }
        SessionMutation mutation = sessionService.beginMutation(SubjectType.USER, id);
        registerCompletion(mutation);
        if (promotionUserMapper.deleteById(id) != 1) {
            throw new IllegalStateException("推广用户删除未生效");
        }
    }

    private PromotionUser requireUser(Long id) {
        PromotionUser user = promotionUserMapper.findById(id);
        if (user == null) throw new BusinessException(ErrorCode.USER_MANAGEMENT_NOT_FOUND);
        return user;
    }

    private PromotionUser requireUserForUpdate(Long id) {
        PromotionUser user = promotionUserMapper.findByIdForUpdate(id);
        if (user == null) throw new BusinessException(ErrorCode.USER_MANAGEMENT_NOT_FOUND);
        return user;
    }

    private void requireContact(String mobile, String email) {
        if (mobile == null && email == null) throw new BusinessException(ErrorCode.USER_CONTACT_REQUIRED);
    }

    private void checkUniqueContacts(String mobile, String email, Long currentId) {
        if (mobile != null) checkOther(promotionUserMapper.findByMobile(mobile), currentId, ErrorCode.USER_MOBILE_DUPLICATE);
        if (email != null) checkOther(promotionUserMapper.findByEmail(email), currentId, ErrorCode.USER_EMAIL_DUPLICATE);
    }

    private void checkOther(PromotionUser existing, Long currentId, ErrorCode errorCode) {
        if (existing != null && !Objects.equals(existing.getId(), currentId)) throw new BusinessException(errorCode);
    }

    private RuntimeException mapDuplicateContact(DuplicateKeyException exception, String mobile,
                                                  String email, Long currentId) {
        checkUniqueContacts(mobile, email, currentId);
        String message = exception.getMostSpecificCause().getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("uk_mobile") || message.contains("promotion_user.mobile")) {
            return new BusinessException(ErrorCode.USER_MOBILE_DUPLICATE);
        }
        if (message.contains("uk_email") || message.contains("promotion_user.email")) {
            return new BusinessException(ErrorCode.USER_EMAIL_DUPLICATE);
        }
        return exception;
    }

    private void registerCompletion(SessionMutation mutation) {
        if (mutation == null) return;
        sessionService.registerMutationCompletion(mutation);
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        String email = trimToNull(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private UserListItemVO toListItemVO(PromotionUser user) {
        return UserListItemVO.builder().id(user.getId()).userNo(user.getUserNo()).nickname(user.getNickname())
                .realName(user.getRealName()).mobile(user.getMobile()).email(user.getEmail())
                .avatarUrl(user.getAvatarUrl()).status(user.getStatus()).registerSource(user.getRegisterSource())
                .lastLoginAt(user.getLastLoginAt()).createdAt(user.getCreatedAt()).build();
    }

    private UserDetailVO toDetailVO(PromotionUser user) {
        return UserDetailVO.builder().id(user.getId()).userNo(user.getUserNo()).nickname(user.getNickname())
                .realName(user.getRealName()).mobile(user.getMobile()).email(user.getEmail())
                .avatarUrl(user.getAvatarUrl()).status(user.getStatus()).registerSource(user.getRegisterSource())
                .lastLoginAt(user.getLastLoginAt()).createdAt(user.getCreatedAt()).lastLoginIp(user.getLastLoginIp())
                .remark(user.getRemark()).updatedAt(user.getUpdatedAt()).build();
    }
}
