package examples;

import java.util.Date;
import java.util.List;

class Receipt {
    public Integer money;

    Receipt(Integer money) {
        this.money = money;
    }
}

public class Shop {

    private static void logWork(String log) {
        System.out.println(new Date() + ": " + Thread.currentThread() + ": " + log);
    }

    public static Receipt dealWithBottles() throws InterruptedException {
        Thread.sleep(10000); // Passing bottles to bottle-machine takes about 10 mins
        logWork("Finished with the bottles");
        return new Receipt(-10); // And we earn 10 euros for that
    }

    public static Receipt takeMeat() throws InterruptedException {
        Thread.sleep(6000); // OOOps! There is a queue, you have to take the number and wait for your turn!
        logWork("Finished with the meat");
        return new Receipt(6); // And we pay 6 euros
    }

    public static Receipt takeVegetables() throws InterruptedException {
        Thread.sleep(5000); // We must select good ones and weight each of them
        logWork("Finished with vegetables");
        return new Receipt(5); // And we pay 5 euros
    }

    public static void waitForLongQueue() throws InterruptedException {
        Thread.sleep(7000);     // Waiting for the queue
        logWork("Queue passed");
    }

    public static void pay(List<Receipt> receiptList, int moneyInWallet) throws Exception {
        int result = moneyInWallet;
        for (Receipt receipt : receiptList) {
            result -= receipt.money;
        }
        if (result < 0) {
            throw new Exception("Not enough money :(");
        }
        logWork("Payment processed, " + result + " money left.");
    }
}
