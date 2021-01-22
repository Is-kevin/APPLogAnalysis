package com.atguigu.online;

import com.atguigu.key.UserCityPackageKey;
import com.atguigu.model.StartupReportLogs;
import com.atguigu.model.UserCityStatModel;
import com.atguigu.service.BehaviorStatService;
import com.atguigu.utils.JSONUtil;
import com.atguigu.utils.PropertiesUtil;
import com.atguigu.utils.StringUtil;
import kafka.common.TopicAndPartition;
import kafka.message.MessageAndMetadata;
import kafka.serializer.StringDecoder;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.*;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.HasOffsetRanges;
import org.apache.spark.streaming.kafka.KafkaCluster;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.apache.spark.streaming.kafka.OffsetRange;
import org.codehaus.janino.Java;
import scala.Predef;
import scala.Tuple2;
import scala.collection.JavaConversions;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class OnlineProcess {

    public static void main(String[] args) throws Exception{

        Properties serverProperties = PropertiesUtil.getProperties("data-processing/src/main/resources/config.properties");
        String checkPoint = serverProperties.getProperty("streaming.checkpoint.path");

        JavaStreamingContext javaStreamingContext = JavaStreamingContext.getOrCreate(checkPoint, createContext(serverProperties));

        javaStreamingContext.start();
        javaStreamingContext.awaitTermination();
    }

    private static Function0<JavaStreamingContext> createContext(final Properties serverProperties) {

        Function0<JavaStreamingContext> func = new Function0<JavaStreamingContext>() {
            @Override
            public JavaStreamingContext call() throws Exception {

                SparkConf sparkConf = new SparkConf().setAppName("online").setMaster("local[*]");
                // 配置sparkConf优雅的停止
                sparkConf.set("spark.streaming.stopGracefullyOnShutdown", "true");
                // 配置Spark Streaming每秒钟从kafka分区消费的最大速率
                sparkConf.set("spark.streaming.kafka.maxRatePerPartition", "100");
                // 指定Spark Streaming的序列化方式为Kryo方式
                sparkConf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
                // 指定Kryo序列化方式的注册器
                sparkConf.set("spark.kryo.registrator", "com.atguigu.registrator.MyKryoRegistrator");

                // 获取Spark Streaming的间隔时间
                String interval = serverProperties.getProperty("streaming.interval");
                final Long streamingInterval = Long.parseLong(interval);

                // 创建StreamingContext
                JavaStreamingContext javaStreamingContext = new JavaStreamingContext(sparkConf, Durations.seconds(streamingInterval));

                /*  获取kafka相关的所有配置参数 */
                final String kafkaBrokers = serverProperties.getProperty("kafka.broker.list");
                //  topic: xx1,xx2,xx3.....
                final String kafkaTopics = serverProperties.getProperty("kafka.topic");
                // xx1,xx2,..   ->  [xx1, xx2, ....]
                Set<String> kafkaTopicSet = new HashSet<>(Arrays.asList(kafkaTopics.split(",")));
                final String groupId = serverProperties.getProperty("kafka.groupId");

                Map<String, String> kafkaParam = new HashMap<>();
                kafkaParam.put("metadata.broker.list", kafkaBrokers);
                kafkaParam.put("group.id", groupId);

                final KafkaCluster kafkaCluster = getKafkaCluster(kafkaParam);
                Map<TopicAndPartition, Long> offsets = getConsumerOffsets(kafkaCluster, groupId, kafkaTopicSet);

                JavaInputDStream<String> kafkaDStream = KafkaUtils.createDirectStream(
                        javaStreamingContext,
                        String.class,
                        String.class,
                        StringDecoder.class,
                        StringDecoder.class,
                        String.class,
                        kafkaParam,
                        offsets,
                        new Function<MessageAndMetadata<String, String>, String>() {
                            @Override
                            public String call(MessageAndMetadata<String, String> v1) throws Exception {
                                return v1.message();
                            }
                        }
                );

                // 获取每个RDD里面蕴含的offsetRanges数据信息
                final AtomicReference<OffsetRange[]> offsetRanges = new AtomicReference<>();
                JavaDStream<String> kafkaOriDStream = kafkaDStream.transform(
                        new Function<JavaRDD<String>, JavaRDD<String>>() {
                            @Override
                            public JavaRDD<String> call(JavaRDD<String> rdd) throws Exception {

                                OffsetRange[] offsets = ((HasOffsetRanges)rdd.rdd()).offsetRanges();
                                offsetRanges.set(offsets);

                                return rdd;
                            }
                        }
                );

                JavaDStream<String> kafkaFilterDStream = kafkaOriDStream.filter(
                        new Function<String, Boolean>() {
                            @Override
                            public Boolean call(String message) throws Exception {

                                // 判断数据是否为三种日志数据中的一种
                                if(!message.contains("appVersion") && !message.contains("currentPage") &&
                                        !message.contains("errorMajor")){
                                    return false;
                                }

                                // 只获取三种日志数据中的启动日志
                                if(!message.contains("appVersion")){
                                    return false;
                                }

                                // 将重要字段为空的数据过滤掉
                                StartupReportLogs startupReportLogs = null;
                                startupReportLogs = JSONUtil.json2Object(message, StartupReportLogs.class);

                                if(startupReportLogs == null ||
                                        StringUtil.isEmpty(startupReportLogs.getUserId()) ||
                                        StringUtil.isEmpty(startupReportLogs.getAppId())){
                                    return false;
                                }

                                return true;
                            }
                        }
                );

                JavaPairDStream<UserCityPackageKey, Long> kafkaPairDStream = kafkaFilterDStream.mapToPair(
                        new PairFunction<String, UserCityPackageKey, Long>() {
                            @Override
                            public Tuple2<UserCityPackageKey, Long> call(String message) throws Exception {

                                StartupReportLogs startupReportLogs = JSONUtil.json2Object(message, StartupReportLogs.class);

                                UserCityPackageKey key = new UserCityPackageKey();
                                key.setCity(startupReportLogs.getCity());
                                // userId : user11xx
                                String userId = startupReportLogs.getUserId();
                                Long userIdLong = Long.parseLong(userId.substring(4));
                                key.setUserId(userIdLong);

                                Tuple2<UserCityPackageKey, Long> tuple2 = new Tuple2<>(key, 1L);

                                return tuple2;
                            }
                        }
                ).reduceByKey(
                        new Function2<Long, Long, Long>() {
                            @Override
                            public Long call(Long v1, Long v2) throws Exception {
                                return v1 + v2;
                            }
                        }
                );

                // 到此为止，我们获得了(key, count)
                // key包含city， user信息

                kafkaPairDStream.foreachRDD(
                        new VoidFunction<JavaPairRDD<UserCityPackageKey, Long>>() {
                            @Override
                            public void call(JavaPairRDD<UserCityPackageKey, Long> rdd) throws Exception {
                                rdd.foreachPartition(
                                        new VoidFunction<Iterator<Tuple2<UserCityPackageKey, Long>>>() {
                                            @Override
                                            public void call(Iterator<Tuple2<UserCityPackageKey, Long>> items) throws Exception {
                                                BehaviorStatService behaviorStatService = BehaviorStatService.getInstance(serverProperties);

                                                while(items.hasNext()){
                                                    UserCityStatModel userCityStatModel = new UserCityStatModel();

                                                    Tuple2<UserCityPackageKey, Long> tuple2 = items.next();
                                                    UserCityPackageKey key = tuple2._1();
                                                    Long count = tuple2._2();

                                                    userCityStatModel.setUserId(key.getUserId().toString());
                                                    userCityStatModel.setCity(key.getCity());
                                                    userCityStatModel.setNum(count);

                                                    behaviorStatService.addUserNumOfCity(userCityStatModel);
                                                }
                                            }
                                        });
                                offsetToZk(kafkaCluster, offsetRanges, groupId);
                            }
                        });

                return javaStreamingContext;
            }
        };
        return func;
    }

    /*
* 将offset写入zk
* */
    public static void offsetToZk(final KafkaCluster kafkaCluster,
                                  final AtomicReference<OffsetRange[]> offsetRanges,
                                  final String groupId) {
        // 遍历每一个偏移量信息
        for (OffsetRange o : offsetRanges.get()) {

            // 提取offsetRange中的topic和partition信息封装成TopicAndPartition
            TopicAndPartition topicAndPartition = new TopicAndPartition(o.topic(), o.partition());
            // 创建Map结构保持TopicAndPartition和对应的offset数据
            Map<TopicAndPartition, Object> topicAndPartitionObjectMap = new HashMap();
            // 将当前offsetRange的topicAndPartition信息和untilOffset信息写入Map
            topicAndPartitionObjectMap.put(topicAndPartition, o.untilOffset());

            // 将Java的Map结构转换为Scala的mutable.Map结构
            scala.collection.mutable.Map<TopicAndPartition, Object> testMap = JavaConversions.mapAsScalaMap(topicAndPartitionObjectMap);

            // 将Scala的mutable.Map转化为imutable.Map
            scala.collection.immutable.Map<TopicAndPartition, Object> scalatopicAndPartitionObjectMap =
                    testMap.toMap(new Predef.$less$colon$less<Tuple2<TopicAndPartition, Object>, Tuple2<TopicAndPartition, Object>>() {
                        public Tuple2<TopicAndPartition, Object> apply(Tuple2<TopicAndPartition, Object> v1) {
                            return v1;
                        }
                    });

            // 更新offset到kafkaCluster
            kafkaCluster.setConsumerOffsets(groupId, scalatopicAndPartitionObjectMap);
        }
    }

    /*
    * 获取kafka每个分区消费到的offset,以便继续消费
    * */
    public static Map<TopicAndPartition, Long> getConsumerOffsets(KafkaCluster kafkaCluster, String groupId, Set<String> topicSet) {
        // 将Java的Set结构转换为Scala的mutable.Set结构
        scala.collection.mutable.Set<String> mutableTopics = JavaConversions.asScalaSet(topicSet);
        // 将Scala的mutable.Set结构转换为immutable.Set结构
        scala.collection.immutable.Set<String> immutableTopics = mutableTopics.toSet();
        // 根据传入的分区，获取TopicAndPartition形式的返回数据
        scala.collection.immutable.Set<TopicAndPartition> topicAndPartitionSet2 = (scala.collection.immutable.Set<TopicAndPartition>)
                kafkaCluster.getPartitions(immutableTopics).right().get();


        // 创建用于存储offset数据的HashMap
        // (TopicAndPartition, Offset)
        Map<TopicAndPartition, Long> consumerOffsetsLong = new HashMap();

        // kafkaCluster.getConsumerOffsets：通过kafkaCluster的getConsumerOffsets方法获取指定消费者组合，指定主题分区的offset
        // 如果返回Left，代表获取失败，Zookeeper中不存在对应的offset，因此HashMap中对应的offset应该设置为0
        if (kafkaCluster.getConsumerOffsets(groupId, topicAndPartitionSet2).isLeft()) {

            // 将Scala的Set结构转换为Java的Set结构
            Set<TopicAndPartition> topicAndPartitionSet1 = JavaConversions.setAsJavaSet(topicAndPartitionSet2);

            // 由于没有保存offset（该group首次消费时）, 各个partition offset 默认为0
            for (TopicAndPartition topicAndPartition : topicAndPartitionSet1) {
                consumerOffsetsLong.put(topicAndPartition, 0L);
            }
        } else {
            // offset已存在, 获取Zookeeper上的offset
            // 获取到的结构为Scala的Map结构
            scala.collection.immutable.Map<TopicAndPartition, Object> consumerOffsetsTemp =
                    (scala.collection.immutable.Map<TopicAndPartition, Object>)
                            kafkaCluster.getConsumerOffsets(groupId, topicAndPartitionSet2).right().get();

            // 将Scala的Map结构转换为Java的Map结构
            Map<TopicAndPartition, Object> consumerOffsets = JavaConversions.mapAsJavaMap(consumerOffsetsTemp);

            // 将Scala的Set结构转换为Java的Set结构
            Set<TopicAndPartition> topicAndPartitionSet1 = JavaConversions.setAsJavaSet(topicAndPartitionSet2);

            // 将offset加入到consumerOffsetsLong的对应项
            for (TopicAndPartition topicAndPartition : topicAndPartitionSet1) {
                Long offset = (Long) consumerOffsets.get(topicAndPartition);
                consumerOffsetsLong.put(topicAndPartition, offset);
            }
        }

        return consumerOffsetsLong;
    }

    public static KafkaCluster getKafkaCluster(Map<String, String> kafkaParams) {
        // 将Java的HashMap转化为Scala的mutable.Map
        scala.collection.mutable.Map<String, String> testMap = JavaConversions.mapAsScalaMap(kafkaParams);
        // 将Scala的mutable.Map转化为imutable.Map
        scala.collection.immutable.Map<String, String> scalaKafkaParam =
                testMap.toMap(new Predef.$less$colon$less<Tuple2<String, String>, Tuple2<String, String>>() {
                    public Tuple2<String, String> apply(Tuple2<String, String> v1) {
                        return v1;
                    }
                });

        // 由于KafkaCluster的创建需要传入Scala.HashMap类型的参数，因此要进行上述的转换
        // 将immutable.Map类型的Kafka参数传入构造器，创建KafkaCluster
        return new KafkaCluster(scalaKafkaParam);
    }

}
