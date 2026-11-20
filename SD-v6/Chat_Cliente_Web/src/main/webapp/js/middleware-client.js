/* Cliente HTTP do navegador com alternancia automatica entre middleware principal e reserva. */
(function (global) {
    "use strict";

    global.createMiddlewareClient = function (configElement) {
        if (!configElement) {
            throw new Error("Configuracao de middleware nao encontrada na pagina.");
        }

        const primary = (configElement.dataset.primary || "").replace(/\/+$/, "");
        const backup = (configElement.dataset.backup || "").replace(/\/+$/, "");
        const hosts = Array.from(new Set([primary, backup].filter(Boolean)));
        if (!hosts.length) {
            throw new Error("Nenhum endereco de middleware foi configurado.");
        }

        let active = primary || hosts[0];

        async function fetchWithFailover(path, options) {
            const endpoint = String(path || "");
            if (!endpoint.startsWith("/")) {
                throw new Error("O caminho da API deve comecar com '/'.");
            }

            const maxWaitMs = Math.max(5000, Math.min(60000,
                Number(configElement.dataset.failoverWait || 25000)));
            const deadline = Date.now() + maxWaitMs;
            let lastError = null;

            do {
                const ordered = Array.from(new Set([active, ...hosts]));
                for (const base of ordered) {
                    const controller = new AbortController();
                    const timeout = setTimeout(() => controller.abort(), 5000);
                    try {
                        const response = await fetch(base + endpoint, {
                            cache: "no-store",
                            ...(options || {}),
                            signal: controller.signal
                        });

                        if (response.status >= 500 || response.status === 408 || response.status === 425) {
                            lastError = new Error("Middleware " + base + " retornou HTTP " + response.status);
                            continue;
                        }

                        active = base;
                        return response;
                    } catch (error) {
                        lastError = error;
                    } finally {
                        clearTimeout(timeout);
                    }
                }

                if (Date.now() >= deadline) break;
                await new Promise(resolve => setTimeout(resolve, 900));
            } while (Date.now() < deadline);

            throw new Error("Nenhum middleware confirmou a requisicao durante o failover. "
                + (lastError ? lastError.message : ""));
        }

        return {
            fetch: fetchWithFailover,
            base: function () { return active; },
            primary: primary,
            backup: backup,
            hosts: hosts.slice()
        };
    };
})(window);
