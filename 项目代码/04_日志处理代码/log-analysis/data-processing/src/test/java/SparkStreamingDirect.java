//import com.atguigu.key.UserHourPackageKey;
//import com.atguigu.model.StartupReportLogs;
//import com.atguigu.model.UserBehaviorStatModel;
//import com.atguigu.service.BehaviorStatService;
//import com.atguigu.utils.DateUtils;
//import com.atguigu.utils.JSONUtil;
//import com.atguigu.utils.PropertiesUtil;
//import com.atguigu.utils.StringUtil;
//import kafka.common.TopicAndPartition;
//import kafka.message.MessageAndMetadata;
//import kafka.serializer.StringDecoder;
//import org.apache.log4j.Logger;
//import org.apache.spark.SparkConf;
//import org.apache.spark.api.java.JavaPairRDD;
//import org.apache.spark.api.java.JavaRDD;
//import org.apache.spark.api.java.function.*;
//import org.apache.spark.streaming.Durations;
//import org.apache.spark.streaming.api.java.JavaDStream;
//import org.apache.spark.streaming.api.java.JavaInputDStream;
//import org.apache.spark.streaming.api.java.JavaPairDStream;
//import org.apache.spark.streaming.api.java.JavaStreamingContext;
//import org.apache.spark.streaming.kafka.HasOffsetRanges;
//import org.apache.spark.streaming.kafka.KafkaCluster;
//import org.apache.spark.streaming.kafka.KafkaUtils;
//import org.apache.spark.streaming.kafka.OffsetRange;
//import scala.Predef;
//import scala.Tuple2;
//import scala.collection.JavaConversions;
//
//import java.util.*;
//import java.util.concurrent.atomic.AtomicReference;
//
//public class SparkStreamingDirect {
//
//    public static Logger log = Logger.getLogger(SparkStreamingDirect.class);
//
//    public static void main(String[] args) throws Exception {
//
//        final Properties serverProps = PropertiesUtil.getProperties("data-processing/src/main/resources/config.properties");
//        //获取checkpoint的hdfs路径
//        String checkpointPath = serverProps.getProperty("streaming.checkpoint.path");
//
//        //如果checkpointPath hdfs目录下的有文件，则反序列化文件生产context,否则使用函数createContext返回的context对象
//        JavaStreamingContext javaStreamingContext = JavaStreamingContext.getOrCreate(checkpointPath, createContext(serverProps));
//        javaStreamingContext.start();
//
//        javaStreamingContext.awaitTermination();
//    }
//
//    /**
//     * 根据配置文件以及业务逻辑创建JavaStreamingContext
//     *
//     * @param serverProps
//     * @return
//     */
//    public static Function0<JavaStreamingContext> createContext(final Properties serverProps) {
//        Function0<JavaStreamingContext> createContextFunc = new Function0<JavaStreamingContext>() {
//            @Override
//            public JavaStreamingContext call() throws Exception {
//
//                //获取配置中的topic
//                String topicStr = serverProps.getProperty("kafka.topic");
//                Set<String> topicSet = new HashSet(Arrays.asList(topicStr.split(",")));
//                //获取配置中的groupId
//                final String groupId = serverProps.getProperty("kafka.groupId");
//
//                //获取批次的时间间隔，比如5s
//                final Long streamingInterval = Long.parseLong(serverProps.getProperty("streaming.interval"));
//                //获取checkpoint的hdfs路径
//                final String checkpointPath = serverProps.getProperty("streaming.checkpoint.path");
//                //获取kafka broker列表
//                final String kafkaBrokerList = serverProps.getProperty("kafka.broker.list");
//
//                //组合kafka参数
//                final Map<String, String> kafkaParams = new HashMap();
//                kafkaParams.put("metadata.broker.list", kafkaBrokerList);
//                kafkaParams.put("group.id", groupId);
//
//                //从zookeeper中获取每个分区的消费到的offset位置
//                final KafkaCluster kafkaCluster = getKafkaCluster(kafkaParams);
//                Map<TopicAndPartition, Long> consumerOffsetsLong = getConsumerOffsets(kafkaCluster, groupId, topicSet);
//                printZkOffset(consumerOffsetsLong);
//
//                // 创建SparkConf对象
//                SparkConf sparkConf = new SparkConf().setMaster("local[*]").setAppName("online");
//
//                // 优雅停止Spark
//                // 暴力停掉sparkstreaming是有可能出现问题的，比如你的数据源是kafka，
//                // 已经加载了一批数据到sparkstreaming中正在处理，如果中途停掉，
//                // 这个批次的数据很有可能没有处理完，就被强制stop了，
//                // 下次启动时候会重复消费或者部分数据丢失。
//                sparkConf.set("spark.streaming.stopGracefullyOnShutdown", "true");
//
//                // 在Spark的架构中，在网络中传递的或者缓存在内存、硬盘中的对象需要进行序列化操作，序列化的作用主要是利用时间换空间
//                sparkConf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
//                //增加MyRegistrator类，注册需要用Kryo序列化的类
//                sparkConf.set("spark.kryo.registrator", "com.atguigu.registrator.MyKryoRegistrator");
//
//                // 每秒钟对于每个partition读取多少条数据
//                // 如果不进行设置，Spark Streaming会一开始就读取partition中的所有数据到内存，给内存造成巨大压力
//                // 设置此参数后可以很好地控制Spark Streaming读取的数据量，也可以说控制了读取的进度
//                sparkConf.set("spark.streaming.kafka.maxRatePerPartition", "100");
//
//                // 创建javaStreamingContext，设置5s执行一次
//                JavaStreamingContext javaStreamingContext = new JavaStreamingContext(sparkConf, Durations.seconds(streamingInterval));
//                javaStreamingContext.checkpoint(checkpointPath);
//
//                //创建kafka DStream
//                JavaInputDStream<String> kafkaMessage = KafkaUtils.createDirectStream(
//                        javaStreamingContext,
//                        String.class,
//                        String.class,
//                        StringDecoder.class,
//                        StringDecoder.class,
//                        String.class,
//                        kafkaParams,
//                        consumerOffsetsLong,
//                        new Function<MessageAndMetadata<String, String>, String>() {
//                            public String call(MessageAndMetadata<String, String> v1) throws Exception {
//                                return v1.message();
//                            }
//                        }
//                );
//
//                //原子性引用
//                //需要把每个批次的offset保存到此变量
//                final AtomicReference<OffsetRange[]> offsetRanges = new AtomicReference();
//
//                JavaDStream<String> kafkaMessageDStreamTransform = kafkaMessage.transform(new Function<JavaRDD<String>, JavaRDD<String>>() {
//                    public JavaRDD<String> call(JavaRDD<String> rdd) throws Exception {
//                        // 表示具有[[OffsetRange]]集合的任何对象，这可以用来访问
//                        // 由直Direct Kafka DStream生成的RDD中的偏移量范围
//                        OffsetRange[] offsets = ((HasOffsetRanges) rdd.rdd()).offsetRanges();
//                        offsetRanges.set(offsets);
//
//                        for (OffsetRange o : offsetRanges.get()) {
//                            log.info("rddoffsetRange:========================================================================");
//                            log.info("rddoffsetRange:topic=" + o.topic()
//                                    + ",partition=" + o.partition()
//                                    + ",fromOffset=" + o.fromOffset()
//                                    + ",untilOffset=" + o.untilOffset()
//                                    + ",rddpartitions=" + rdd.getNumPartitions()
//                                    + ",isempty=" + rdd.isEmpty()
//                                    + ",id=" + rdd.id());
//                        }
//
//                        return rdd;
//                    }
//                });
//
//                //将kafka中的消息转换成对象并过滤不合法的消息
//                JavaDStream<String> kafkaMessageFilter = kafkaMessageDStreamTransform.filter(new Function<String, Boolean>() {
//                    @Override
//                    public Boolean call(String message) throws Exception {
//                        try {
//                            // 第一步：将topic中不合法的message全部滤除
//                            if(!message.contains("activeTimeInMs") && !message.contains("stayDurationInSec") &&
//                                    !message.contains("errorMajor")){
//                                return false;
//                            }
//
//                            // 由于只对startup日志进行处理，因此去除其他的日志
//                            if(!message.contains("activeTimeInMs") || !message.contains("appVersion")){
//                                return false;
//                            }
//
//                            // 第二步：完成JSON到对象的转换
//                            StartupReportLogs requestModel = JSONUtil.json2Object(message, StartupReportLogs.class);
//
//                            // 第三步：滤除不合法的Log对象
//                            if (requestModel == null ||
//                                    StringUtil.isEmpty(requestModel.getUserId()) ||
//                                    StringUtil.isEmpty(requestModel.getAppId())
//                                    ) {
//                                return false;
//                            }
//                            return true;
//                        } catch (Exception e) {
//                            log.error("find illegal message", e);
//                            return false;
//                        }
//                    }
//                });
//
//                //将每条用户行为转换成键值对，建是我们自定义的key,值是使用应用的时长，并统计时长
//                JavaPairDStream<UserHourPackageKey, Long> kafkaStatic = kafkaMessageFilter.mapToPair(new PairFunction<String, UserHourPackageKey, Long>() {
//                    @Override
//                    public Tuple2<UserHourPackageKey, Long> call(String s) throws Exception {
//                        StartupReportLogs requestModel = null;
//                        try {
//                            //将JSON字符串数据转换为StartupReportLogs.class对象
//                            requestModel = JSONUtil.json2Object(s, StartupReportLogs.class);
//                            //System.out.println(requestModel.toString());
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//
//                        //以userId、Hour(yyyyMMddHH)、PackageName组合为key
//                        UserHourPackageKey key = new UserHourPackageKey();
//                        String userId = requestModel.getUserId();
//                        key.setUserId(Long.valueOf(userId.substring(4, userId.length())));
//                        Long startTime = requestModel.getStartTimeInMs();
//                        key.setHour(DateUtils.getDateStringByMillisecond(DateUtils.HOUR_FORMAT, startTime));
//                        key.setPackageName(requestModel.getAppId());
//                        key.setCity(requestModel.getCity());
//
//                        //以Package活跃时间为value
//                        Tuple2<UserHourPackageKey, Long> t = new Tuple2<UserHourPackageKey, Long>(key, requestModel.getActiveTimeInMs() / 1000);
//
//                        return t;
//                    }
//                    //将一个批次中的同一Package的activeTime聚合到一起
//                }).reduceByKey(new Function2<Long, Long, Long>() {
//                    @Override
//                    public Long call(Long v1, Long v2) throws Exception {
//                        return v1 + v2;
//                    }
//                });
//
//                kafkaStatic.print();
//
//                //将每个用户的统计时长写入hbase
//                kafkaStatic.foreachRDD(new VoidFunction<JavaPairRDD<UserHourPackageKey, Long>>() {
//                    @Override
//                    public void call(JavaPairRDD<UserHourPackageKey, Long> rdd) throws Exception {
//                        rdd.foreachPartition(new VoidFunction<Iterator<Tuple2<UserHourPackageKey, Long>>>() {
//                            @Override
//                            public void call(Iterator<Tuple2<UserHourPackageKey, Long>> it) throws Exception {
//                                BehaviorStatService service = BehaviorStatService.getInstance(serverProps);
//
//                                while (it.hasNext()) {
//                                    Tuple2<UserHourPackageKey, Long> t = it.next();
//                                    UserHourPackageKey key = t._1();
//
//                                    //创建用户行为统计模型
//                                    UserBehaviorStatModel model = new UserBehaviorStatModel();
//                                    //根据key中的数据填充统计模型
//                                    model.setUserId(StringUtil.getFixedLengthStr(key.getUserId() + "", 10));
//                                    model.setHour(key.getHour());
//                                    model.setPackageName(key.getPackageName());
//                                    model.setTimeLen(t._2());
//                                    model.setCity(key.getCity());
//
//                                    //根据统计模型中的数据，对HBase表中的数据进行更新
//                                    service.addUserNumOfCity(model);
//                                }
//                            }
//                        });
//
//                        //kafka offset写入zk
//                        offsetToZk(kafkaCluster, offsetRanges, groupId);
//                    }
//                });
//                return javaStreamingContext;
//            }
//        };
//        return createContextFunc;
//    }
//
//    /*
//    * 将offset写入zk
//    * */
//    public static void offsetToZk(final KafkaCluster kafkaCluster,
//                                  final AtomicReference<OffsetRange[]> offsetRanges,
//                                  final String groupId) {
//        // 遍历每一个偏移量信息
//        for (OffsetRange o : offsetRanges.get()) {
//
//            // 提取offsetRange中的topic和partition信息封装成TopicAndPartition
//            TopicAndPartition topicAndPartition = new TopicAndPartition(o.topic(), o.partition());
//            // 创建Map结构保持TopicAndPartition和对应的offset数据
//            Map<TopicAndPartition, Object> topicAndPartitionObjectMap = new HashMap();
//            // 将当前offsetRange的topicAndPartition信息和untilOffset信息写入Map
//            topicAndPartitionObjectMap.put(topicAndPartition, o.untilOffset());
//
//            // 将Java的Map结构转换为Scala的mutable.Map结构
//            scala.collection.mutable.Map<TopicAndPartition, Object> testMap = JavaConversions.mapAsScalaMap(topicAndPartitionObjectMap);
//
//            // 将Scala的mutable.Map转化为imutable.Map
//            scala.collection.immutable.Map<TopicAndPartition, Object> scalatopicAndPartitionObjectMap =
//                    testMap.toMap(new Predef.$less$colon$less<Tuple2<TopicAndPartition, Object>, Tuple2<TopicAndPartition, Object>>() {
//                        public Tuple2<TopicAndPartition, Object> apply(Tuple2<TopicAndPartition, Object> v1) {
//                            return v1;
//                        }
//                    });
//
//            // 更新offset到kafkaCluster
//            kafkaCluster.setConsumerOffsets(groupId, scalatopicAndPartitionObjectMap);
//        }
//    }
//
//    /*
//    * 获取kafka每个分区消费到的offset,以便继续消费
//    * */
//    public static Map<TopicAndPartition, Long> getConsumerOffsets(KafkaCluster kafkaCluster, String groupId, Set<String> topicSet) {
//        // 将Java的Set结构转换为Scala的mutable.Set结构
//        scala.collection.mutable.Set<String> mutableTopics = JavaConversions.asScalaSet(topicSet);
//        // 将Scala的mutable.Set结构转换为immutable.Set结构
//        scala.collection.immutable.Set<String> immutableTopics = mutableTopics.toSet();
//        // 根据传入的分区，获取TopicAndPartition形式的返回数据
//        scala.collection.immutable.Set<TopicAndPartition> topicAndPartitionSet2 = (scala.collection.immutable.Set<TopicAndPartition>)
//                kafkaCluster.getPartitions(immutableTopics).right().get();
//
//
//        // 创建用于存储offset数据的HashMap
//        // (TopicAndPartition, Offset)
//        Map<TopicAndPartition, Long> consumerOffsetsLong = new HashMap();
//
//        // kafkaCluster.getConsumerOffsets：通过kafkaCluster的getConsumerOffsets方法获取指定消费者组合，指定主题分区的offset
//        // 如果返回Left，代表获取失败，Zookeeper中不存在对应的offset，因此HashMap中对应的offset应该设置为0
//        if (kafkaCluster.getConsumerOffsets(groupId, topicAndPartitionSet2).isLeft()) {
//
//            // 将Scala的Set结构转换为Java的Set结构
//            Set<TopicAndPartition> topicAndPartitionSet1 = JavaConversions.setAsJavaSet(topicAndPartitionSet2);
//
//            // 由于没有保存offset（该group首次消费时）, 各个partition offset 默认为0
//            for (TopicAndPartition topicAndPartition : topicAndPartitionSet1) {
//                consumerOffsetsLong.put(topicAndPartition, 0L);
//            }
//        } else {
//            // offset已存在, 获取Zookeeper上的offset
//            // 获取到的结构为Scala的Map结构
//            scala.collection.immutable.Map<TopicAndPartition, Object> consumerOffsetsTemp =
//                    (scala.collection.immutable.Map<TopicAndPartition, Object>)
//                            kafkaCluster.getConsumerOffsets(groupId, topicAndPartitionSet2).right().get();
//
//            // 将Scala的Map结构转换为Java的Map结构
//            Map<TopicAndPartition, Object> consumerOffsets = JavaConversions.mapAsJavaMap(consumerOffsetsTemp);
//
//            // 将Scala的Set结构转换为Java的Set结构
//            Set<TopicAndPartition> topicAndPartitionSet1 = JavaConversions.setAsJavaSet(topicAndPartitionSet2);
//
//            // 将offset加入到consumerOffsetsLong的对应项
//            for (TopicAndPartition topicAndPartition : topicAndPartitionSet1) {
//                Long offset = (Long) consumerOffsets.get(topicAndPartition);
//                consumerOffsetsLong.put(topicAndPartition, offset);
//            }
//        }
//
//        return consumerOffsetsLong;
//    }
//
//    public static KafkaCluster getKafkaCluster(Map<String, String> kafkaParams) {
//        // 将Java的HashMap转化为Scala的mutable.Map
//        scala.collection.mutable.Map<String, String> testMap = JavaConversions.mapAsScalaMap(kafkaParams);
//        // 将Scala的mutable.Map转化为imutable.Map
//        scala.collection.immutable.Map<String, String> scalaKafkaParam =
//                testMap.toMap(new Predef.$less$colon$less<Tuple2<String, String>, Tuple2<String, String>>() {
//                    public Tuple2<String, String> apply(Tuple2<String, String> v1) {
//                        return v1;
//                    }
//                });
//
//        // 由于KafkaCluster的创建需要传入Scala.HashMap类型的参数，因此要进行上述的转换
//        // 将immutable.Map类型的Kafka参数传入构造器，创建KafkaCluster
//        return new KafkaCluster(scalaKafkaParam);
//    }
//
//    /**
//     * 打印配置文件
//     *
//     * @param serverProps
//     */
//    public static void printConfig(Properties serverProps) {
//        Iterator<Map.Entry<Object, Object>> it1 = serverProps.entrySet().iterator();
//        while (it1.hasNext()) {
//            Map.Entry<Object, Object> entry = it1.next();
//            log.info(entry.getKey().toString() + "=" + entry.getValue().toString());
//        }
//    }
//
//    /**
//     * 打印从zookeeper中获取的每个分区消费到的位置
//     *
//     * @param consumerOffsetsLong
//     */
//    public static void printZkOffset(Map<TopicAndPartition, Long> consumerOffsetsLong) {
//        Iterator<Map.Entry<TopicAndPartition, Long>> it1 = consumerOffsetsLong.entrySet().iterator();
//        while (it1.hasNext()) {
//            Map.Entry<TopicAndPartition, Long> entry = it1.next();
//            TopicAndPartition key = entry.getKey();
//            log.info("zookeeper offset:partition=" + key.partition() + ",beginOffset=" + entry.getValue());
//        }
//    }
//}
