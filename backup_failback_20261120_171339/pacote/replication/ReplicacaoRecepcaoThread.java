package pacote.replication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import pacote.ConfiguracaoTLS;
import pacote.Util;
import pacote.database.MensagemArmazenada;
import pacote.database.MensagemDAO;
import protocolo.ChatMessage;
import protocolo.JsonProtocol;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Recebe replicacoes TLS do middleware principal e coordena o failback. */
public final class ReplicacaoRecepcaoThread implements Runnable {

    private static final int TAMANHO_MAXIMO_JSON = 1_048_576;
    private static final int LIMITE_MAXIMO_LOTE = 100;
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String token;
    private final int porta;

    private volatile boolean paradaManual;
    private volatile SSLServerSocket receptor;
    private volatile ExecutorService atendimentos;

    private final Set<Socket> clientesAtivos = ConcurrentHashMap.newKeySet();
    private final Path marcadorAtivo = Util.DiretorioDados.resolve("middleware-reserva-ativo.flag");

    private volatile long inicioMs;
    private volatile long ultimoHeartbeatPrincipalMs;
    private volatile boolean promovido;
    private volatile boolean promocaoEmAndamento;
    private volatile boolean reintegracaoEmAndamento;
    private volatile long inicioReintegracaoMs;
    private volatile ScheduledExecutorService monitorPromocao;

    public ReplicacaoRecepcaoThread() throws IOException {
        this.token = ReplicacaoConfig.tokenReceptor();
        this.porta = ReplicacaoConfig.portaReceptor();
    }

    /** Abre a porta sincronamente para que falhas de inicializacao sejam detectadas. */
    public synchronized void iniciar() throws IOException {
        if (receptor != null && !receptor.isClosed()) return;

        final SSLContext contexto;
        try {
            contexto = ConfiguracaoTLS.criarContextoServidor();
        } catch (GeneralSecurityException erro) {
            throw new IOException("Nao foi possivel carregar TLS para a replicacao.", erro);
        }

        SSLServerSocket novoServidor = null;
        ExecutorService novoExecutor = null;
        try {
            novoServidor = (SSLServerSocket) contexto.getServerSocketFactory().createServerSocket();
            novoServidor.setReuseAddress(true);
            novoServidor.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            novoServidor.setNeedClientAuth(false);
            novoServidor.bind(new InetSocketAddress("0.0.0.0", porta), 50);

            novoExecutor = Executors.newVirtualThreadPerTaskExecutor();
            receptor = novoServidor;
            atendimentos = novoExecutor;
            paradaManual = false;
            inicioMs = System.currentTimeMillis();
            ultimoHeartbeatPrincipalMs = inicioMs;
            promovido = Files.isRegularFile(marcadorAtivo);
            promocaoEmAndamento = false;
            reintegracaoEmAndamento = false;
            inicioReintegracaoMs = 0;

            Thread.ofVirtual().name("middleware-replicacao-accept").start(this);

            System.out.println("Receptor de replicacao TLS ativo na porta " + porta);
            iniciarMonitorPromocao();
            if (promovido) {
                System.out.println("Marcador persistente detectado: Middleware B ja era o principal.");
                Util.iniciarServicosDeAtendimento();
            }
        } catch (IOException | RuntimeException erro) {
            if (novoExecutor != null) novoExecutor.shutdownNow();
            if (novoServidor != null) {
                try {
                    novoServidor.close();
                } catch (IOException fechamento) {
                    erro.addSuppressed(fechamento);
                }
            }
            receptor = null;
            atendimentos = null;
            throw new IOException("Nao foi possivel iniciar o receptor de replicacao na porta " + porta + ".", erro);
        }
    }

    @Override
    public void run() {
        try {
            while (!paradaManual) {
                SSLServerSocket servidor = receptor;
                if (servidor == null || servidor.isClosed()) break;

                SSLSocket cliente = (SSLSocket) servidor.accept();
                cliente.setSoTimeout(30000);
                clientesAtivos.add(cliente);

                ExecutorService executor = atendimentos;
                try {
                    if (executor == null || executor.isShutdown()) {
                        clientesAtivos.remove(cliente);
                        fecharSocket(cliente);
                        continue;
                    }
                    executor.execute(() -> atenderCliente(cliente));
                } catch (RejectedExecutionException erro) {
                    clientesAtivos.remove(cliente);
                    fecharSocket(cliente);
                }
            }
        } catch (SocketException erro) {
            if (!paradaManual) registrarErro("ReplicacaoRecepcaoThread.run", erro);
        } catch (IOException erro) {
            if (!paradaManual) registrarErro("ReplicacaoRecepcaoThread.run", erro);
        } finally {
            fecharClientes();
            System.out.println("Receptor de replicacao TLS encerrado.");
        }
    }

    private void atenderCliente(SSLSocket socket) {
        String ip = socket.getInetAddress().getHostAddress();
        try (socket) {
            socket.startHandshake();
            try (BufferedReader input = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter output = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                String linha = input.readLine();
                if (linha == null || linha.isBlank()) {
                    enviarErro(output, null, "JSON_VAZIO", "A requisicao de replicacao esta vazia.");
                    return;
                }
                if (linha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_JSON) {
                    enviarErro(output, null, "JSON_MUITO_GRANDE", "A requisicao ultrapassa 1 MiB.");
                    return;
                }

                final ReplicationEnvelope envelope;
                try {
                    envelope = JSON.readValue(linha, ReplicationEnvelope.class);
                } catch (JsonProcessingException erro) {
                    enviarErro(output, null, "JSON_INVALIDO", "Nao foi possivel interpretar o envelope de replicacao.");
                    registrarErro("ReplicacaoRecepcaoThread - JSON invalido de " + ip, erro);
                    return;
                }

                ChatMessage resposta = processar(envelope, ip);
                String respostaLinha = JsonProtocol.serializeLine(resposta);
                if (respostaLinha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_JSON) {
                    resposta = criarErro(correlationId(envelope), "LOTE_MUITO_GRANDE",
                            "O lote de exportacao excede 1 MiB. Tente novamente com menos registros.");
                    respostaLinha = JsonProtocol.serializeLine(resposta);
                }
                output.write(respostaLinha);
                output.flush();
            }
        } catch (Exception erro) {
            if (!paradaManual) registrarErro("ReplicacaoRecepcaoThread.atenderCliente | IP: " + ip, erro);
        } finally {
            clientesAtivos.remove(socket);
        }
    }

    private ChatMessage processar(ReplicationEnvelope envelope, String ip) {
        String correlationId = correlationId(envelope);
        if (envelope == null || envelope.protocolVersion() != ChatMessage.CURRENT_VERSION
                || envelope.operation() == null) {
            return criarErro(correlationId, "ENVELOPE_INVALIDO", "Envelope de replicacao invalido.");
        }
        if (!tokenValido(envelope.token())) {
            return criarErro(correlationId, "REPLICACAO_NAO_AUTORIZADA", "Token de replicacao invalido.");
        }

        return switch (envelope.operation()) {
            case "HEARTBEAT" -> processarHeartbeat(envelope, correlationId);
            case "REPLICATE_MESSAGE" -> processarMensagem(envelope, correlationId, ip);
            case "PREPARE_FAILBACK" -> prepararFailback(envelope, correlationId);
            case "SYNC_EXPORT" -> exportarLote(envelope, correlationId);
            case "SYNC_IMPORT_BATCH" -> importarLote(envelope, correlationId);
            case "SYNC_STATUS" -> consultarResumo(envelope, correlationId);
            case "COMPLETE_FAILBACK" -> concluirFailback(envelope, correlationId);
            case "CANCEL_FAILBACK" -> cancelarFailback(envelope, correlationId);
            default -> criarErro(correlationId, "OPERACAO_INVALIDA", "Operacao de replicacao nao suportada.");
        };
    }

    private ChatMessage processarHeartbeat(ReplicationEnvelope envelope, String correlationId) {
        if (envelope.message() == null || envelope.message().type() != ChatMessage.Type.PING) {
            return criarErro(correlationId, "HEARTBEAT_INVALIDO", "Heartbeat invalido.");
        }
        ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
        String role = (promovido || promocaoEmAndamento) ? "ACTIVE" : "STANDBY";
        return criarAck(correlationId, "Reserva " + ("ACTIVE".equals(role) ? "ativo." : "em standby."),
                Map.of("status", "SUCCESS", "role", role));
    }

    private synchronized ChatMessage processarMensagem(
            ReplicationEnvelope envelope, String correlationId, String ip) {
        if (envelope.message() == null || envelope.message().type() != ChatMessage.Type.SEND_MESSAGE) {
            return criarErro(correlationId, "TIPO_INVALIDO", "Somente mensagens SEND_MESSAGE podem ser replicadas.");
        }
        if (promovido || promocaoEmAndamento || reintegracaoEmAndamento) {
            return criarErro(correlationId, "RESERVA_ATIVO",
                    "Este middleware ja assumiu ou esta transferindo o atendimento e nao aceita replicacoes comuns.");
        }
        ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
        ChatMessage mensagem = envelope.message();
        if (mensagem.sender().isBlank() || mensagem.sender().length() > 20) {
            return criarErro(correlationId, "REMETENTE_INVALIDO", "Remetente invalido na replicacao.");
        }
        if (mensagem.content().isBlank() && mensagem.sticker().isBlank()) {
            return criarErro(correlationId, "CONTEUDO_VAZIO", "A mensagem replicada nao possui conteudo.");
        }
        if (envelope.renderedHtml() == null || envelope.renderedHtml().isBlank()) {
            return criarErro(correlationId, "HTML_AUSENTE", "A representacao HTML da mensagem nao foi enviada.");
        }

        try {
            boolean inserida = Util.registrarMensagemReplicada(mensagem, envelope.renderedHtml());
            System.out.println((inserida ? "Mensagem replicada" : "Mensagem ja existente no Banco B")
                    + " | ID: " + mensagem.id() + " | remetente: " + mensagem.sender()
                    + " | IP principal: " + ip);
            return criarAck(correlationId,
                    inserida ? "Mensagem persistida no Banco B." : "Mensagem ja estava persistida no Banco B.",
                    Map.of("status", "SUCCESS", "duplicate", !inserida, "role", "STANDBY"));
        } catch (IOException erro) {
            registrarErro("ReplicacaoRecepcaoThread - persistencia Banco B", erro);
            return criarErro(correlationId, "ERRO_PERSISTENCIA", "Nao foi possivel persistir a mensagem no Banco B.");
        }
    }

    private synchronized ChatMessage prepararFailback(ReplicationEnvelope envelope, String correlationId) {
        if (!solicitacaoPingValida(envelope)) {
            return criarErro(correlationId, "FAILBACK_INVALIDO", "Solicitacao de failback invalida.");
        }
        if (promocaoEmAndamento) {
            return criarErro(correlationId, "PROMOCAO_EM_ANDAMENTO", "A promocao da reserva ainda esta em andamento.");
        }
        if (!promovido) {
            ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
            return criarAck(correlationId, "A reserva ja esta em standby.",
                    Map.of("status", "SUCCESS", "role", "STANDBY", "state", "STANDBY"));
        }
        if (!reintegracaoEmAndamento) {
            reintegracaoEmAndamento = true;
            inicioReintegracaoMs = System.currentTimeMillis();
            try {
                // rebaixarParaReserva e sincronizado com registrarMensagem: ao
                // retornar, as gravacoes em andamento terminaram e novas sao bloqueadas.
                Util.rebaixarParaReserva();
                System.out.println("[FAILBACK] Middleware B suspendeu os listeners e aguarda sincronizacao com A.");
            } catch (RuntimeException erro) {
                reintegracaoEmAndamento = false;
                inicioReintegracaoMs = 0;
                return criarErro(correlationId, "QUIESCE_FALHOU", "Nao foi possivel suspender os servicos do Middleware B.");
            }
        }
        ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
        return criarAck(correlationId, "Reserva suspensa para sincronizacao.",
                Map.of("status", "SUCCESS", "role", "ACTIVE", "state", "QUIESCED"));
    }

    private synchronized ChatMessage exportarLote(ReplicationEnvelope envelope, String correlationId) {
        if (!solicitacaoPingValida(envelope)) {
            return criarErro(correlationId, "SYNC_INVALIDO", "Solicitacao de exportacao invalida.");
        }
        if (!podeSincronizar()) {
            return criarErro(correlationId, "RESERVA_ATIVO", "O banco da reserva so pode ser exportado em STANDBY ou durante failback quiescido.");
        }
        int offset = envelope.offset() == null ? 0 : envelope.offset();
        int limite = envelope.limit() == null ? 25 : envelope.limit();
        if (offset < 0 || limite < 1 || limite > LIMITE_MAXIMO_LOTE) {
            return criarErro(correlationId, "PAGINACAO_INVALIDA", "Offset ou limite de exportacao invalido.");
        }

        try {
            ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
            List<MensagemArmazenada> lote = Util.listarMensagensParaReplicacao(offset, limite);
            long total = Util.contarMensagensPersistidas();
            String conteudoJson = JSON.writeValueAsString(lote);
            ChatMessage resposta = criarAck(correlationId, conteudoJson,
                    Map.of("status", "SUCCESS", "role", papelAtual(), "offset", offset,
                            "count", lote.size(), "total", total));
            if (JsonProtocol.serializeLine(resposta).getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_JSON) {
                return criarErro(correlationId, "LOTE_MUITO_GRANDE", "O lote excede 1 MiB. Reduza a quantidade de registros por pagina.");
            }
            return resposta;
        } catch (Exception erro) {
            registrarErro("ReplicacaoRecepcaoThread.exportarLote", erro);
            return criarErro(correlationId, "ERRO_EXPORTACAO", "Nao foi possivel exportar o lote de mensagens.");
        }
    }

    private synchronized ChatMessage importarLote(ReplicationEnvelope envelope, String correlationId) {
        if (!solicitacaoPingValida(envelope)) {
            return criarErro(correlationId, "SYNC_INVALIDO", "Solicitacao de importacao invalida.");
        }
        if (!podeSincronizar()) {
            return criarErro(correlationId, "RESERVA_ATIVO", "A reserva precisa estar em STANDBY ou quiescida para receber sincronizacao.");
        }
        List<MensagemArmazenada> lote = envelope.messages();
        if (lote == null || lote.isEmpty() || lote.size() > LIMITE_MAXIMO_LOTE) {
            return criarErro(correlationId, "LOTE_INVALIDO", "O lote de importacao esta vazio ou excede o limite permitido.");
        }

        try {
            ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
            int inseridas = 0;
            for (MensagemArmazenada registro : lote) {
                if (registro == null || registro.mensagem() == null || registro.renderedHtml() == null
                        || registro.renderedHtml().isBlank()) {
                    return criarErro(correlationId, "REGISTRO_INVALIDO", "O lote contem uma mensagem incompleta.");
                }
                inseridas += Util.registrarMensagemReplicada(registro.mensagem(), registro.renderedHtml()) ? 1 : 0;
            }
            return criarAck(correlationId, "Lote persistido com sucesso.",
                    Map.of("status", "SUCCESS", "role", papelAtual(), "received", lote.size(), "inserted", inseridas));
        } catch (IOException erro) {
            registrarErro("ReplicacaoRecepcaoThread.importarLote", erro);
            return criarErro(correlationId, "ERRO_IMPORTACAO", "Nao foi possivel persistir todo o lote no Banco B.");
        }
    }

    private synchronized ChatMessage consultarResumo(ReplicationEnvelope envelope, String correlationId) {
        if (!solicitacaoPingValida(envelope)) {
            return criarErro(correlationId, "SYNC_INVALIDO", "Solicitacao de resumo invalida.");
        }
        if (!podeSincronizar()) {
            return criarErro(correlationId, "RESERVA_ATIVO", "O resumo so pode ser consultado em STANDBY ou durante failback quiescido.");
        }
        try {
            ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
            MensagemDAO.ResumoMensagens resumo = Util.obterResumoMensagens();
            return criarAck(correlationId, "Resumo do banco da reserva.",
                    Map.of("status", "SUCCESS", "role", papelAtual(),
                            "count", resumo.quantidade(), "digest", resumo.sha256()));
        } catch (IOException erro) {
            registrarErro("ReplicacaoRecepcaoThread.consultarResumo", erro);
            return criarErro(correlationId, "ERRO_RESUMO", "Nao foi possivel calcular o resumo do Banco B.");
        }
    }

    private synchronized ChatMessage concluirFailback(ReplicationEnvelope envelope, String correlationId) {
        if (!solicitacaoPingValida(envelope)) {
            return criarErro(correlationId, "FAILBACK_INVALIDO", "Confirmacao de failback invalida.");
        }
        if (!promovido && !reintegracaoEmAndamento) {
            return criarAck(correlationId, "Middleware B ja esta em STANDBY.",
                    Map.of("status", "SUCCESS", "role", "STANDBY", "state", "STANDBY"));
        }
        if (!promovido || !reintegracaoEmAndamento) {
            return criarErro(correlationId, "FAILBACK_NAO_PREPARADO", "O Middleware B nao esta em estado quiescido para concluir o failback.");
        }

        try {
            MensagemDAO.ResumoMensagens atual = Util.obterResumoMensagens();
            long countEsperado = numeroMetadata(envelope.message(), "count");
            String digestEsperado = textoMetadata(envelope.message(), "digest");
            if (atual.quantidade() != countEsperado || !atual.sha256().equals(digestEsperado)) {
                return criarErro(correlationId, "BANCOS_DIVERGENTES", "O Banco B nao corresponde ao resumo confirmado pelo Middleware A.");
            }

            // O marcador so e removido depois da verificacao integral dos bancos.
            Files.deleteIfExists(marcadorAtivo);
            promovido = false;
            reintegracaoEmAndamento = false;
            inicioReintegracaoMs = 0;
            ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
            System.out.println("[FAILBACK] Banco B sincronizado e devolvido ao estado STANDBY.");
            return criarAck(correlationId, "Failback concluido. Middleware B em STANDBY.",
                    Map.of("status", "SUCCESS", "role", "STANDBY", "state", "STANDBY"));
        } catch (Exception erro) {
            registrarErro("ReplicacaoRecepcaoThread.concluirFailback", erro);
            return criarErro(correlationId, "ERRO_FAILBACK", "Nao foi possivel concluir a transferencia de lideranca.");
        }
    }

    private synchronized ChatMessage cancelarFailback(ReplicationEnvelope envelope, String correlationId) {
        if (!solicitacaoPingValida(envelope)) {
            return criarErro(correlationId, "FAILBACK_INVALIDO", "Cancelamento de failback invalido.");
        }
        if (!reintegracaoEmAndamento) {
            return criarAck(correlationId, "Nenhuma reintegracao estava em andamento.",
                    Map.of("status", "SUCCESS", "role", papelAtual()));
        }
        try {
            Util.iniciarServicosDeAtendimento();
            reintegracaoEmAndamento = false;
            inicioReintegracaoMs = 0;
            promovido = true;
            ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
            System.out.println("[FAILBACK] Reintegracao cancelada. Middleware B voltou a atender.");
            return criarAck(correlationId, "Failback cancelado; Middleware B continua ACTIVE.",
                    Map.of("status", "SUCCESS", "role", "ACTIVE"));
        } catch (Exception erro) {
            registrarErro("ReplicacaoRecepcaoThread.cancelarFailback", erro);
            return criarErro(correlationId, "ERRO_REATIVACAO", "Nao foi possivel reativar o Middleware B.");
        }
    }

    private void iniciarMonitorPromocao() throws IOException {
        long intervalo = ReplicacaoConfig.intervaloHeartbeatMs();
        monitorPromocao = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "middleware-reserva-failover-monitor");
            t.setDaemon(true);
            return t;
        });
        monitorPromocao.scheduleWithFixedDelay(this::verificarPromocao, intervalo, 1000, TimeUnit.MILLISECONDS);
    }

    private void verificarPromocao() {
        if (paradaManual) return;
        try {
            if (reintegracaoEmAndamento) {
                long timeoutReintegracao = ReplicacaoConfig.timeoutReintegracaoMs();
                if (System.currentTimeMillis() - inicioReintegracaoMs >= timeoutReintegracao) {
                    abortarReintegracaoPorTimeout();
                }
                return;
            }
            if (promovido || promocaoEmAndamento) return;

            long grace = ReplicacaoConfig.graceInicialMs();
            long timeout = ReplicacaoConfig.timeoutPromocaoMs();
            long agora = System.currentTimeMillis();
            if (agora - inicioMs < grace) return;
            if (agora - ultimoHeartbeatPrincipalMs < timeout) return;
            promover();
        } catch (Exception erro) {
            registrarErro("ReplicacaoRecepcaoThread.verificarPromocao", erro);
        }
    }

    private synchronized void abortarReintegracaoPorTimeout() {
        if (!reintegracaoEmAndamento || !promovido || paradaManual) return;
        try {
            Util.iniciarServicosDeAtendimento();
            reintegracaoEmAndamento = false;
            inicioReintegracaoMs = 0;
            ultimoHeartbeatPrincipalMs = System.currentTimeMillis();
            System.err.println("[FAILBACK] Tempo de reintegracao expirou. Middleware B retomou o atendimento.");
        } catch (Exception erro) {
            // Mantem o estado quiescido e tenta reativar novamente no proximo ciclo.
            inicioReintegracaoMs = System.currentTimeMillis();
            registrarErro("ReplicacaoRecepcaoThread.abortarReintegracaoPorTimeout", erro);
        }
    }

    private synchronized void promover() throws IOException {
        if (promovido || promocaoEmAndamento || reintegracaoEmAndamento || paradaManual) return;
        promocaoEmAndamento = true;
        System.out.println("[FAILOVER] Heartbeat do principal expirou. Promovendo Middleware B.");
        try {
            // Persiste a decisao antes de abrir portas de clientes: se o processo
            // cair durante a promocao, o proximo boot nao iniciara como STANDBY.
            Files.createDirectories(marcadorAtivo.getParent());
            Files.writeString(marcadorAtivo, Instant.now().toString(), StandardCharsets.UTF_8);
            Util.iniciarServicosDeAtendimento();
            promovido = true;
            System.out.println("[FAILOVER] Middleware B assumiu o atendimento dos clientes.");
        } catch (Exception erro) {
            promovido = false;
            try {
                Files.deleteIfExists(marcadorAtivo);
            } catch (IOException limpeza) {
                erro.addSuppressed(limpeza);
            }
            Util.rebaixarParaReserva();
            if (erro instanceof IOException io) throw io;
            throw new IOException("Nao foi possivel iniciar o atendimento no Middleware B.", erro);
        } finally {
            promocaoEmAndamento = false;
        }
    }

    private boolean podeSincronizar() {
        return !promovido || reintegracaoEmAndamento;
    }

    private String papelAtual() {
        return (promovido || promocaoEmAndamento) ? "ACTIVE" : "STANDBY";
    }

    private boolean solicitacaoPingValida(ReplicationEnvelope envelope) {
        return envelope != null && envelope.message() != null
                && envelope.message().type() == ChatMessage.Type.PING;
    }

    private long numeroMetadata(ChatMessage mensagem, String campo) throws IOException {
        Object valor = mensagem.metadata().get(campo);
        if (valor instanceof Number numero) return numero.longValue();
        if (valor instanceof String texto) {
            try {
                return Long.parseLong(texto);
            } catch (NumberFormatException erro) {
                throw new IOException("Campo numerico invalido: " + campo, erro);
            }
        }
        throw new IOException("Campo numerico ausente: " + campo);
    }

    private String textoMetadata(ChatMessage mensagem, String campo) throws IOException {
        Object valor = mensagem.metadata().get(campo);
        if (valor instanceof String texto && !texto.isBlank()) return texto;
        throw new IOException("Campo textual ausente: " + campo);
    }

    private boolean tokenValido(String recebido) {
        if (recebido == null) return false;
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ChatMessage criarAck(String correlationId, String content, Map<String, Object> metadata) {
        return new ChatMessage(
                ChatMessage.CURRENT_VERSION,
                null,
                correlationId,
                ChatMessage.Type.ACK,
                "middleware-reserva",
                "middleware-principal",
                content,
                "", "", "", "",
                Instant.now().toString(),
                metadata
        );
    }

    private ChatMessage criarErro(String correlationId, String codigo, String descricao) {
        return new ChatMessage(
                ChatMessage.CURRENT_VERSION,
                null,
                correlationId,
                ChatMessage.Type.ERROR,
                "middleware-reserva",
                "",
                descricao,
                "", "", "", "",
                Instant.now().toString(),
                Map.of("code", codigo)
        );
    }

    private String correlationId(ReplicationEnvelope envelope) {
        return envelope == null || envelope.message() == null ? null : envelope.message().id();
    }

    private void enviarErro(BufferedWriter output, String correlationId, String codigo, String descricao)
            throws IOException {
        output.write(JsonProtocol.serializeLine(criarErro(correlationId, codigo, descricao)));
        output.flush();
    }

    private void registrarErro(String origem, Throwable erro) {
        Util.registrarErroServidor("internal", "REPLICACAO", "N/A", origem, erro);
    }

    public synchronized void fecharServidor() {
        paradaManual = true;
        ScheduledExecutorService monitor = monitorPromocao;
        monitorPromocao = null;
        if (monitor != null) monitor.shutdownNow();

        SSLServerSocket servidor = receptor;
        receptor = null;
        if (servidor != null && !servidor.isClosed()) {
            try {
                servidor.close();
            } catch (IOException erro) {
                registrarErro("ReplicacaoRecepcaoThread.fecharServidor", erro);
            }
        }
        fecharClientes();
        ExecutorService executor = atendimentos;
        atendimentos = null;
        if (executor != null) executor.shutdownNow();
    }

    private void fecharClientes() {
        for (Socket cliente : clientesAtivos) fecharSocket(cliente);
        clientesAtivos.clear();
    }

    private void fecharSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }
}
