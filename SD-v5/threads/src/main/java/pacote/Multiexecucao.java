package pacote;
// Toda classe que terá métodos executados por threads, precisa implementar a classe abstrata Runnable
// Após criar a classe que implementa Runnable, atribua as tarefas ao método run()
// Para executar em thread instancie como thread a sua classe e execute o método start() [aloca um fio de processamento para executar o método run()]
public class Multiexecucao implements Runnable{
    int valor=0;
    public Multiexecucao(int dado){
        this.valor = dado;
    }
    @Override
    public void run() {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        for(int i=0; i<100_000;i++){
            System.out.println("Impresso pela Thread:" + this.valor);
        }
    }
    
}
