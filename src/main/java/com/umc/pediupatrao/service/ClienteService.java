package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.util.DadosPessoais;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente novoCliente(Cliente cliente) {
        cliente.setId(null);
        return salvar(cliente);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll(Sort.by("nome"));
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        normalizar(cliente);
        validar(cliente);
        return cliente.getId() == null ? incluir(cliente) : atualizar(cliente);
    }

    // Exclusão restrita ao GERENTE (operação destrutiva), registrada na auditoria
    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE, id,
                new ComparadorCampos().comparar("nome", existente.getNome(), null).lista(), null);
    }

    private Cliente incluir(Cliente cliente) {
        Cliente salvo = clienteRepository.save(cliente);
        ComparadorCampos campos = new ComparadorCampos();
        campos.registrar("nome", salvo.getNome())
                .registrar("telefone", DadosPessoais.mascararTelefone(salvo.getTelefone()))
                .registrar("cep", salvo.getCep())
                .registrar("logradouro", salvo.getLogradouro())
                .registrar("numero", salvo.getNumero())
                .registrar("complemento", salvo.getComplemento())
                .registrar("bairro", salvo.getBairro())
                .registrar("cidade", salvo.getCidade())
                .registrar("estado", salvo.getEstado());
        auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE,
                salvo.getId(), campos.lista(), null);
        return salvo;
    }

    private Cliente atualizar(Cliente cliente) {
        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));

        // O formulário não envia o campo legado "endereco": mantém o valor existente
        if (cliente.getEndereco() == null) {
            cliente.setEndereco(anterior.getEndereco());
        }

        ComparadorCampos alteracoes = new ComparadorCampos()
                .comparar("nome", anterior.getNome(), cliente.getNome())
                .comparar("telefone", DadosPessoais.mascararTelefone(anterior.getTelefone()),
                        DadosPessoais.mascararTelefone(cliente.getTelefone()))
                .comparar("cep", anterior.getCep(), cliente.getCep())
                .comparar("logradouro", anterior.getLogradouro(), cliente.getLogradouro())
                .comparar("numero", anterior.getNumero(), cliente.getNumero())
                .comparar("complemento", anterior.getComplemento(), cliente.getComplemento())
                .comparar("bairro", anterior.getBairro(), cliente.getBairro())
                .comparar("cidade", anterior.getCidade(), cliente.getCidade())
                .comparar("estado", anterior.getEstado(), cliente.getEstado());

        // Telefone mudou só nos dígitos ocultos pela máscara: registra a alteração mesmo assim
        if (!igual(anterior.getTelefone(), cliente.getTelefone())
                && alteracoes.lista().stream().noneMatch(a -> a.getCampo().equals("telefone"))) {
            alteracoes.comparar("telefone", "(alterado)", DadosPessoais.mascararTelefone(cliente.getTelefone()));
        }

        Cliente salvo = clienteRepository.save(cliente);
        if (!alteracoes.isVazio()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE,
                    salvo.getId(), alteracoes.lista(), null);
        }
        return salvo;
    }

    private static void normalizar(Cliente c) {
        c.setNome(limpar(c.getNome()));
        c.setTelefone(limpar(c.getTelefone()));
        c.setCep(limpar(c.getCep()));
        c.setLogradouro(limpar(c.getLogradouro()));
        c.setNumero(limpar(c.getNumero()));
        c.setComplemento(limpar(c.getComplemento()));
        c.setBairro(limpar(c.getBairro()));
        c.setCidade(limpar(c.getCidade()));
        String estado = limpar(c.getEstado());
        c.setEstado(estado == null ? null : estado.toUpperCase());
        if (c.getId() != null && c.getId().isBlank()) {
            c.setId(null);
        }
    }

    private static void validar(Cliente c) {
        if (c.getNome() == null) {
            throw new RegraNegocioException("Informe o nome do cliente.");
        }
        if (c.getNome().length() > 120) {
            throw new RegraNegocioException("O nome deve ter no máximo 120 caracteres.");
        }
        if (c.getTelefone() == null) {
            throw new RegraNegocioException("Informe o telefone do cliente.");
        }
        int digitos = c.getTelefone().replaceAll("\\D", "").length();
        if (digitos < 10 || digitos > 11) {
            throw new RegraNegocioException("Telefone inválido: informe DDD + número (10 ou 11 dígitos).");
        }
        if (c.getEstado() != null && c.getEstado().length() != 2) {
            throw new RegraNegocioException("Estado deve ser a sigla com 2 letras (ex.: SP).");
        }
    }

    private static String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static boolean igual(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
