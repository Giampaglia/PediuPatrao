package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administração de usuários — todas as operações exigem perfil ADMIN.
 * Regras contra elevação indevida de privilégios:
 *  - o perfil precisa ser um dos três perfis válidos;
 *  - ninguém altera o próprio perfil nem exclui o próprio usuário;
 *  - o último ADMIN não pode ser rebaixado nem excluído.
 */
@Service
public class UsuarioService {

    private static final Pattern FORMATO_USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,40}$");
    private static final int SENHA_MINIMA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final UsuarioLogado usuarioLogado;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService, UsuarioLogado usuarioLogado) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.usuarioLogado = usuarioLogado;
    }

    // Busca um usuário por ID
    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    // Lista todos os usuários
    @PreAuthorize("hasRole('ADMIN')")
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Cria um novo usuário (criptografando a senha)
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario salvarUsuario(Usuario dados) {
        String username = validarUsername(dados.getUsername());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com o username \"" + username + "\".");
        }
        validarSenha(dados.getPassword());
        if (dados.getRole() == null) {
            throw new RegraNegocioException("Selecione um perfil válido (ADMIN, GERENTE ou ATENDENTE).");
        }

        // Novo objeto: ignora qualquer id enviado na requisição (evita sobrescrever outro usuário)
        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setPassword(passwordEncoder.encode(dados.getPassword()));
        novo.setRole(dados.getRole());
        Usuario salvo = usuarioRepository.save(novo);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO, salvo.getId(),
                new ComparadorCampos().registrar("username", username).registrar("perfil", salvo.getRole()).lista(),
                null);
        return salvo;
    }

    // Atualiza username, perfil e (opcionalmente) a senha
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario atualizarUsuario(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        boolean proprioUsuario = existente.getUsername().equals(usuarioLogado.username());

        String usernameAnterior = existente.getUsername();
        Perfil perfilAnterior = existente.getRole();

        if (dados.getUsername() != null && !dados.getUsername().isBlank()) {
            String novoUsername = validarUsername(dados.getUsername());
            if (!novoUsername.equals(usernameAnterior) && usuarioRepository.findByUsername(novoUsername).isPresent()) {
                throw new RegraNegocioException("Já existe um usuário com o username \"" + novoUsername + "\".");
            }
            existente.setUsername(novoUsername);
        }

        Perfil novoPerfil = dados.getRole() == null ? perfilAnterior : dados.getRole();
        if (novoPerfil != perfilAnterior) {
            if (proprioUsuario) {
                throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
            }
            if (perfilAnterior == Perfil.ADMIN && usuarioRepository.countByRole(Perfil.ADMIN) <= 1) {
                throw new RegraNegocioException("Deve existir pelo menos um administrador no sistema.");
            }
            existente.setRole(novoPerfil);
        }

        // Só atualiza a senha se uma nova foi informada
        boolean senhaRedefinida = dados.getPassword() != null && !dados.getPassword().isBlank();
        if (senhaRedefinida) {
            validarSenha(dados.getPassword());
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
        }

        Usuario salvo = usuarioRepository.save(existente);

        ComparadorCampos alteracoes = new ComparadorCampos()
                .comparar("username", usernameAnterior, salvo.getUsername())
                .comparar("perfil", perfilAnterior, salvo.getRole());
        if (!alteracoes.isVazio() || senhaRedefinida) {
            // A senha nunca é gravada: apenas indica-se que foi redefinida
            auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO,
                    salvo.getId(), alteracoes.lista(), senhaRedefinida ? "Senha redefinida pelo administrador" : null);
        }
        return salvo;
    }

    // Deleta um usuário pelo ID
    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(usuarioLogado.username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (existente.getRole() == Perfil.ADMIN && usuarioRepository.countByRole(Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("Não é possível excluir o último administrador.");
        }
        usuarioRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO, id,
                new ComparadorCampos()
                        .comparar("username", existente.getUsername(), null)
                        .comparar("perfil", existente.getRole(), null)
                        .lista(),
                null);
    }

    private static String validarUsername(String username) {
        String valor = username == null ? "" : username.trim();
        if (!FORMATO_USERNAME.matcher(valor).matches()) {
            throw new RegraNegocioException("Username inválido: use de 3 a 40 caracteres (letras, números, ponto, hífen ou _).");
        }
        return valor;
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
    }
}
