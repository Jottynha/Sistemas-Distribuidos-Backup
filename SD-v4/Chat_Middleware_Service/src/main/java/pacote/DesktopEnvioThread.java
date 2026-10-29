package pacote;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import javax.swing.JOptionPane;

public class DesktopEnvioThread implements Runnable {
    private volatile boolean paradaManual = false;
    private ServerSocket emissor;
    private Socket cliente;
    @Override
    public void run() {
        try {
            while (!paradaManual) {
                FileReader freader = new FileReader(Util.PathRepDesktop);
                BufferedReader buffer = new BufferedReader(freader);
                StringBuilder msgs = new StringBuilder(); // evitar criar novas strings
                while (buffer.ready()) {
                    msgs.append(buffer.readLine());
                }
                buffer.close();
                freader.close();
                emissor = new ServerSocket(Util.PortaEnvioDesktop);
                emissor.setReuseAddress(true);
                cliente = emissor.accept();
                if (paradaManual) {
                    break;
                }
                ObjectOutputStream output = new ObjectOutputStream(cliente.getOutputStream());
                output.writeUTF(msgs.toString());
                output.close();
                cliente.close();
                cliente = null; // segurança
                emissor.close();
                emissor = null; // segurança
            }
        } catch (Exception e) {
            if (!paradaManual) {
                JOptionPane.showMessageDialog(null,"Erro em Desktop Envio Thread: "+ e.getMessage());
            }
        } finally {
            fecharRecursos();
        }
    }

    public void fecharServidor() {
        paradaManual = true;
        fecharRecursos();
    }

    private void fecharRecursos() {
        try {
            if (cliente != null &&!cliente.isClosed()) {
                cliente.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (emissor != null && !emissor.isClosed()) {
                emissor.close();
            }
        } catch (Exception ignored) {
        }
    }
}