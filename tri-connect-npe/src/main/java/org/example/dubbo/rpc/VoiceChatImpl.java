package org.example.dubbo.rpc;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.StatusRpcException;
import org.apache.dubbo.rpc.TriRpcStatus;

/**
 * @author chaoyoung
 * @date 2023/8/14
 * @since
 */
@DubboService(protocol = "tri", interfaceClass = VoiceChat.class, version = "1.0"/*, path = "org.example.dubbo.rpc.VoiceChat"*/)
@Slf4j
public class VoiceChatImpl extends DubboVoiceChatTriple.VoiceChatImplBase {

    @Override
    public StreamObserver<VoiceChatRequest> chat(StreamObserver<VoiceChatResponse> responseObserver) {
        log.info("接收到新的请求");
        return new StreamObserver<VoiceChatRequest>() {
            int n = 0;
            String callId;

            @Override
            public void onNext(VoiceChatRequest request) {
                n++;
                callId = request.getCallId();
                byte[] data = request.getData().toByteArray();
                if (n % 10 == 0) {
                    log.info("callId: {} : data size: {}", callId, data.length);
                }
                responseObserver.onNext(VoiceChatResponse.newBuilder()
                        .setCallId(callId).setData(ByteString.copyFrom(data))
                        .build());
                if (n == 10000000) {
                    log.info("server on completed");
                    responseObserver.onCompleted();
                }
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof StatusRpcException) {
                    TriRpcStatus triRpcStatus = ((StatusRpcException) t).getStatus();
                    if (TriRpcStatus.Code.CANCELLED.equals(triRpcStatus.code)) {
                        log.warn("callId: {} : client cancelled. triRpcStatus: {}", callId, triRpcStatus.toMessageWithoutCause());
                        return;
                    }
                }
                log.error(String.format("callId: %s : voice chat error", callId), t);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                log.info("callId: {} : voice chat onCompleted", callId);
                responseObserver.onCompleted();
            }
        };
    }

}
