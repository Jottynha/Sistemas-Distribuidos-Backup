package pacote;

public class Threads {
    public static void main(String[] args) {
        // Execução na thread principal, demais executam após o trecho de código
        for(int i=0; i < 100_000; i++){
            System.out.println("Thread Principal");
        }
        // Thread principal intancia outras threads e faz execução simultânea
        Multiexecucao exec1 = new Multiexecucao(1);
        Thread t1 = new Thread(exec1);
        Multiexecucao exec2 = new Multiexecucao(2);
        Thread t2 = new Thread(exec2);
        // Outra forma de criar
        Thread t3 = new Thread(new Multiexecucao(3));
        // Usando virtual threads
        Multiexecucao exec4 = new Multiexecucao(4);
        Thread t4 = Thread.ofVirtual().unstarted(exec4);
        // Outra forma
        Thread.ofVirtual().start(new Multiexecucao(5));
        // Outra forma
        Multiexecucao exec6 = new Multiexecucao(6);
        Thread.ofVirtual().start(exec6);
        // Classes abstratas com apenas 1 método a ser implementado, podem fazê-lo via lambda
        // Runnable é um exemplo
        Thread t7 = new Thread(() ->{ // () é os parâmetros do método a ser implementado, no caso de run nenhum
            for(int i=0; i < 100_000; i++){
                System.out.println("Sintaxe Lambda");
            }
        }
                
        );
        t1.start();
        t2.start();
        t3.start();
        t4.start();
        t7.start();
    }
}
