package org.ecocean.queue;

/*

this is just a silly placeholder for now... the idea that we could abstract Queue to allow
for both (say) RabbitMQ and a simple directory/file-based queue if the install didnt want to go full RabbitMQ
... but for now let RabbitMQ live on as its own....

*/

public abstract class Queue {
    protected String type = null;
    protected String queueName = null;

    protected Queue(final String name) {
        queueName = name;
    }

    public static synchronized void init(String context) throws java.io.IOException {} //override in base class

    public abstract void publish(String msg) throws java.io.IOException;

    public abstract void consume(QueueMessageHandler msgHandler) throws java.io.IOException;  //assumed to "run in background" and just return

    //this is static and should be overridden
    public static boolean isAvailable(String context) { return false; }

    public boolean isConsumerShutdownMessage(String msg) {
        return "SHUTDOWN".equals(msg);
    }

    public String getType() {
        return type;
    }

    public String toString() {
        return "[" + ((type == null) ? "unknown type" : type) + "] " + ((queueName == null) ? "UNNAMED_QUEUE" : queueName);
    }
}


