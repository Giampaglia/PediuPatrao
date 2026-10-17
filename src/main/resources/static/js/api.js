// Chamadas às APIs com o token CSRF (lido das <meta> do layout).
// Uso (também pelo console do navegador, para demonstrar bloqueios):
//   await api('/api/pedidos/ID/cancelar', { method: 'POST', body: { justificativa: 'Cliente desistiu' } })
//   -> { status: 403, ok: false, dados: {...} }
(function () {
    function cabecalhosCsrf() {
        const token = document.querySelector('meta[name="_csrf"]');
        const header = document.querySelector('meta[name="_csrf_header"]');
        return token && header ? { [header.content]: token.content } : {};
    }

    window.api = async function (url, opcoes = {}) {
        const headers = Object.assign({ 'Accept': 'application/json' }, cabecalhosCsrf(), opcoes.headers || {});
        let body = opcoes.body;
        if (body !== undefined && typeof body !== 'string') {
            body = JSON.stringify(body);
        }
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        const resposta = await fetch(url, Object.assign({}, opcoes, { headers, body, credentials: 'same-origin' }));
        let dados = null;
        const texto = await resposta.text();
        if (texto) {
            try { dados = JSON.parse(texto); } catch (e) { dados = texto; }
        }
        return { status: resposta.status, ok: resposta.ok, dados };
    };

    // Evita injeção de HTML ao montar tabelas com dados vindos da API
    window.escapeHtml = function (valor) {
        if (valor === null || valor === undefined) return '';
        return String(valor)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };
})();
