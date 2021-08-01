package chapter08;

@FunctionalInterface
public interface DenyPolicy {
    // 拒绝方法
    void reject(Runnable runnable, ThreadPool threadPool);

    // 该拒绝策略会直接将任务丢弃
    class DiscardDenyPolicy implements  DenyPolicy {
        @Override
        public void reject(Runnable runnable, ThreadPool threadPool) {
            // do nothing
        }
    }

    // 该拒绝策略会向任务提交者抛出异常
    class AbortDenyPolicy implements DenyPolicy {
        @Override
        public void reject(Runnable runnable, ThreadPool threadPool) {
//            throw new Run
        }
    }

    // 该拒绝策略会使任务提交者所在的线程中执行任务
    class RunnerDenyPolicy implements DenyPolicy {
        @Override
        public void reject(Runnable runnable, ThreadPool threadPool) {
            if( !threadPool.isShutdown()) {
                runnable.run();
            }
        }
    }

}
