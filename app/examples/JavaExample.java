package examples;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;



public class JavaExample {

    public static void main(String[] args) {
        try1();
        try2();
    }

    private static void try1() {
        long start = System.currentTimeMillis();
        try {
            Receipt bottles = Shop.dealWithBottles();
            Receipt meat = Shop.takeMeat();
            Receipt vegetables = Shop.takeVegetables();
            Shop.waitForLongQueue();
            Shop.pay(Lists.newArrayList(bottles, meat, vegetables), 10);
        } catch (Exception e) {
            System.out.println("Show quest failed!" + e);
        }
        System.out.println("Finished in " + ((System.currentTimeMillis() - start) / 1000));
    }

    private static void try2() {
        long start = System.currentTimeMillis();
        try {
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<Receipt>> futures = new ArrayList<>();
            Future<Receipt> bottles = executor.submit(new Callable<Receipt>() {
                @Override
                public Receipt call() throws Exception {
                    return Shop.dealWithBottles();
                }
            });
            futures.add(bottles);
            Future<Receipt> meat = executor.submit(new Callable<Receipt>() {
                @Override
                public Receipt call() throws Exception {
                    return Shop.takeMeat();
                }
            });
            futures.add(meat);
            Future<Receipt> vegetables = executor.submit(new Callable<Receipt>() {
                @Override
                public Receipt call() throws Exception {
                    return Shop.takeVegetables();
                }
            });
            futures.add(vegetables);
            Future<?> queue = executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Shop.waitForLongQueue();
                    } catch (InterruptedException e) {
                        e.printStackTrace(); // TODO: We can't just ignore the error!!!
                    }
                }
            });
            // Wait while finish
            int running = futures.size() + 1;
            while (running > 0) {
                running = futures.size() + 1;
                for (Future<Receipt> f : futures) {
                    if (f.isDone()) running--;
                }
                if (queue.isDone()) running--;
            }
            // Collect receipts
            final ArrayList<Receipt> receipts = new ArrayList<>(futures.size());
            for (Future<Receipt> f : futures) {
                receipts.add(f.get());
            }

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Shop.pay(receipts, 10);
                    } catch (Exception e) {
                        e.printStackTrace();  // TODO: We can't just ignore the error!!!
                    }
                }
            });

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.println("Finished in " + ((System.currentTimeMillis() - start) / 1000));

        // And now we need to go one more level deeper (c) Inception
        // What if we need to do such quest in multiple shops?
    }
}
