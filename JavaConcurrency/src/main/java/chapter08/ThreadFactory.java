package chapter08;

// 创建线程的工厂
@FunctionalInterface
public interface ThreadFactory {
    // 用于创建线程
    Thread createThread(Runnable runnable);
}
