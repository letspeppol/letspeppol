package org.letspeppol.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.letspeppol.app.service.DocumentService;
import org.letspeppol.app.service.SynchronizationGateService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;


@RequiredArgsConstructor
@Component
public class PostAuthProxyRequest extends OncePerRequestFilter {

    private final DocumentService documentService;
    private final SynchronizationGateService synchronizationGateService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/sapi/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth && authentication.isAuthenticated()) {
            Jwt jwt = jwtAuth.getToken();
            String peppolId = JwtUtil.getPeppolId(jwt);
            String tokenValue = jwt.getTokenValue();
            if (synchronizationGateService.tryStart(peppolId)) {
                // start a virtual thread (fire-and-forget) to check if the user has new documents
                Thread.startVirtualThread(() -> {
                    try {
                        documentService.synchronize(peppolId, tokenValue);
                    } catch (Exception e) {
                        logger.warn("Post-auth async task failed", e);
                    } finally {
                        synchronizationGateService.finish(peppolId);
                    }
                });
            }
        }
        filterChain.doFilter(request, response);
    }
}
