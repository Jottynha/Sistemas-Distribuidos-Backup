package pacote;

import java.awt.CheckboxMenuItem;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

public class MenuServidor {
    SystemTray Tray;
    TrayIcon Icon;
    PopupMenu PopUp;
    MenuItem mnuPainelControle;
    CheckboxMenuItem mnuItDesktop, mnuItWeb, mnuItTerceiros, mnuItPublicidade;
    Menu mnuAcoes;
    FrmPainelDeControle frmpainelcontrole;
    public MenuServidor(){
        try{
            frmpainelcontrole = new FrmPainelDeControle();
            if(!SystemTray.isSupported()){
                System.out.print("Sem suporte a SystemTray");    
                return;
            } else {
                Tray = SystemTray.getSystemTray();
                ImageIcon imgIcone = new ImageIcon("images/icone.jpeg","Servidor Chat");
                Icon = new TrayIcon(imgIcone.getImage());
                Icon.setImageAutoSize(true);
                PopUp = new PopupMenu();
                mnuPainelControle = new MenuItem("Abrir Painel de Controle");
                mnuPainelControle.addActionListener(
                        new ActionListener(){
                            @Override
                            public void actionPerformed(ActionEvent ae) {
                                //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
                                frmpainelcontrole.setVisible(true);
                            }
                        }
                );
                mnuAcoes = new Menu("Ações");
                mnuItDesktop = new CheckboxMenuItem("Servidor Desktop");
                mnuItWeb = new CheckboxMenuItem("Servidor Web");
                mnuItTerceiros = new CheckboxMenuItem("Servidor Terceiros");
                mnuItPublicidade = new CheckboxMenuItem("Servidor Publicidade");
                PopUp.add(mnuPainelControle);
                PopUp.addSeparator();
                mnuAcoes.add(mnuItDesktop);
                mnuAcoes.add(mnuItWeb);
                mnuAcoes.add(mnuItTerceiros);
                mnuAcoes.addSeparator();
                mnuAcoes.add(mnuItPublicidade);
                PopUp.add(mnuAcoes);
                Icon.setPopupMenu(PopUp);
                Tray.add(Icon);
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,"Erro ao criar o menu do servidor de chat:" + e.getMessage());
            e.printStackTrace();
        }
    }
    
}
