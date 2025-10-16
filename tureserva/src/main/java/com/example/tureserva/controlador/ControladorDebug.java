package com.example.tureserva.controlador;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class ControladorDebug {

    @GetMapping("/debug/auth")
    @ResponseBody
    public Map<String, Object> debugAuth() {
        Map<String, Object> info = new HashMap<>();
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null) {
            info.put("authenticated", auth.isAuthenticated());
            info.put("principal", auth.getPrincipal().getClass().getSimpleName());
            info.put("authorities", auth.getAuthorities());
            info.put("name", auth.getName());
            
            if (auth instanceof OAuth2AuthenticationToken) {
                OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) auth;
                OAuth2User oauth2User = oauth2Token.getPrincipal();
                info.put("oauth2", true);
                info.put("attributes", oauth2User.getAttributes());
                info.put("registrationId", oauth2Token.getAuthorizedClientRegistrationId());
            } else {
                info.put("oauth2", false);
            }
        } else {
            info.put("authenticated", false);
            info.put("message", "No authentication found");
        }
        
        return info;
    }
}