package com.lupo.indexagent.listener;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lupo.akkapubsubconnector.listener.PubSubManager;
import com.lupo.akkapubsubconnector.listener.PubSubSource;
import com.lupo.indexagent.model.Message;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorAttributes;
import akka.stream.FlowShape;
import akka.stream.Supervision;
import akka.stream.Supervision.Directive;
import akka.stream.UniformFanInShape;
import akka.stream.UniformFanOutShape;
import akka.stream.alpakka.google.GoogleAttributes;
import akka.stream.alpakka.google.GoogleSettings;
import akka.stream.alpakka.googlecloud.pubsub.AcknowledgeRequest;
import akka.stream.alpakka.googlecloud.pubsub.PubSubConfig;
import akka.stream.alpakka.googlecloud.pubsub.ReceivedMessage;
import akka.stream.alpakka.googlecloud.pubsub.javadsl.GooglePubSub;
import akka.stream.alpakka.solr.SolrUpdateSettings;
import akka.stream.alpakka.solr.WriteMessage;
import akka.stream.alpakka.solr.WriteResult;
import akka.stream.alpakka.solr.javadsl.SolrFlow;
import akka.stream.javadsl.Balance;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Source;

@Component
public class Listener {
    private static final Logger LOG = LoggerFactory.getLogger(Listener.class);
    Function<Message, SolrInputDocument> messageToSolrInputDoc = message -> {
        final SolrInputDocument solrInputDocument = new SolrInputDocument();

        final Map<String, Object> messageAsMap = message.getMessageAsMap();

        for (Map.Entry<String, Object> entry : messageAsMap
                .entrySet()) {
            solrInputDocument.addField(entry.getKey(),
                                       entry.getValue());
        }
        return solrInputDocument;
    };


    @Value("${PARALLELISM}")
    private int parallelism;
    @Value("${SOLRCLOUD_BASE_URL}")
    private String solrCloudBaseUrl;
    @Value("${SOLR_COLLECTION_NAME}")
    private String solrCollectionName;
    @Autowired
    private PubSubSource pubSubSource;
    @Autowired
    private PubSubManager pubSubManager;

    @Autowired
    private CloudSolrClient cloudSolrClient;

    @Autowired
    private Gson gson;

    @Value("${PUBSUB_SUBSCRIPTION}")
    private String subscriptionIn;

    @Value("${PUBSUB_PROJECT_ID}")
    private String projectId;

    //TODO: Move code into akka-pubsub-connector lib
    @PostConstruct
    public void start() {
        final ActorSystem actorSystem = ActorSystem.create();
        final PubSubConfig pubSubConfig = this.pubSubManager.createPubSubConfig();

        final Source<ReceivedMessage, NotUsed> pubSubSource = this.pubSubSource.createSource(pubSubConfig);
        final Flow<ReceivedMessage, List<WriteMessage<Message, Message>>, NotUsed> parallelConversion = makeFlowParallel(conversionFlow());

        final RunnableGraph<CompletionStage<Done>> runnableGraph = pubSubSource.via(parallelConversion)
                                                                               .via(SolrFlow.typedsWithPassThrough(this.solrCollectionName,
                                                                                                                   SolrUpdateSettings.create()
                                                                                                                                     .withCommitWithin(5),
                                                                                                                   messageToSolrInputDoc,
                                                                                                                   cloudSolrClient,
                                                                                                                   Message.class))
                                                                               .async()
                                                                               .via(extractAcknowledgmentId())
                                                                               .map(AcknowledgeRequest::create)
                                                                               .toMat(GooglePubSub.acknowledge(this.subscriptionIn,
                                                                                                               pubSubConfig),
                                                                                      Keep.right());

        runGraph(runnableGraph,
                 actorSystem);


    }

    private void runGraph(final RunnableGraph<CompletionStage<Done>> runnableGraph,
                          final ActorSystem actorSystem) {
        final GoogleSettings defaultSettings = GoogleSettings.create(actorSystem);
        final GoogleSettings customSettings = defaultSettings.withProjectId(this.projectId);

        final akka.japi.function.Function<Throwable, Directive> supervisorStrategy = streamSupervisorStrategy();

        final RunnableGraph<CompletionStage<Done>> withCustomSupervision =
                runnableGraph.withAttributes(ActorAttributes.withSupervisionStrategy(supervisorStrategy))
                             .addAttributes(GoogleAttributes.settings(customSettings));

        withCustomSupervision.run(actorSystem);
    }

    private akka.japi.function.Function<Throwable, Directive> streamSupervisorStrategy() {
        return exception -> (Directive) Supervision.resume();
    }

    private Flow<List<WriteResult<Message, Message>>, List<String>, NotUsed> extractAcknowledgmentId() {
        return Flow.<List<WriteResult<Message, Message>>>create()
                   .map(messageResults -> messageResults.stream()
                                                        .map(this::extractAcknowledgmentId)
                                                        .collect(Collectors.toList()));
    }

    private String extractAcknowledgmentId(final WriteResult<Message, Message> result) {
        if (result.status() != 0) {
            LOG.info("Failed to write message to Solr {}",
                     result.passThrough());
            return "";
        }
        LOG.info("AcknowledgmentId: {} ready to send to pubsub",
                 result.passThrough()
                       .getPubSubAcknowledgmentId());
        return result.passThrough()
                     .getPubSubAcknowledgmentId();
    }

    private Flow<ReceivedMessage, List<WriteMessage<Message, Message>>, NotUsed> conversionFlow() {
        return Flow.of(ReceivedMessage.class)
                   .via(convertToMessage())
                   .via(convertToWriteMessage())
                   .groupedWithin(5,
                                  Duration.ofSeconds(1));
    }

    private Flow<ReceivedMessage, List<WriteMessage<Message, Message>>, NotUsed> makeFlowParallel(final Flow<ReceivedMessage,
            List<WriteMessage<Message, Message>>,
            NotUsed> flow) {
        return Flow.fromGraph(
                GraphDSL.create((builder) -> {
                    final UniformFanInShape<List<WriteMessage<Message, Message>>, List<WriteMessage<Message, Message>>> mergeShape
                            = builder.add(Merge.create(parallelism));
                    final UniformFanOutShape<ReceivedMessage, ReceivedMessage> dispatchShape = builder.add(Balance.create(parallelism));

                    for (int i = 0; i < parallelism; i++) {
                        builder.from(dispatchShape.out(i))
                               .via(builder.add(flow.async()))
                               .toInlet(mergeShape.in(i));
                    }
                    return FlowShape.of(dispatchShape.in(),
                                        mergeShape.out());
                })

        );
    }

    private Flow<Message, WriteMessage<Message, Message>, NotUsed> convertToWriteMessage() {
        return Flow.of(Message.class)
                   .map(message -> WriteMessage.createUpsertMessage(message)
                                               .withPassThrough(message));
    }
    private Flow<ReceivedMessage, Message, NotUsed> convertToMessage() {
        return Flow.of(ReceivedMessage.class)
                   .map(receivedMessage -> {
                       final byte[] messageBytes = Base64.getDecoder()
                                                         .decode(receivedMessage.message()
                                                                                .data()
                                                                                .get());
                       final String decodedMessage = new String(messageBytes);

                       final Map<String, Object> messageAsMap = gson.fromJson(decodedMessage,
                                                                              new TypeToken<>() {
                                                                              });
                       LOG.info("Converting Message: {} AckId: {}",
                                decodedMessage,
                                receivedMessage.ackId());
                       return new Message(receivedMessage.ackId(),
                                          messageAsMap);
                   });
    }
}
