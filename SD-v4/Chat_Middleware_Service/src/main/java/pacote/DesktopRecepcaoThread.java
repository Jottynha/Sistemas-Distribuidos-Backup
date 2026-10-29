package pacote;

import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import javax.swing.JOptionPane;

public class DesktopRecepcaoThread implements Runnable {
    private volatile boolean paradaManual = false;
    private Socket cliente;
    private ServerSocket receptor;
    @Override
    public void run() {
        try{
            while(!paradaManual){
                receptor = new ServerSocket(Util.PortaRecepcaoDesktop);
                cliente = receptor.accept();
                ObjectInputStream reader = new ObjectInputStream(cliente.getInputStream());
                String msg = reader.readUTF();
                reader.close();
                cliente.close();
                receptor.close();
                FileWriter fwriter = new FileWriter(Util.PathRepDesktop,true);
                fwriter.write(msg);
                fwriter.write(System.lineSeparator());
                fwriter.close();
            }
        } catch (Exception e){
            if(!paradaManual){
                JOptionPane.showMessageDialog(null,"Erro em DesktopRecepcaoThread::run:\n"+e.getMessage());                
            }
        }
    }
    public void fecharServidor() {
        paradaManual = true;
        try {
            if (cliente != null && !cliente.isClosed()) {
                cliente.close();
            }
            if (receptor != null && !receptor.isClosed()) {
                receptor.close();
            }
        } catch (Exception e) {
            if (paradaManual) {
            }
        }
}
    
    
}
