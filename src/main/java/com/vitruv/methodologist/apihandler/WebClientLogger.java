package com.vitruv.methodologist.apihandler;

import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.logstash.logback.marker.Markers;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/*
 * to see more details :
 * https://stackoverflow.com/questions/46154994/how-to-log-spring-5-webclient-call/67939497#67939497
 */
public class WebClientLogger implements ExchangeFilterFunction {
    final Logger log;

    public WebClientLogger(Class<?> clazz) {
        log = LoggerFactory.getLogger(clazz);
    }

    @Override
    public Mono<ClientResponse> filter(
            @NotNull ClientRequest request, @NotNull ExchangeFunction next) {
        /* Log request headers using 'request.headers().entrySet().stream().map(Object::toString).collect(joining(","))'*/
        BodyInserter<?, ? super ClientHttpRequest> originalBodyInserter = request.body();
        Map<String, String> logEntry = new HashMap<>();

        ClientRequest loggingClientRequest = ClientRequest.from(request).body((outputMessage, context) -> {
            ClientHttpRequestDecorator loggingOutputMessage = new ClientHttpRequestDecorator(outputMessage) {
                private final AtomicBoolean alreadyLogged = new AtomicBoolean(false);

                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    boolean needToLog = alreadyLogged.compareAndSet(false, true);
                    if (needToLog) {
                        body = DataBufferUtils.join(body).doOnNext(content -> {
                            logEntry.put("method", request.method().name());
                            logEntry.put("url", request.url().toString());
                            logEntry.put("request_body", content.toString(StandardCharsets.UTF_8));
                        });
                    }
                    return super.writeWith(body);
                }

                @Override
                /* This is for requests with no request body (e.g. GET) */
                public Mono<Void> setComplete() {
                    boolean needToLog = alreadyLogged.compareAndSet(false, true);
                    if (needToLog) {
                        logEntry.put("method", request.method().name());
                        logEntry.put("url", request.url().toString());
                    }
                    return super.setComplete();
                }
            };

            return originalBodyInserter.insert(loggingOutputMessage, context);
        }).build();
        // 'clientResponse.headers().asHttpHeaders().entrySet().stream().map(Object::toString).collect(joining(","))'
        return next.exchange(loggingClientRequest).map(clientResponse -> clientResponse.mutate().body(f ->
                        f.map(dataBuffer -> {
                            logEntry.put("type", "THIRD_API");
                            logEntry.put("status", String.valueOf(clientResponse.statusCode().value()));
                            logEntry.put("response", dataBuffer.toString(StandardCharsets.UTF_8));
                            /*
                            * maybe if we want to detect 200 response code with error
                            * searching for some error keywords would be nice, line error, Error, ...
                            */
                            if (clientResponse.statusCode().is2xxSuccessful())
                                log.info(Markers.appendEntries(logEntry), "");
                            else log.error(Markers.appendEntries(logEntry), "");
                            return dataBuffer;
                        }))
                .build());
    }
}