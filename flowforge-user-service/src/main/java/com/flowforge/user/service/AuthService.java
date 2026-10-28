package com.flowforge.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.user.dto.LoginRequest;
import com.flowforge.user.dto.LoginResponse;
import com.flowforge.user.dto.RegisterRequest;
import com.flowforge.user.dto.UserProfile;
import com.flowforge.user.entity.SysUser;
import com.flowforge.user.entity.SysUserRole;
import com.flowforge.user.mapper.SysRoleMapper;
import com.flowforge.user.mapper.SysUserMapper;
import com.flowforge.user.mapper.SysUserRoleMapper;
import com.flowforge.user.security.JwtService;
import com.flowforge.user.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    private static final String DEFAULT_ROLE = "USER";

    /**
     * 用户不存在时用于执行一次等价的哈希比对。
     * 目的是让「用户不存在」与「密码错误」的响应耗时接近，避免通过时间差枚举用户名。
     */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(SysUserMapper userMapper,
                       SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserProfile register(RegisterRequest request) {
        // 先查一次只为给出友好提示；并发下的正确性由 uk_username 唯一键保证
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.USERNAME_TAKEN);
        }

        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setEmail(blankToNull(request.email()));
        user.setStatus(1);
        user.setFailedAttempts(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.USERNAME_TAKEN);
        }

        Long roleId = roleMapper.selectIdByRoleCode(DEFAULT_ROLE);
        if (roleId == null) {
            throw new IllegalStateException("默认角色 USER 未初始化，请先执行 deploy/mysql/init 下的初始化脚本");
        }
        SysUserRole link = new SysUserRole();
        link.setUserId(user.getId());
        link.setRoleId(roleId);
        userRoleMapper.insert(link);

        // created_at 由数据库默认值生成，insert 不会回填，回查一次保证返回给前端的资料完整
        SysUser created = userMapper.selectById(user.getId());
        log.info("用户注册成功 userId={} username={}", user.getId(), user.getUsername());
        return toProfile(created, List.of(DEFAULT_ROLE));
    }

    /**
     * 登录。
     *
     * <p>**刻意不加事务**：密码错误时抛出业务异常，如果整个方法在一个事务里，
     * 失败计数与锁定时间的更新会随异常一起回滚，导致「连续失败 5 次锁定」永远不生效。
     * 这里每一步都是单条 SQL，数据库自动提交即可保证原子性。
     */
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailure(user.getId(), now);
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getFailedAttempts, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getLastLoginAt, now));

        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        String token = jwtService.issue(new LoginUser(user.getId(), user.getUsername(), roles));

        log.info("用户登录成功 userId={} username={}", user.getId(), user.getUsername());
        return LoginResponse.bearer(token, jwtService.ttlSeconds(), toProfile(user, roles));
    }

    public UserProfile profileOf(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toProfile(user, roleMapper.selectRoleCodesByUserId(userId));
    }

    /**
     * 记录一次登录失败。
     *
     * <p>用 {@code failed_attempts = failed_attempts + 1} 原子自增，
     * 避免并发失败请求互相覆盖导致计数偏小；锁定判定再基于自增后的值单独执行。
     */
    private void recordFailure(Long userId, LocalDateTime now) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .setSql("failed_attempts = failed_attempts + 1"));

        SysUser current = userMapper.selectById(userId);
        if (current != null && current.getFailedAttempts() != null
                && current.getFailedAttempts() >= MAX_FAILED_ATTEMPTS
                && current.getLockedUntil() == null) {
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, userId)
                    .set(SysUser::getLockedUntil, now.plus(LOCK_DURATION)));
            log.warn("账号因连续登录失败被锁定 userId={} attempts={}", userId, current.getFailedAttempts());
        }
    }

    private UserProfile toProfile(SysUser user, List<String> roles) {
        return new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                roles,
                user.getCreatedAt());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
