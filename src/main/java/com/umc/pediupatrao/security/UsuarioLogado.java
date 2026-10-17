package com.umc.pediupatrao.security;

import com.umc.pediupatrao.entity.Perfil;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Acesso ao usuário autenticado na requisição atual. */
@Component
public class UsuarioLogado {

    public static final String SISTEMA = "sistema";

    public String username() {
        Authentication auth = autenticacao();
        return auth == null ? SISTEMA : auth.getName();
    }

    /** Perfil(is) do usuário sem o prefixo ROLE_, ex.: "GERENTE". */
    public String perfil() {
        Authentication auth = autenticacao();
        if (auth == null) {
            return SISTEMA.toUpperCase();
        }
        String perfis = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.joining(","));
        return perfis.isEmpty() ? "SEM_PERFIL" : perfis;
    }

    public boolean temPerfil(Perfil perfil) {
        Authentication auth = autenticacao();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + perfil.name()));
    }

    private Authentication autenticacao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }
}
