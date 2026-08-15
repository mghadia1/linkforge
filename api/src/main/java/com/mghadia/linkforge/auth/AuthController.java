package com.mghadia.linkforge.auth;

import com.mghadia.linkforge.auth.dto.AuthDtos.LoginRequest;
import com.mghadia.linkforge.auth.dto.AuthDtos.RegisterRequest;
import com.mghadia.linkforge.auth.dto.AuthDtos.TokenResponse;
import com.mghadia.linkforge.user.User;
import com.mghadia.linkforge.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;
    private final JwtService tokens;

    public AuthController(UserService users, JwtService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    /** Registering logs you in; there is no reason to make a client immediately post again. */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = users.register(request.email(), request.password());
        JwtService.IssuedToken token = tokens.issue(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TokenResponse.bearer(token.value(), token.expiresAt()));
    }

    /**
     * One failure response for every way of failing. An unknown address and a wrong password are
     * indistinguishable from out here — in status, in body, and (see
     * {@link UserService#authenticate}) in how long they take.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return users.authenticate(request.email(), request.password())
                .map(user -> {
                    JwtService.IssuedToken token = tokens.issue(user);
                    return ResponseEntity.ok(TokenResponse.bearer(token.value(), token.expiresAt()));
                })
                .orElseThrow(InvalidCredentialsException::new);
    }
}
