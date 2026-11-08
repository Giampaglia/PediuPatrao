// Dashboard administrativo — consome a API /api/dashboard (RF06) e trata
// atualização periódica, carregamento, dados vazios e falhas (RF07).
// Depende de: api.js (window.api / window.escapeHtml) e Chart.js 4.
(function () {
    'use strict';

    const API = '/api/dashboard';
    const INTERVALO_MS = 60000;
    const TEMPO_LIMITE_MS = 20000;
    const CHAVE_AUTO = 'pediupatrao.dashboard.auto';

    // Papéis de cor (mesmos valores de dashboard.css)
    const COR = {
        serie1: '#2a78d6',
        serie1Lavagem: 'rgba(42, 120, 214, 0.10)',
        serie2: '#eb6834',
        grade: '#e1e0d9',
        eixo: '#c3c2b7',
        suave: '#898781',
        tinta: '#0b0b0b',
        tinta2: '#52514e',
        superficie: '#fcfcfb'
    };

    const STATUS_CLASSE = {
        RECEBIDO: 'bg-secondary',
        EM_PREPARACAO: 'bg-warning text-dark',
        PRONTO: 'bg-primary',
        SAIU_PARA_ENTREGA: 'bg-info text-dark',
        RETIRADO: 'bg-info text-dark',
        FINALIZADO: 'bg-success',
        CANCELADO: 'bg-danger'
    };
    const EM_ANDAMENTO = ['RECEBIDO', 'EM_PREPARACAO', 'PRONTO', 'SAIU_PARA_ENTREGA', 'RETIRADO'];

    // ---------- formatação ----------
    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const inteiro = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });
    const decimal1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    const num = (v) => (v === null || v === undefined ? 0 : Number(v));
    const fmtMoeda = (v) => moeda.format(num(v));
    const fmtInt = (v) => inteiro.format(num(v));
    const fmtPct = (v) => (v === null || v === undefined ? '—' : decimal1.format(num(v)) + '%');
    function fmtMoedaCompacta(v) {
        const n = num(v);
        if (Math.abs(n) >= 1000000) return 'R$ ' + decimal1.format(n / 1000000) + ' mi';
        if (Math.abs(n) >= 10000) return 'R$ ' + inteiro.format(n / 1000) + ' mil';
        if (Math.abs(n) >= 1000) return 'R$ ' + decimal1.format(n / 1000) + ' mil';
        return 'R$ ' + inteiro.format(n);
    }
    function fmtData(iso) {
        if (!iso) return '';
        const [a, m, d] = iso.substring(0, 10).split('-');
        return d + '/' + m + '/' + a;
    }
    function fmtDataHora(iso) {
        if (!iso) return '—';
        const d = new Date(iso);
        return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' '
            + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    }
    function fmtMinutos(min) {
        if (min === null || min === undefined) return '—';
        const m = Math.round(num(min));
        if (m < 60) return m + ' min';
        return Math.floor(m / 60) + ' h ' + String(m % 60).padStart(2, '0') + ' min';
    }
    const isoLocal = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    const esc = (v) => window.escapeHtml(v);

    // ---------- estado ----------
    const estado = {
        metrica: 'faturamento',
        agrupamento: 'DIA',
        criterio: 'QUANTIDADE',
        opcoes: null,
        produtos: [],
        controlador: null,
        timer: null,
        ultimaAtualizacao: null,
        sessaoExpirada: false,
        dados: {}
    };
    const graficos = {};
    const $ = (id) => document.getElementById(id);

    // =====================================================================
    // Comunicação com a API
    // =====================================================================

    class ErroApi extends Error {
        constructor(status, mensagem) {
            super(mensagem);
            this.status = status;
        }
    }

    async function obter(caminho, extras, sinal) {
        const params = parametrosFiltro();
        Object.entries(extras || {}).forEach(([k, v]) => params.set(k, v));
        const url = API + caminho + (params.toString() ? '?' + params.toString() : '');

        // Cancela se o servidor demorar demais ou se uma nova atualização substituir esta
        const controlador = new AbortController();
        let estourou = false;
        const timer = setTimeout(() => { estourou = true; controlador.abort(); }, TEMPO_LIMITE_MS);
        const repassar = () => controlador.abort();
        if (sinal) sinal.addEventListener('abort', repassar);

        let resposta;
        try {
            resposta = await window.api(url, { signal: controlador.signal });
        } catch (e) {
            if (estourou) throw new ErroApi(0, 'O servidor demorou para responder.');
            if (e.name === 'AbortError') throw e;
            throw new ErroApi(0, 'Sem conexão com o servidor.');
        } finally {
            clearTimeout(timer);
            if (sinal) sinal.removeEventListener('abort', repassar);
        }
        if (!resposta.ok) {
            const msg = resposta.dados && resposta.dados.mensagem ? resposta.dados.mensagem : 'Erro ' + resposta.status + ' ao consultar a API.';
            throw new ErroApi(resposta.status, msg);
        }
        return resposta.dados;
    }

    function parametrosFiltro() {
        const p = new URLSearchParams();
        const inicio = $('f-inicio').value;
        const fim = $('f-fim').value;
        if (inicio) p.set('dataInicio', inicio);
        if (fim) p.set('dataFim', fim);
        statusSelecionados().forEach((s) => p.append('status', s));
        if ($('f-tipo').value) p.set('tipoEntrega', $('f-tipo').value);
        if ($('f-categoria').value) p.set('categoria', $('f-categoria').value);
        if ($('f-produto').value) p.set('produtoId', $('f-produto').value);
        return p;
    }

    function statusSelecionados() {
        return Array.from(document.querySelectorAll('#f-status input:checked')).map((i) => i.value);
    }

    // =====================================================================
    // Ciclo de atualização (RF07)
    // =====================================================================

    const SECOES = {
        resumo: { caminho: '/resumo', render: renderResumo, cartoes: ['db-indicadores'] },
        vendas: { caminho: '/vendas', extras: () => ({ agrupamento: estado.agrupamento }), render: renderVendas, cartoes: ['c-vendas'] },
        hora: { caminho: '/vendas', extras: () => ({ agrupamento: 'HORA_DO_DIA' }), render: renderHora, cartoes: ['c-hora'] },
        semana: { caminho: '/vendas', extras: () => ({ agrupamento: 'DIA_DA_SEMANA' }), render: renderSemana, cartoes: ['c-semana'] },
        status: { caminho: '/pedidos/status', render: renderStatus, cartoes: ['c-status'] },
        ranking: { caminho: '/produtos/mais-vendidos', extras: () => ({ ordenarPor: estado.criterio, limite: 10 }), render: renderRanking, cartoes: ['c-ranking'] },
        categorias: { caminho: '/vendas/categorias', render: renderCategorias, cartoes: ['c-categorias'] },
        tipos: { caminho: '/vendas/tipos-entrega', render: renderTipos, cartoes: ['c-tipos'] },
        recentes: { caminho: '/pedidos/recentes', extras: () => ({ limite: 10 }), render: renderRecentes, cartoes: ['c-recentes'] }
    };

    async function atualizar(nomes) {
        if (estado.sessaoExpirada) return;
        const erroFiltro = validarFiltros();
        $('f-erro').textContent = erroFiltro || '';
        if (erroFiltro) return;

        const lista = nomes || Object.keys(SECOES);
        // Atualização completa cancela a anterior, evitando respostas fora de ordem
        if (!nomes && estado.controlador) estado.controlador.abort();
        const controlador = new AbortController();
        if (!nomes) estado.controlador = controlador;

        lista.forEach((n) => SECOES[n].cartoes.forEach((c) => marcarCarregando(c, true)));
        const resultados = await Promise.allSettled(lista.map((n) => {
            const s = SECOES[n];
            return obter(s.caminho, s.extras ? s.extras() : null, controlador.signal);
        }));

        if (controlador.signal.aborted) return;
        let falhas = 0;
        resultados.forEach((r, i) => {
            const nome = lista[i];
            const secao = SECOES[nome];
            secao.cartoes.forEach((c) => marcarCarregando(c, false));
            if (r.status === 'fulfilled') {
                estado.dados[nome] = r.value;
                secao.cartoes.forEach((c) => limparEstado(c));
                try {
                    secao.render(r.value);
                } catch (e) {
                    console.error('Falha ao exibir ' + nome, e);
                    secao.cartoes.forEach((c) => mostrarErro(c, 'Não foi possível exibir estes dados.', nome));
                }
            } else if (r.reason && r.reason.name !== 'AbortError') {
                falhas++;
                tratarErro(r.reason, nome);
            }
        });

        if (falhas === 0) {
            estado.ultimaAtualizacao = new Date();
            $('db-alerta').innerHTML = '';
        }
        mostrarHoraAtualizacao(falhas);
    }

    function tratarErro(erro, nome) {
        const secao = SECOES[nome];
        if (erro.status === 401) {
            estado.sessaoExpirada = true;
            pararAuto();
            alertaGeral('warning', 'Sua sessão expirou. <a href="/login" class="alert-link">Entre novamente</a> para continuar acompanhando o dashboard.');
            return;
        }
        if (erro.status === 403) {
            pararAuto();
            alertaGeral('danger', 'Seu perfil não tem acesso ao dashboard. Ele é exclusivo de administradores e gerentes.');
            return;
        }
        if (erro.status === 400) {
            // Filtro rejeitado pela API: a mensagem explica o que corrigir
            $('f-erro').textContent = erro.message;
        }
        const temDados = estado.dados[nome] !== undefined;
        const msg = erro.status === 503 || erro.status === 0
            ? erro.message + (temDados ? ' Exibindo os últimos dados carregados.' : '')
            : erro.message;
        secao.cartoes.forEach((c) => {
            if (temDados && erro.status !== 400) {
                // mantém o último dado válido na tela e avisa no topo
                alertaGeral('warning', esc(msg) + ' <button type="button" class="btn btn-sm btn-link alert-link p-0 align-baseline" data-tentar>Tentar novamente</button>');
            } else {
                mostrarErro(c, msg, nome);
            }
        });
    }

    function validarFiltros() {
        const inicio = $('f-inicio').value;
        const fim = $('f-fim').value;
        if (!inicio || !fim) return 'Informe as datas inicial e final.';
        if (inicio > fim) return 'A data inicial não pode ser posterior à data final.';
        const limite = estado.opcoes ? estado.opcoes.limitePeriodoDias : 366;
        const dias = Math.round((new Date(fim) - new Date(inicio)) / 86400000) + 1;
        if (dias > limite) return 'O período não pode ultrapassar ' + limite + ' dias.';
        return '';
    }

    function marcarCarregando(id, ativo) {
        const el = $(id);
        if (!el) return;
        el.querySelectorAll('.db-card').forEach((c) => c.classList.toggle('db-atualizando', ativo));
        if (el.classList.contains('db-card')) el.classList.toggle('db-atualizando', ativo);
        if (id === 'db-indicadores') el.classList.toggle('db-carregando', ativo && !estado.dados.resumo);
    }

    function limparEstado(id) {
        const est = $(id) && $(id).querySelector(':scope > .db-estado');
        if (est) est.innerHTML = '';
    }

    function mostrarVazio(id, mensagem) {
        const est = $(id).querySelector(':scope > .db-estado');
        if (est) est.innerHTML = '<i class="bi bi-inbox fs-3" aria-hidden="true"></i><span>' + esc(mensagem) + '</span>';
    }

    function mostrarErro(id, mensagem, nome) {
        const el = $(id);
        const est = el && el.querySelector(':scope > .db-estado');
        if (est) {
            est.innerHTML = '<i class="bi bi-exclamation-triangle fs-4" aria-hidden="true"></i><span>' + esc(mensagem)
                + '</span><button type="button" class="btn btn-sm btn-outline-secondary" data-tentar="' + esc(nome) + '">Tentar novamente</button>';
        } else if (id === 'db-indicadores') {
            alertaGeral('danger', 'Indicadores: ' + esc(mensagem) + ' <button type="button" class="btn btn-sm btn-link alert-link p-0 align-baseline" data-tentar="resumo">Tentar novamente</button>');
        }
    }

    function alertaGeral(tipo, html) {
        $('db-alerta').innerHTML = '<div class="alert alert-' + tipo + ' py-2 mb-3" role="alert">' + html + '</div>';
    }

    function mostrarHoraAtualizacao(falhas) {
        const el = $('db-atualizado');
        if (!estado.ultimaAtualizacao) {
            el.textContent = falhas ? 'Falha ao carregar' : '';
            return;
        }
        const hora = estado.ultimaAtualizacao.toLocaleTimeString('pt-BR');
        el.textContent = (falhas ? 'Dados de ' : 'Atualizado às ') + hora;
    }

    // ---------- atualização automática ----------
    function iniciarAuto() {
        pararAuto();
        if (!$('db-auto').checked || estado.sessaoExpirada) return;
        estado.timer = setInterval(() => {
            if (document.visibilityState === 'visible') atualizar();
        }, INTERVALO_MS);
    }

    function pararAuto() {
        if (estado.timer) clearInterval(estado.timer);
        estado.timer = null;
    }

    document.addEventListener('visibilitychange', () => {
        // Ao voltar para a aba, atualiza se os dados estiverem desatualizados
        if (document.visibilityState === 'visible' && $('db-auto').checked && estado.ultimaAtualizacao
            && Date.now() - estado.ultimaAtualizacao.getTime() > INTERVALO_MS) {
            atualizar();
        }
    });

    // =====================================================================
    // Gráficos (Chart.js)
    // =====================================================================

    // Linha vertical acompanhando o ponteiro nos gráficos de linha
    const miraVertical = {
        id: 'miraVertical',
        afterDatasetsDraw(chart) {
            const ativos = chart.tooltip && chart.tooltip.getActiveElements();
            if (!ativos || !ativos.length || chart.config.type !== 'line') return;
            const x = ativos[0].element.x;
            const { top, bottom } = chart.chartArea;
            const ctx = chart.ctx;
            ctx.save();
            ctx.strokeStyle = COR.eixo;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(x, top);
            ctx.lineTo(x, bottom);
            ctx.stroke();
            ctx.restore();
        }
    };

    // Valor escrito na ponta das barras horizontais
    const rotuloNaPonta = {
        id: 'rotuloNaPonta',
        afterDatasetsDraw(chart, _args, opcoes) {
            if (!opcoes || !opcoes.formatar || chart.options.indexAxis !== 'y') return;
            const ctx = chart.ctx;
            ctx.save();
            ctx.font = '12px system-ui, -apple-system, "Segoe UI", sans-serif';
            ctx.fillStyle = COR.tinta2;
            ctx.textBaseline = 'middle';
            chart.getDatasetMeta(0).data.forEach((barra, i) => {
                const valor = chart.data.datasets[0].data[i];
                if (!valor) return;
                ctx.fillText(opcoes.formatar(valor, i), barra.x + 6, barra.y);
            });
            ctx.restore();
        }
    };

    function configurarChart() {
        Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", sans-serif';
        Chart.defaults.font.size = 12;
        Chart.defaults.color = COR.tinta2;
        Chart.defaults.borderColor = COR.grade;
        Chart.defaults.plugins.legend.display = false;
        Chart.defaults.plugins.tooltip.backgroundColor = '#0b0b0b';
        Chart.defaults.plugins.tooltip.padding = 10;
        Chart.defaults.plugins.tooltip.cornerRadius = 6;
        Chart.defaults.plugins.tooltip.displayColors = false;
        Chart.defaults.animation.duration = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 300;
        Chart.defaults.maintainAspectRatio = false;
        Chart.register(miraVertical, rotuloNaPonta);
    }

    function desenhar(idCartao, config) {
        const canvas = $(idCartao).querySelector('canvas');
        if (graficos[idCartao]) {
            const g = graficos[idCartao];
            if (g.config.type === config.type) {
                g.data = config.data;
                g.options = config.options;
                g.update();
                return g;
            }
            g.destroy();
        }
        graficos[idCartao] = new Chart(canvas, config);
        return graficos[idCartao];
    }

    const eixoValor = (formatar) => ({
        beginAtZero: true,
        grid: { color: COR.grade, drawTicks: false },
        border: { display: false },
        ticks: { color: COR.suave, padding: 8, maxTicksLimit: 6, callback: formatar }
    });
    const eixoCategoria = () => ({
        grid: { display: false },
        border: { color: COR.eixo },
        ticks: { color: COR.suave, autoSkipPadding: 12, maxRotation: 0 }
    });

    function barrasVerticais(rotulos, valores, formatarTooltip) {
        return {
            type: 'bar',
            data: { labels: rotulos, datasets: [{ data: valores, backgroundColor: COR.serie1, hoverBackgroundColor: '#1c5cab', borderRadius: 4, borderSkipped: 'start', maxBarThickness: 24, categoryPercentage: 0.8, barPercentage: 0.9 }] },
            options: {
                interaction: { mode: 'index', intersect: false },
                scales: { x: eixoCategoria(), y: eixoValor((v) => fmtInt(v)) },
                plugins: { tooltip: { callbacks: { label: formatarTooltip } } }
            }
        };
    }

    // Mede o texto com a mesma fonte do rótulo desenhado na ponta das barras
    const medidor = document.createElement('canvas').getContext('2d');
    function larguraTexto(texto) {
        medidor.font = '12px system-ui, -apple-system, "Segoe UI", sans-serif';
        return medidor.measureText(texto).width;
    }

    function barrasHorizontais(rotulos, valores, formatarValor, formatarTooltip) {
        // Reserva à direita exatamente o espaço do maior rótulo, para que nunca seja cortado
        const folga = Math.ceil(Math.max(0, ...valores.map((v, i) => (v ? larguraTexto(formatarValor(v, i)) : 0)))) + 12;
        return {
            type: 'bar',
            data: { labels: rotulos, datasets: [{ data: valores, backgroundColor: COR.serie1, hoverBackgroundColor: '#1c5cab', borderRadius: 4, borderSkipped: 'start', maxBarThickness: 18, categoryPercentage: 0.8, barPercentage: 0.9 }] },
            options: {
                indexAxis: 'y',
                layout: { padding: { right: folga } },
                interaction: { mode: 'index', intersect: false, axis: 'y' },
                scales: {
                    x: { beginAtZero: true, display: false },
                    y: { grid: { display: false }, border: { color: COR.eixo }, ticks: { color: COR.tinta2, autoSkip: false } }
                },
                plugins: {
                    rotuloNaPonta: { formatar: formatarValor },
                    tooltip: { callbacks: { label: formatarTooltip } }
                }
            }
        };
    }

    // ---------- visão em tabela (acessibilidade) ----------
    function tabela(idCartao, colunas, linhas) {
        const alvo = $(idCartao).querySelector('.db-tabela');
        const cab = colunas.map((c) => '<th scope="col"' + (c.num ? ' class="text-end"' : '') + '>' + esc(c.titulo) + '</th>').join('');
        const corpo = linhas.length
            ? linhas.map((l) => '<tr>' + colunas.map((c, i) => '<td' + (c.num ? ' class="text-end"' : '') + '>' + esc(l[i]) + '</td>').join('') + '</tr>').join('')
            : '<tr><td colspan="' + colunas.length + '" class="text-center text-secondary">Sem dados no período.</td></tr>';
        alvo.innerHTML = '<table class="table table-sm mb-0 db-tabela-dados"><thead><tr>' + cab + '</tr></thead><tbody>' + corpo + '</tbody></table>';
    }

    // =====================================================================
    // Renderização das seções
    // =====================================================================

    function renderResumo(r) {
        $('db-periodo').textContent = 'Período: ' + fmtData(r.periodo.dataInicio) + ' a ' + fmtData(r.periodo.dataFim)
            + ' (' + r.periodo.dias + (r.periodo.dias === 1 ? ' dia)' : ' dias)');
        $('k-faturamento').textContent = fmtMoeda(r.faturamento);
        $('k-pedidos').textContent = fmtInt(r.pedidosValidos);
        $('k-ticket').textContent = fmtMoeda(r.ticketMedio);
        $('k-itens').textContent = fmtInt(r.itensVendidos);
        $('n-itens').textContent = r.pedidosValidos ? decimal1.format(r.itensVendidos / r.pedidosValidos) + ' itens por pedido' : '';
        $('k-cancelamento').textContent = fmtPct(r.taxaCancelamento);
        $('n-cancelamento').textContent = fmtInt(r.pedidosCancelados) + (r.pedidosCancelados === 1 ? ' pedido cancelado' : ' pedidos cancelados');
        $('k-descontos').textContent = fmtMoeda(r.totalDescontos);
        $('n-descontos').textContent = 'em ' + fmtInt(r.pedidosComDesconto) + (r.pedidosComDesconto === 1 ? ' pedido' : ' pedidos');
        $('k-tempo').textContent = fmtMinutos(r.tempoMedioAteSaidaMinutos);

        const c = r.comparativo;
        const ref = 'vs ' + fmtData(c.periodoAnterior.dataInicio).substring(0, 5) + '–' + fmtData(c.periodoAnterior.dataFim).substring(0, 5);
        delta('d-faturamento', c.variacaoFaturamento, ref, fmtMoeda(c.faturamento));
        delta('d-pedidos', c.variacaoPedidos, ref, fmtInt(c.pedidosValidos));
        delta('d-ticket', c.variacaoTicketMedio, ref, fmtMoeda(c.ticketMedio));

        const q = r.qualidadeDados;
        const avisos = [];
        if (q.pedidosSemStatus) avisos.push(q.pedidosSemStatus + ' pedido(s) sem status reconhecido fora dos cálculos');
        if (q.pedidosSemDataEntrada) avisos.push(q.pedidosSemDataEntrada + ' pedido(s) antigo(s) sem data de entrada não aparecem em nenhum período');
        $('k-qualidade').textContent = avisos.join(' · ');
    }

    function delta(id, variacao, referencia, valorAnterior) {
        const el = $(id);
        if (variacao === null || variacao === undefined) {
            el.innerHTML = '<span title="Sem vendas no período anterior">Sem base de comparação</span>';
            return;
        }
        const v = num(variacao);
        const icone = v > 0 ? 'bi-arrow-up-right' : v < 0 ? 'bi-arrow-down-right' : 'bi-arrow-right';
        const classe = v > 0 ? 'db-sobe-bom' : v < 0 ? 'db-desce-ruim' : '';
        const sinal = v > 0 ? '+' : '';
        el.innerHTML = '<span class="' + classe + '"><i class="bi ' + icone + '" aria-hidden="true"></i> ' + sinal + fmtPct(v)
            + '</span> <span title="Período anterior: ' + esc(valorAnterior) + '">' + esc(referencia) + '</span>';
    }

    const METRICAS = {
        faturamento: { nome: 'Faturamento', fmt: fmtMoeda, eixo: fmtMoedaCompacta },
        pedidos: { nome: 'Pedidos', fmt: (v) => fmtInt(v) + ' pedidos', eixo: (v) => fmtInt(v) },
        ticketMedio: { nome: 'Ticket médio', fmt: fmtMoeda, eixo: fmtMoedaCompacta }
    };

    function renderVendas(s) {
        const m = METRICAS[estado.metrica];
        const pontos = s.pontos;
        $('s-vendas').textContent = fmtMoeda(s.faturamentoTotal) + ' em ' + fmtInt(s.totalPedidos) + ' pedidos válidos';
        if (!s.totalPedidos) {
            mostrarVazio('c-vendas', 'Nenhuma venda no período selecionado.');
        }
        const valores = pontos.map((p) => num(p[estado.metrica]));
        const comMarcador = pontos.length <= 16;
        desenhar('c-vendas', {
            type: 'line',
            data: {
                labels: pontos.map((p) => p.rotulo),
                datasets: [{
                    label: m.nome, data: valores, borderColor: COR.serie1, backgroundColor: COR.serie1Lavagem, fill: 'origin',
                    // monotone: suaviza sem "inventar" picos ou valores abaixo de zero
                    borderWidth: 2, cubicInterpolationMode: 'monotone', borderJoinStyle: 'round', borderCapStyle: 'round',
                    pointRadius: comMarcador ? 4 : 0, pointHoverRadius: 5, pointBackgroundColor: COR.serie1,
                    pointBorderColor: COR.superficie, pointBorderWidth: 2, pointHitRadius: 12
                }]
            },
            options: {
                interaction: { mode: 'index', intersect: false },
                scales: { x: eixoCategoria(), y: eixoValor(m.eixo) },
                plugins: {
                    tooltip: {
                        callbacks: {
                            title: (itens) => pontos[itens[0].dataIndex].rotulo,
                            label: (item) => {
                                const p = pontos[item.dataIndex];
                                return [fmtMoeda(p.faturamento), fmtInt(p.pedidos) + ' pedidos', 'Ticket médio ' + fmtMoeda(p.ticketMedio)];
                            }
                        }
                    }
                }
            }
        });
        tabela('c-vendas', [{ titulo: 'Período' }, { titulo: 'Pedidos', num: true }, { titulo: 'Faturamento', num: true }, { titulo: 'Ticket médio', num: true }],
            pontos.map((p) => [p.rotulo, fmtInt(p.pedidos), fmtMoeda(p.faturamento), fmtMoeda(p.ticketMedio)]));
    }

    function renderDistribuicao(idCartao, idSub, s, descrever) {
        const pontos = s.pontos;
        if (!s.totalPedidos) {
            $(idSub).textContent = '';
            mostrarVazio(idCartao, 'Nenhuma venda no período selecionado.');
        } else {
            const pico = pontos.reduce((a, b) => (b.pedidos > a.pedidos ? b : a));
            $(idSub).textContent = descrever(pico);
        }
        desenhar(idCartao, barrasVerticais(pontos.map((p) => p.rotulo), pontos.map((p) => p.pedidos),
            (item) => {
                const p = pontos[item.dataIndex];
                return [fmtInt(p.pedidos) + ' pedidos', fmtMoeda(p.faturamento)];
            }));
        tabela(idCartao, [{ titulo: 'Período' }, { titulo: 'Pedidos', num: true }, { titulo: 'Faturamento', num: true }],
            pontos.map((p) => [p.rotulo, fmtInt(p.pedidos), fmtMoeda(p.faturamento)]));
    }

    function renderHora(s) {
        renderDistribuicao('c-hora', 's-hora', s, (pico) => {
            const fim = String((Number(pico.chave) + 1) % 24).padStart(2, '0') + 'h';
            return 'Pico entre ' + pico.rotulo + ' e ' + fim + ' (' + fmtInt(pico.pedidos) + ' pedidos no período)';
        });
    }

    function renderSemana(s) {
        renderDistribuicao('c-semana', 's-semana', s, (pico) => 'Dia mais movimentado: ' + pico.rotulo + ' (' + fmtInt(pico.pedidos) + ' pedidos)');
    }

    function renderStatus(s) {
        $('s-status').textContent = fmtInt(s.total) + ' pedidos no período';
        $('r-status').innerHTML =
            '<div><strong>' + fmtInt(s.emAndamento) + '</strong><span>em andamento</span></div>'
            + '<div><strong>' + fmtInt(s.finalizados) + '</strong><span>finalizados</span></div>'
            + '<div><strong>' + fmtInt(s.cancelados) + '</strong><span>cancelados</span></div>';
        if (!s.total) mostrarVazio('c-status', 'Nenhum pedido no período selecionado.');
        const itens = s.porStatus;
        desenhar('c-status', barrasHorizontais(itens.map((i) => i.descricao), itens.map((i) => i.quantidade),
            (v, i) => fmtInt(v) + ' · ' + fmtPct(itens[i].percentual),
            (item) => {
                const i = itens[item.dataIndex];
                return [fmtInt(i.quantidade) + ' pedidos (' + fmtPct(i.percentual) + ')', fmtMoeda(i.valorTotal)];
            }));
        tabela('c-status', [{ titulo: 'Status' }, { titulo: 'Pedidos', num: true }, { titulo: '%', num: true }, { titulo: 'Valor', num: true }],
            itens.map((i) => [i.descricao, fmtInt(i.quantidade), fmtPct(i.percentual), fmtMoeda(i.valorTotal)]));
    }

    function renderRanking(r) {
        const porFaturamento = r.criterio === 'FATURAMENTO';
        $('s-ranking').textContent = 'Top ' + r.produtos.length + ' por ' + (porFaturamento ? 'faturamento' : 'quantidade vendida')
            + ' · ' + fmtInt(r.totalItensVendidos) + ' itens no total';
        if (!r.produtos.length) mostrarVazio('c-ranking', 'Nenhum produto vendido no período selecionado.');
        const p = r.produtos;
        desenhar('c-ranking', barrasHorizontais(
            p.map((x) => x.posicao + 'º ' + x.nome),
            p.map((x) => num(porFaturamento ? x.faturamento : x.quantidade)),
            (v, i) => (porFaturamento ? fmtMoedaCompacta(v) : fmtInt(v)) + ' · ' + fmtPct(p[i].participacao),
            (item) => {
                const x = p[item.dataIndex];
                return [x.categoria, fmtInt(x.quantidade) + ' unidades em ' + fmtInt(x.pedidos) + ' pedidos', fmtMoeda(x.faturamento)];
            }));
        tabela('c-ranking', [{ titulo: '#', num: true }, { titulo: 'Produto' }, { titulo: 'Categoria' }, { titulo: 'Quantidade', num: true }, { titulo: 'Faturamento', num: true }, { titulo: 'Pedidos', num: true }, { titulo: 'Participação', num: true }],
            p.map((x) => [x.posicao, x.nome, x.categoria, fmtInt(x.quantidade), fmtMoeda(x.faturamento), fmtInt(x.pedidos), fmtPct(x.participacao)]));
    }

    function renderCategorias(v) {
        const c = v.categorias;
        $('s-categorias').textContent = fmtMoeda(v.faturamentoTotal) + ' no período';
        if (!c.length) mostrarVazio('c-categorias', 'Nenhuma venda no período selecionado.');
        desenhar('c-categorias', barrasHorizontais(c.map((x) => x.categoria), c.map((x) => num(x.faturamento)),
            (val, i) => fmtMoedaCompacta(val) + ' · ' + fmtPct(c[i].participacao),
            (item) => {
                const x = c[item.dataIndex];
                return [fmtMoeda(x.faturamento) + ' (' + fmtPct(x.participacao) + ')', fmtInt(x.quantidade) + ' itens'];
            }));
        tabela('c-categorias', [{ titulo: 'Categoria' }, { titulo: 'Itens', num: true }, { titulo: 'Faturamento', num: true }, { titulo: 'Participação', num: true }],
            c.map((x) => [x.categoria, fmtInt(x.quantidade), fmtMoeda(x.faturamento), fmtPct(x.participacao)]));
    }

    function renderTipos(v) {
        const cores = [COR.serie1, COR.serie2];
        const alvo = $('r-tipos');
        if (!v.totalPedidos) {
            alvo.innerHTML = '<p class="text-secondary small mb-0">Nenhuma venda no período selecionado.</p>';
            return;
        }
        const segmentos = v.tipos.map((t, i) => t.pedidos
            ? '<span style="flex:' + t.pedidos + ' 1 0;background:' + cores[i] + '" title="' + esc(t.descricao + ': ' + fmtPct(t.participacao)) + '"></span>' : '').join('');
        const legenda = v.tipos.map((t, i) =>
            '<li><span class="db-chave" style="background:' + cores[i] + '"></span><span>' + esc(t.descricao) + '</span>'
            + '<span class="db-legenda-valor">' + fmtInt(t.pedidos) + ' pedidos · ' + fmtPct(t.participacao) + ' · ticket ' + fmtMoeda(t.ticketMedio) + '</span></li>').join('');
        alvo.innerHTML = '<div class="db-segmentos" role="img" aria-label="'
            + esc(v.tipos.map((t) => t.descricao + ' ' + fmtPct(t.participacao)).join(', ')) + '">' + segmentos + '</div>'
            + '<ul class="db-legenda">' + legenda + '</ul>';
    }

    function renderRecentes(lista) {
        const corpo = $('r-recentes');
        if (!lista.length) {
            corpo.innerHTML = '<tr><td colspan="7" class="text-center text-secondary py-4"><i class="bi bi-inbox d-block fs-4 mb-1" aria-hidden="true"></i>Nenhum pedido no período selecionado.</td></tr>';
            return;
        }
        corpo.innerHTML = lista.map((p) => {
            const classe = STATUS_CLASSE[p.status] || 'bg-light text-dark';
            const tempo = EM_ANDAMENTO.includes(p.status) && p.minutosDesdeEntrada !== null
                ? '<div class="db-tempo">há ' + esc(fmtMinutos(p.minutosDesdeEntrada)) + '</div>' : '';
            return '<tr>'
                + '<td><a href="/pedidos/' + encodeURIComponent(p.id) + '">#' + esc(p.codigo) + '</a></td>'
                + '<td>' + esc(p.clienteNome || '—') + '</td>'
                + '<td>' + (p.tipoEntrega === 'ENTREGA' ? 'Entrega' : p.tipoEntrega === 'RETIRADA' ? 'Retirada' : '—') + '</td>'
                + '<td class="text-end">' + fmtInt(p.quantidadeItens) + '</td>'
                + '<td class="text-end">' + fmtMoeda(p.valorTotal) + '</td>'
                + '<td><span class="badge ' + classe + '">' + esc(p.statusDescricao) + '</span></td>'
                + '<td>' + esc(fmtDataHora(p.dataHoraEntrada)) + tempo + '</td>'
                + '</tr>';
        }).join('');
    }

    // =====================================================================
    // Filtros (RF05)
    // =====================================================================

    function aplicarPreset(valor) {
        const hoje = new Date();
        let inicio = new Date(hoje);
        if (valor === 'hoje') {
            // mesmo dia
        } else if (valor === 'mes') {
            inicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
        } else if (valor !== 'personalizado') {
            inicio.setDate(hoje.getDate() - (Number(valor) - 1));
        } else {
            return;
        }
        $('f-inicio').value = isoLocal(inicio);
        $('f-fim').value = isoLocal(hoje);
    }

    async function carregarOpcoes() {
        try {
            const o = await obter('/filtros', {}, undefined);
            estado.opcoes = o;
            estado.produtos = o.produtos;
            $('f-status').innerHTML = o.status.map((s) =>
                '<div class="form-check"><input class="form-check-input" type="checkbox" value="' + esc(s.valor) + '" id="st-' + esc(s.valor) + '">'
                + '<label class="form-check-label" for="st-' + esc(s.valor) + '">' + esc(s.descricao) + '</label></div>').join('');
            $('f-tipo').insertAdjacentHTML('beforeend', o.tiposEntrega.map((t) => '<option value="' + esc(t.valor) + '">' + esc(t.descricao) + '</option>').join(''));
            $('f-categoria').insertAdjacentHTML('beforeend', o.categorias.map((c) => '<option value="' + esc(c) + '">' + esc(c) + '</option>').join(''));
            preencherProdutos();
            return true;
        } catch (e) {
            if (e.status === 401 || e.status === 403) {
                tratarErro(e, 'resumo');
                return false;
            }
            alertaGeral('warning', 'Não foi possível carregar as opções de filtro (' + esc(e.message) + '). Os indicadores continuam disponíveis.');
            return true;
        }
    }

    function preencherProdutos() {
        const categoria = $('f-categoria').value.toLowerCase();
        const atual = $('f-produto').value;
        const lista = estado.produtos.filter((p) => !categoria || (p.categoria || '').toLowerCase() === categoria);
        $('f-produto').innerHTML = '<option value="">Todos</option>' + lista.map((p) =>
            '<option value="' + esc(p.id) + '">' + esc(p.nome) + (p.ativo ? '' : ' (inativo)') + '</option>').join('');
        if (lista.some((p) => p.id === atual)) $('f-produto').value = atual;
    }

    function atualizarRotuloStatus() {
        const sel = statusSelecionados();
        const botao = $('f-status-botao');
        if (!sel.length) botao.textContent = 'Todos';
        else if (sel.length === 1) botao.textContent = document.querySelector('label[for="st-' + sel[0] + '"]').textContent;
        else botao.textContent = sel.length + ' selecionados';
    }

    // Filtros na URL: recarregar a página ou compartilhar o link mantém o recorte
    function salvarFiltrosNaUrl() {
        const p = parametrosFiltro();
        if ($('f-preset').value !== 'personalizado') {
            p.delete('dataInicio');
            p.delete('dataFim');
            p.set('periodo', $('f-preset').value);
        }
        history.replaceState(null, '', location.pathname + (p.toString() ? '?' + p.toString() : ''));
    }

    function lerFiltrosDaUrl() {
        const p = new URLSearchParams(location.search);
        const periodo = p.get('periodo');
        if (p.get('dataInicio') || p.get('dataFim')) {
            $('f-preset').value = 'personalizado';
            $('f-inicio').value = p.get('dataInicio') || '';
            $('f-fim').value = p.get('dataFim') || '';
        } else {
            $('f-preset').value = periodo && $('f-preset').querySelector('option[value="' + CSS.escape(periodo) + '"]') ? periodo : '30';
            aplicarPreset($('f-preset').value);
        }
        p.getAll('status').forEach((s) => {
            const cb = document.getElementById('st-' + s);
            if (cb) cb.checked = true;
        });
        if (p.get('tipoEntrega')) $('f-tipo').value = p.get('tipoEntrega');
        if (p.get('categoria')) {
            $('f-categoria').value = p.get('categoria');
            preencherProdutos();
        }
        if (p.get('produtoId')) $('f-produto').value = p.get('produtoId');
        atualizarRotuloStatus();
    }

    function limparFiltros() {
        $('f-preset').value = '30';
        aplicarPreset('30');
        document.querySelectorAll('#f-status input').forEach((i) => { i.checked = false; });
        $('f-tipo').value = '';
        $('f-categoria').value = '';
        preencherProdutos();
        $('f-produto').value = '';
        atualizarRotuloStatus();
    }

    function alternar(grupoId, aoMudar) {
        $(grupoId).addEventListener('click', (e) => {
            const botao = e.target.closest('button[data-valor]');
            if (!botao) return;
            $(grupoId).querySelectorAll('button').forEach((b) => {
                const ativo = b === botao;
                b.classList.toggle('active', ativo);
                b.setAttribute('aria-pressed', String(ativo));
            });
            aoMudar(botao.dataset.valor);
        });
    }

    // =====================================================================
    // Inicialização
    // =====================================================================

    async function iniciar() {
        if (!window.Chart || !window.api) {
            alertaGeral('danger', 'Não foi possível carregar os componentes do dashboard. Recarregue a página.');
            return;
        }
        configurarChart();

        try {
            const salvo = localStorage.getItem(CHAVE_AUTO);
            if (salvo !== null) $('db-auto').checked = salvo === '1';
        } catch (e) { /* armazenamento indisponível: mantém o padrão */ }

        const ok = await carregarOpcoes();
        if (!ok) return;
        lerFiltrosDaUrl();

        $('f-preset').addEventListener('change', (e) => aplicarPreset(e.target.value));
        ['f-inicio', 'f-fim'].forEach((id) => $(id).addEventListener('change', () => { $('f-preset').value = 'personalizado'; }));
        $('f-categoria').addEventListener('change', preencherProdutos);
        $('f-status').addEventListener('change', atualizarRotuloStatus);
        $('db-filtros').addEventListener('submit', (e) => {
            e.preventDefault();
            salvarFiltrosNaUrl();
            atualizar();
        });
        $('f-limpar').addEventListener('click', () => {
            limparFiltros();
            salvarFiltrosNaUrl();
            atualizar();
        });
        $('db-atualizar').addEventListener('click', () => atualizar());
        $('db-auto').addEventListener('change', (e) => {
            try { localStorage.setItem(CHAVE_AUTO, e.target.checked ? '1' : '0'); } catch (err) { /* ignora */ }
            iniciarAuto();
        });

        alternar('t-metrica', (v) => { estado.metrica = v; if (estado.dados.vendas) renderVendas(estado.dados.vendas); });
        alternar('t-agrupamento', (v) => { estado.agrupamento = v; atualizar(['vendas']); });
        alternar('t-criterio', (v) => { estado.criterio = v; atualizar(['ranking']); });

        document.addEventListener('click', (e) => {
            const tentar = e.target.closest('[data-tentar]');
            if (tentar) {
                const nome = tentar.getAttribute('data-tentar');
                atualizar(nome && SECOES[nome] ? [nome] : undefined);
                return;
            }
            const verTabela = e.target.closest('.db-ver-tabela');
            if (verTabela) {
                const cartao = verTabela.closest('.db-card');
                const mostrandoTabela = cartao.querySelector('.db-tabela').classList.toggle('d-none') === false;
                cartao.querySelector('.db-area').classList.toggle('d-none', mostrandoTabela);
                verTabela.textContent = mostrandoTabela ? 'Ver gráfico' : 'Ver tabela';
            }
        });

        await atualizar();
        iniciarAuto();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})();
