package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria um usuário de cada perfil para a demonstração (senha padrão "teste123",
 * troque em produção) e alguns produtos caso o catálogo esteja vazio.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String SENHA_PADRAO = "teste123";

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarios, ProdutoRepository produtos, PasswordEncoder encoder) {
        return args -> {
            garantirUsuario(usuarios, encoder, "admin", Perfil.ADMIN);
            garantirUsuario(usuarios, encoder, "gerente01", Perfil.GERENTE);
            garantirUsuario(usuarios, encoder, "atendente01", Perfil.ATENDENTE);

            if (produtos.count() == 0) {
                produtos.save(produto("Pizza Calabresa", "Pizza", 49.90));
                produtos.save(produto("Pizza Mussarela", "Pizza", 45.00));
                produtos.save(produto("Pizza Portuguesa", "Pizza", 54.90));
                produtos.save(produto("Refrigerante 2L", "Bebida", 12.00));
                log.info("Produtos de exemplo criados.");
            }
        };
    }

    private static void garantirUsuario(UsuarioRepository usuarios, PasswordEncoder encoder, String username, Perfil perfil) {
        usuarios.findByUsername(username).ifPresentOrElse(existente -> {
            // Usuário criado pela versão anterior sem perfil
            if (existente.getRole() == null) {
                existente.setRole(perfil);
                usuarios.save(existente);
                log.info("Perfil {} atribuído ao usuário {}", perfil, username);
            }
        }, () -> {
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPassword(encoder.encode(SENHA_PADRAO));
            usuario.setRole(perfil);
            usuarios.save(usuario);
            log.info("Usuário {} ({}) criado.", username, perfil);
        });
    }

    private static Produto produto(String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }
}
