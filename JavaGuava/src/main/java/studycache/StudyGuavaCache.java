package studycache;

import com.google.common.cache.*;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class StudyGuavaCache {

    @Test
    public void TestCacheBuild() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)  // 设置缓存最多可以容纳的对象的个数
                .build();
        cache.put("hello", "hello world");
        System.out.println(cache.getIfPresent("hello"));
    }

    @Test
    public void TestCacheExpireAfterWrite() throws InterruptedException {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)  // 设置缓存最多可以容纳的对象的个数
                .expireAfterWrite(3, TimeUnit.SECONDS)  //对象写入3秒之后过期
                .build();

        cache.put("testkey1", "value1");
        int time = 1;
        while(true) {
            System.out.println("第" + time++ + "次取到了的值为：" + cache.getIfPresent("testkey1"));
            Thread.sleep(1000);
        }
    }

    @Test
    public void TestCacheWeakReference() {
        Cache<String, Object> cache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .weakValues()  // 设置value为弱引用
                .build();

        Object object = new Object();
//        String str = "hello";
        cache.put("key1", object);
        System.out.println(cache.getIfPresent("key1"));
        object = new Object();
        System.gc();
        System.out.println(cache.getIfPresent("key1"));
    }

    @Test
    public void Testinvalidate() {
        Cache<String, String> cache =  CacheBuilder.newBuilder().build();

        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        List<String> strings = new ArrayList<String>();
        strings.add("key1");
        strings.add("key2");

        cache.invalidateAll(strings);  //设置需要删除的key

        System.out.println(cache.getIfPresent("key1"));
        System.out.println(cache.getIfPresent("key2"));
        System.out.println(cache.getIfPresent("key3"));

    }

    // 测试监听器
    @Test
    public void TestListener() {
        RemovalListener<String, String> listener = new RemovalListener<String, String>() {
            public void onRemoval(RemovalNotification<String, String> notification) {
                System.out.println("[" + notification.getKey() + ":" + notification.getValue() + "] is removed!");
            }
        };

        Cache<String,String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .removalListener(listener)
                .build();

        cache.put("key1","value1");
        cache.put("key2","value2");
        cache.put("key3","value3");
        cache.put("key4","value3");
        cache.put("key5","value3");
        cache.put("key6","value3");
        cache.put("key7","value3");
        cache.put("key8","value3");

    }

    private static Cache<String,String> cache = CacheBuilder.newBuilder()
            .maximumSize(3)
            .build();

    public static void main() {
        new Thread(new Runnable() {
            public void run() {
                System.out.println("thread1");
                try {
                    String value = cache.get("key", new Callable<String>() {
                        public String call() throws Exception {
                            System.out.println("load1"); //加载数据线程执行标志
                            Thread.sleep(1000); //模拟加载时间
                            return "auto load by Callable";
                        }
                    });
                    System.out.println("thread1 " + value);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(new Runnable() {
            public void run() {
                System.out.println("thread2");
                try {
                    String value = cache.get("key", new Callable<String>() {
                        public String call() throws Exception {
                            System.out.println("load2"); //加载数据线程执行标志
                            Thread.sleep(1000); //模拟加载时间
                            return "auto load by Callable";
                        }
                    });
                    System.out.println("thread2 " + value);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 自动加载配置信息功能
     * @throws InterruptedException
     */
    @Test
    public void TestAutoLoader() throws InterruptedException {
        main();
        Thread.sleep(3000);
    }

    /**
     * 用于统计缓存命中情况信息
     */
    @Test
    public void TestRecordStats() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .recordStats()  // 开启统计功能
                .build();

        cache.put("key1","value1");
        cache.put("key2","value2");
        cache.put("key3","value3");
        cache.put("key4","value4");

        cache.getIfPresent("key1");
        cache.getIfPresent("key2");
        cache.getIfPresent("key3");
        cache.getIfPresent("key4");
        cache.getIfPresent("key5");
        cache.getIfPresent("key6");

        System.out.println(cache.stats()); //获取统计信息

    }

    /**
     * LoadingCache是Cache的子接口，相比较于Cache，当从LoadingCache中读取一个指定key的记录时，
     * 如果该记录不存在，则LoadingCache可以自动执行加载数据到缓存的操作。
     */
    @Test
    public void TestLoadingCache() throws ExecutionException {
        CacheLoader<String,String> loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) throws Exception {
                Thread.sleep(1000); //休眠1s，模拟加载数据
                System.out.println(key + " is loaded from a cacheLoader!");
                return key + "'s value";
            }
        };

        LoadingCache<String,String> loadingCache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .build(loader);

        System.out.println(loadingCache.get("key1"));
        System.out.println(loadingCache.get("key2"));
        System.out.println(loadingCache.get("key3"));

    }

}
