package pacote.replication;

import pacote.Util;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mantem o lease do principal e o auto-fencia quando o reserva fica indisponivel. */
public final class ReplicacaoHeartbeatMonitor {

    private static volatile ReplicacaoHeartbeatMonitor instancia;

    private final ReplicacaoConfig config;
    private final long intervaloMs;
    private final long leaseMs;
    private final ScheduledExecutorService scheduler;
    private volatile long ultimoSucessoMs = System.currentTimeMillis();
    // true significa que o A nao esta autorizado a atender clientes.
    private final AtomicBoolean fenced = new AtomicBoolean(true);
    // So libera o retorno de A depois da sincronizacao inicial.
    private volatile boolean prontoParaAtender;
    private final AtomicBoolean recuperacaoEmAndamento = new AtomicBoolean(false);

    private ReplicacaoHeartbeatMonitor(ReplicacaoConfig config) throws IOException {
        this.config = config;
        this.intervaloMs = ReplicacaoConfig.intervaloHeartbeatMs();
        this.leaseMs = ReplicacaoConfig.leasePrincipalMs();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "middleware-primary-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static synchronized ReplicacaoHeartbeatMonitor iniciar(ReplicacaoConfig config) throws IOException {
        if (instancia != null) return instancia;
        ReplicacaoHeartbeatMonitor novo = new ReplicacaoHeartbeatMonitor(config);
        instancia = novo;
        novo.scheduler.scheduleWithFixedDelay(novo::verificar, novo.intervaloMs,
                novo.intervaloMs, TimeUnit.MILLISECONDS);
        return novo;
    }

    /** Autoriza o principal somente depois de a sincronizacao dos bancos terminar. */
    public synchronized void assumirAtendimentoInicial() throws IOException {
        // Libera a retomada automatica somente depois de os listeners abrirem.
        iniciarAtendimentoSeCercado();
        prontoParaAtender = true;
    }

    /** Mantem o processo sem listeners quando o reserva e o principal. */
    public void marcarStandby() {
        if (fenced.compareAndSet(false, true)) {
            Util.rebaixarParaReserva();
        }
    }

    private void verificar() {
        try {
            String estado = ReplicacaoClient.consultarEstadoReserva(config);
            ultimoSucessoMs = System.currentTimeMillis();

            if ("ACTIVE".equals(estado)) {
                if (fenced.compareAndSet(false, true)) {
                    System.err.println("[FAILOVER] O reserva assumiu o atendimento. O principal esta sendo colocado em standby.");
                    Util.rebaixarParaReserva();
                }
                return;
            }

            if (prontoParaAtender && fenced.get()) {
                iniciarSincronizacaoDeRecuperacao();
            }
        } catch (Exception erro) {
            long indisponivelMs = System.currentTimeMillis() - ultimoSucessoMs;
            if (indisponivelMs >= leaseMs && fenced.compareAndSet(false, true)) {
                System.err.println("[FAILOVER] Lease com o reserva expirou. O principal interrompera o atendimento para evitar split-brain.");
                Util.rebaixarParaReserva();
            }
        }
    }

    /**
     * Se A perdeu o lease ou foi cercado por B, nao basta receber STANDBY:
     * ele precisa reconciliar os bancos novamente antes de reabrir listeners.
     * A tarefa roda fora do scheduler para que os heartbeats continuem ativos.
     */
    private void iniciarSincronizacaoDeRecuperacao() {
        if (!prontoParaAtender || !fenced.get()
                || !recuperacaoEmAndamento.compareAndSet(false, true)) {
            return;
        }

        Thread.ofVirtual().name("middleware-sync-recovery").start(() -> {
            try {
                System.out.println("[RECUPERACAO] Reserva voltou a STANDBY. Reconciliando os bancos antes de reativar A.");
                ReplicacaoClient.sincronizarReserva(config);
                String estadoFinal = ReplicacaoClient.consultarEstadoReserva(config);
                if (!"STANDBY".equals(estadoFinal)) {
                    throw new IOException("A reserva deixou de estar em STANDBY durante a recuperacao.");
                }
                iniciarAtendimentoSeCercado();
            } catch (Exception erro) {
                Util.registrarErroServidor("internal", "FAILOVER", "N/A",
                        "ReplicacaoHeartbeatMonitor.sincronizarRecuperacao", erro);
                System.err.println("[RECUPERACAO] A continua em STANDBY; a sincronizacao sera tentada novamente. Motivo: "
                        + erro.getMessage());
            } finally {
                recuperacaoEmAndamento.set(false);
            }
        });
    }

    private void iniciarAtendimentoSeCercado() throws IOException {
        if (fenced.compareAndSet(true, false)) {
            try {
                Util.iniciarServicosDeAtendimento();
            } catch (IOException erro) {
                fenced.set(true);
                Util.rebaixarParaReserva();
                throw erro;
            }
        }
    }

    public static synchronized void parar() {
        if (instancia != null) {
            instancia.scheduler.shutdownNow();
            instancia = null;
        }
    }
}
