package com.ruinhome.auth;

import com.ruinhome.user.ManagerPeriod;
import com.ruinhome.user.UserAccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository userAccountRepository) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(header.substring(7));
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                var account = userAccountRepository.findByUsername(claims.getSubject()).orElse(null);
                if (account != null && account.isEnabled() && ManagerPeriod.isActiveToday(account)) {
                    String role = account.getRole().name();
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            account.getUsername(), null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            // token khong hop le: khong set authentication, xu ly o entry point
        }

        chain.doFilter(request, response);
    }
}
