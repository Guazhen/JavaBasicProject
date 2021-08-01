package chapter08;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BasicThreadPool extends Thread implements ThreadPool {

    // 初始化线程数量
    private final int initSize;

    // 线程池最大线程数量
    private final int maxSize;

    // 线程池核心线程数量
    private final int coreSize;

    // 当前活跃的线程数量
    private int activeCount;

    // 创建线程所需的工厂
    private final ThreadFactory threadFactory;

    // 任务队列
    private  final RunnableQueue runnableQueue;

    // 线程池是否已经被shutdown
    private volatile boolean isShutdown = false;

    // 工作线程队列
    private final Queue<ThreadTask> threadQueue = new ArrayDeque<>();

    private final static DenyPolicy DEFAULT_DENY_POLICY = new DenyPolicy.DiscardDenyPolicy();

    private final static ThreadFactory DEFAULT_THREAD_FACTORY=new DefaultThreadFactory();

    private final long keepAliveTime;
    private final TimeUnit timeUnit;

    // 构造时需要传递的参数：初始化的线程数量，最大的线程数量，核心线程数量，任务队列的最大数量
    public BasicThreadPool(int initSize, int maxSize, int coreSize, int queueSize) {
        this(initSize, maxSize, coreSize, DEFAULT_THREAD_FACTORY, queueSize, DEFAULT_DENY_POLICY,10,TimeUnit.SECONDS);
    }

    public BasicThreadPool(int initSize, int maxSize, int coreSize, ThreadFactory threadFactory,
                           int queueSize, DenyPolicy denyPolicy, long keepAliveTime,
                           TimeUnit timeUnit) {
        this.initSize = initSize;
        this.maxSize = maxSize;
        this.coreSize = coreSize;
        this.threadFactory = threadFactory;
        this.runnableQueue = new LinkedRunnableQueue(queueSize, denyPolicy, this);
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.init();
    }

    // 初始化时，先创建initSize个线程
    private void init() {
        start();
        for(int i = 0; i < initSize; i++) {
            newThread();
        }
    }
    /*
        一个线程池除了控制参数之外，最主要的是应该有活动线程，其中Queue<Thread-Task>主要用来存放活动线程，
        BasicThreadPool同时也是Thread的子类，它在初始化的时候启动， 在keepalive时间间隔到了之后再自动维护
        活动线程数量(采用继承Thread的方式其实不是一种好的方法，因为Basic ThreadPool会暴露Thread的方法，
        建议将继承关系更改为组合关系，读者可以自行修改)。
     */


    /*
    提交任务非常简单，只是将Runnable插入runnableQueue中即可。
     */
    @Override
    public void execute(Runnable runnable) {
        if( this.isShutdown ) {
            throw new IllegalStateException("The thread pool is destory");
        }
        // 提交任务只是简单地往任务队列中插入Runnable
        this.runnableQueue.offer(runnable);
    }

    // 4.线程池自动维护
    /*
    线程池中线程数量的维护主要由run负责， 这也是为什么BasicThreadPool继承自Thread了，
     不过笔者不推荐使用直接继承的方式， 线程池自动维护代码如下：
     */

    private void newThread() {
        // 创建任务线程，并且启动
        InternalTask internalTask = new InternalTask(runnableQueue);
        Thread thread = this.threadFactory.createThread(internalTask);
        ThreadTask threadTask = new ThreadTask(thread, internalTask);
        threadQueue.offer(threadTask);
        this.activeCount++;
        this.start();
    }

    private void removeThread(){
        //从线程池中移除某个线程
        ThreadTask threadTask = threadQueue.remove();
        threadTask.internalTask.stop();
        this.activeCount--;
    }

    @Override
    public void run() {
        // run方法继承自Thread主要用于维护线程数量，比如扩容，回收等工作
        while(!isShutdown && !isInterrupted() ) {
            try {
                timeUnit.sleep(keepAliveTime);
            } catch (InterruptedException e) {
                isShutdown = true;
                break;
            }
            synchronized (this) {
                if ( isShutdown) break;
                // 当前的队列中有任务尚未处理，并且activeCount<coreSize 则继续扩容
                if ( runnableQueue.size() > 0 && activeCount < coreSize ) {
                    for(int i = initSize; i < coreSize; i++) {
                        newThread();
                    }
                    // continue的目的在于不想让线程的扩容直接达到maxSize
                    continue;
                }

                // 当前的队列中有任务尚未处理，并且activeCount < maxSize则继续扩容
                if ( runnableQueue.size() > 0 && activeCount < maxSize ) {
                    for( int i = coreSize; i < maxSize; i++) {
                        newThread();
                    }
                }
                // 如果任务队列中没有任务，则需要回收，回收至coreSize即可
                if ( runnableQueue.size() == 0 && activeCount > coreSize) {
                    for( int i = coreSize; i < activeCount; i++) {
                        removeThread();
                    }
                }
            }
        }
    }

    /*
    5. 线程池销毁
    线程池的销毁同样需要同步机制的保护，
    主要是为了防止与线程池本身的维护线程引起数据冲突，线程池销毁代码如下：
     */
    /*
    销毁线程池主要为了是停止BasicThreadPool线程， 停止线程池中的活动线程并且将
    isShutdown开关变量更改为true
     */
    /*
    1.下面重点来解说线程自动维护的方法，自动维护线程的代码块是同步代码块，主要是为了阻止在线程维护过程中线程池销毁引起的数据不一致问题。
    2.任务队列中若存在积压任务， 并且当前活动线程少于核心线程数， 则新建coreSize initSize数量的线程，并且将其加入到活动线程队列中
    为了防止马上进行coreSize数量的扩充，建议使用continue终止本次循环。
    3.任务队列中有积压任务，并且当前活动线程少于最大线程数，则新建maxSize-coreSize数量的线程，并且将其加入到活动队列中。
    4.当前线程池不够繁忙时，则需要回收部分线程，回收到coreSize数量即可，回收时调用removeThread()方法，
    在该方法中需要考虑的一点是，如果被回收的线程恰巧从Runnable任务取出了某个任务，
    则会继续保持该线程的运行， 直到完成了任务的运行为止，详见Internal Task的run方法。
     */
    @Override
    public void shutdown() {
        synchronized (this) {
            if ( isShutdown ) return;
            isShutdown = true;
            threadQueue.forEach(threadTask -> {
                threadTask.internalTask.stop();
                threadTask.thread.interrupt();
            });
            this.interrupt();
        }
    }

    @Override
    public int getInitSize() {
        if( isShutdown)
            throw new IllegalStateException("The thread pool is destory");
        return this.initSize;
    }

    @Override
    public int getMaxSize() {
        if ( isShutdown )
            throw new IllegalStateException("The thread pool is destory");
        return this.maxSize;
    }

    @Override
    public int getCoreSize() {
        if ( isShutdown )
            throw new IllegalStateException("The thread pool is destory");
        return this.coreSize;
    }

    @Override
    public int getQueueSize() {
        if ( isShutdown )
            throw new IllegalStateException("The thread pool is destory");
        return runnableQueue.size();
    }

    @Override
    public int getActiveCount() {
        synchronized (this) {
            return this.activeCount;
        }
    }

    @Override
    public boolean isShutdown() {
        return this.isShutdown;
    }

    private static class DefaultThreadFactory implements  ThreadFactory {

        private static final AtomicInteger GROUP_COUNTER = new AtomicInteger(1);
        private static final ThreadGroup group = new ThreadGroup("MyThreadPool-"
        + GROUP_COUNTER.getAndDecrement());

        private static final AtomicInteger COUNTER = new AtomicInteger(0);

        @Override
        public Thread createThread(Runnable runnable) {
            return new Thread(group, runnable, "thread-pool-" + COUNTER.getAndDecrement());
        }
    }

    private static class ThreadTask {
        Thread thread;
        InternalTask internalTask;
        public ThreadTask(Thread thread, InternalTask internalTask) {
            this.thread = thread;
            this.internalTask = internalTask;
        }
    }

}
