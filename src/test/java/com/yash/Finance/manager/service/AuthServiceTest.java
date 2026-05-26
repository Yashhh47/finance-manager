package com.yash.Finance.manager.service;

import com.yash.Finance.manager.dto.request.LoginRequest;
import com.yash.Finance.manager.dto.request.RegisterRequest;
import com.yash.Finance.manager.entity.User;
import com.yash.Finance.manager.exception.ConflictException;
import com.yash.Finance.manager.exception.UnauthorizedException;
import com.yash.Finance.manager.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @InjectMocks
    private AuthService authService;

    private SecurityContext originalSecurityContext;

    @BeforeEach
    void setUp() {
        originalSecurityContext = SecurityContextHolder.getContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalSecurityContext);
    }

    @Test
    void loadUserByUsername_Success() {
        User user = new User();
        user.setUsername("test@user.com");
        user.setPassword("encodedPassword");

        when(userRepository.findByUsername("test@user.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = authService.loadUserByUsername("test@user.com");

        assertNotNull(userDetails);
        assertEquals("test@user.com", userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        verify(userRepository, times(1)).findByUsername("test@user.com");
    }

    @Test
    void loadUserByUsername_UserNotFound() {
        when(userRepository.findByUsername("notfound@user.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            authService.loadUserByUsername("notfound@user.com");
        });
        verify(userRepository, times(1)).findByUsername("notfound@user.com");
    }

    @Test
    void register_Success() {
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setUsername("new@user.com");
        regRequest.setPassword("rawPassword");
        regRequest.setFullName("John Doe");
        regRequest.setPhoneNumber("1234567890");

        User savedUser = new User();
        savedUser.setId(100L);
        savedUser.setUsername("new@user.com");

        when(userRepository.existsByUsername("new@user.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Map<String, Object> result = authService.register(regRequest);

        assertNotNull(result);
        assertEquals("User registered successfully", result.get("message"));
        assertEquals(100L, result.get("userId"));
        verify(userRepository, times(1)).existsByUsername("new@user.com");
        verify(passwordEncoder, times(1)).encode("rawPassword");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_DuplicateUsername() {
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setUsername("exist@user.com");

        when(userRepository.existsByUsername("exist@user.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> {
            authService.register(regRequest);
        });
        verify(userRepository, times(1)).existsByUsername("exist@user.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("test@user.com");
        loginRequest.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(request.getSession(true)).thenReturn(session);

        Map<String, String> result = authService.login(loginRequest, request);

        assertNotNull(result);
        assertEquals("Login successful", result.get("message"));
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(request, times(1)).getSession(true);
        verify(session, times(1)).setAttribute(any(), any());
    }

    @Test
    void logout_WithSession() {
        when(request.getSession(false)).thenReturn(session);

        Map<String, String> result = authService.logout(request);

        assertNotNull(result);
        assertEquals("Logout successful", result.get("message"));
        verify(session, times(1)).invalidate();
    }

    @Test
    void logout_NoSession() {
        when(request.getSession(false)).thenReturn(null);

        Map<String, String> result = authService.logout(request);

        assertNotNull(result);
        assertEquals("Logout successful", result.get("message"));
        verify(session, never()).invalidate();
    }

    @Test
    void getCurrentUser_Success() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test@user.com");
        
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        User user = new User();
        user.setUsername("test@user.com");
        when(userRepository.findByUsername("test@user.com")).thenReturn(Optional.of(user));

        User current = authService.getCurrentUser();

        assertNotNull(current);
        assertEquals("test@user.com", current.getUsername());
        verify(userRepository, times(1)).findByUsername("test@user.com");
    }

    @Test
    void getCurrentUser_Unauthorized() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("anonymousUser");
        
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("anonymousUser")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> {
            authService.getCurrentUser();
        });
        verify(userRepository, times(1)).findByUsername("anonymousUser");
    }
}
