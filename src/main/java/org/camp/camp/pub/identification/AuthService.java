package org.camp.camp.pub.identification;

import lombok.RequiredArgsConstructor;
import org.camp.camp.exceptions.EmailAlreadyExistsException;
import org.camp.camp.exceptions.InvalidCredentialsException;
import org.camp.camp.exceptions.InvalidRefreshTokenException;
import org.camp.camp.jwt.JwtService;
import org.camp.camp.models.RefreshToken;
import org.camp.camp.models.User;
import org.camp.camp.pub.identification.dto.AuthResponse;
import org.camp.camp.pub.identification.dto.LoginRequest;
import org.camp.camp.pub.identification.dto.RefreshRequest;
import org.camp.camp.pub.identification.dto.RegisterRequest;
import org.camp.camp.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private JwtService jwtService;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(InvalidCredentialsException::new);

        if(user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    public AuthResponse register(RegisterRequest request) {
        if(userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        if(userRepository.existsByUsername(request.getUserName())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUserName());
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUserName())
                .displayName(request.getDisplayName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashToken(rawRefreshToken))
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiry))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .expiresIn(accessTokenExpiry)
                .user(AuthResponse.UserSummary.builder()
                        .id(user.getId())
                        .userName(user.getUsername())
                        .displayName(user.getDisplayName())
                        .build()
                ).build();
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshToken existingToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash).orElseThrow(InvalidRefreshTokenException::new);

        if(existingToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        existingToken.setRevoked(true);
        refreshTokenRepository.save(existingToken);

        User user = userRepository.findById(existingToken.getUserId()).orElseThrow(InvalidRefreshTokenException::new);

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing algorithm not available", e);
        }
    }
}
