package org.example.dubbo.rpc;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.example.dubbo.rpc.VoiceChat;
import org.example.dubbo.rpc.VoiceChatRequest;
import org.example.dubbo.rpc.VoiceChatResponse;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class VoiceChatConsumerTest {
    private static final AudioFormat.Encoding encoding = new AudioFormat.Encoding("PCM_SIGNED");
    private static final AudioFormat format = new AudioFormat(encoding, 8000, 16, 1, 2, 8000, false);//编码格式，采样率，每个样本的位数，声道，帧长（字节），帧数，是否按big-endian字节顺序存储

    private static final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(200, new NamedThreadFactory("chat-client-stream", false));

    public static void main(String[] args) throws Exception {
        ReferenceConfig<VoiceChat> ref = new ReferenceConfig<>();
        ref.setInterface(VoiceChat.class);
        ref.setProtocol("tri");
        ref.setVersion("1.0");
//        ref.setProxy("nativestub");
        ref.setUrl("tri://localhost:50051/org.example.dubbo.rpc.VoiceChat");

        ApplicationConfig applicationConfig = new ApplicationConfig("tri-stub-consumer");
        applicationConfig.setQosEnable(false);

        DubboBootstrap.getInstance()
                .application(applicationConfig)
                .reference(ref)
                .registry(new RegistryConfig("zookeeper://localhost:2181"))
                .start();

        VoiceChat voiceChat = ref.get();

        TargetDataLine targetDataLine = AudioSystem.getTargetDataLine(format);
        targetDataLine.open(format);
        targetDataLine.start();

        SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(format);
        sourceDataLine.open(format);
        sourceDataLine.start();

        for (int i = 0; i < 200; i++) {
            String callId = String.valueOf(System.currentTimeMillis());
            StreamObserver<VoiceChatRequest> requestStreamObserver = voiceChat.chat(new VoiceChatStreamObserver(sourceDataLine));
            executorService.scheduleAtFixedRate(() -> {
                byte[] buffer = new byte[1600];
                int len = targetDataLine.read(buffer, 0, buffer.length);
                VoiceChatRequest request = VoiceChatRequest.newBuilder()
                        .setCallId(callId).setData(ByteString.copyFrom(buffer))
                        .build();
                requestStreamObserver.onNext(request);
            }, 100, 100, TimeUnit.MILLISECONDS);
            Thread.sleep(100L);
        }
    }

    private static class VoiceChatStreamObserver implements StreamObserver<VoiceChatResponse> {

        SourceDataLine sourceDataLine;
        int n = 0;

        public VoiceChatStreamObserver(SourceDataLine sourceDataLine) {
            this.sourceDataLine = sourceDataLine;
        }

        @Override
        public void onNext(VoiceChatResponse response) {
            n++;
            if (n % 10 == 0) {
                log.info("callId: {} : reply data size: {}", response.getCallId(), response.getData().size());
            }
            byte[] bytes = response.getData().toByteArray();
            sourceDataLine.write(bytes, 0, bytes.length);
        }

        @Override
        public void onError(Throwable t) {
            log.error("voice chat onError", t);
        }

        @Override
        public void onCompleted() {
            log.info("voice chat completed");
        }
    }

}
