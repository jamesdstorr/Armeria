package com.jamesstorr.armeria.config;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.HttpService;

import com.linecorp.armeria.server.ServiceRequestContext;

import com.linecorp.armeria.server.tomcat.TomcatService;
import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import com.linecorp.armeria.spring.ArmeriaServerConfigurator;

@Configuration
public class ArmeriaConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ArmeriaConfiguration.class);

    public static Connector getConnector(ServletWebServerApplicationContext applicationContext) {
        final TomcatWebServer container = (TomcatWebServer) applicationContext.getWebServer();

        // Start the container to make sure all connectors are available.
        container.start();
        return container.getTomcat().getConnector();
    }

    @Bean
    public TomcatService tomcatService(ServletWebServerApplicationContext applicationContext) {
        return TomcatService.of(getConnector(applicationContext));
    }

    @Bean
    public ArmeriaServerConfigurator armeriaServiceInitializer(TomcatService tomcatService) {
        return sb -> sb.serviceUnder("/", tomcatService).decorator(this::logRequestAndResponse);
    }


    private HttpService logRequestAndResponse(HttpService delegate) {
        return (ServiceRequestContext ctx, HttpRequest req) -> {
            StringBuilder logStringBuilder = new StringBuilder()
                    .append("Request Method: ").append(req.method())
                    .append(", Request URI: ").append(req.uri())
                    .append(", Request Headers: ").append(req.headers())
                    .append(", Request Params: ").append(ctx.query());
            logger.info(logStringBuilder.toString());

            HttpResponse response = delegate.serve(ctx, req);
            response.whenComplete().thenAccept(log -> {
                RequestLog requestLog = ctx.log().ensureComplete();
                String responseLog = new StringBuilder()
                        .append("Response Status: ").append(requestLog.responseHeaders().status())
                        .append(", Response Headers: ").append(requestLog.responseHeaders())
                        .append(", Response Content: ").append(requestLog.responseContent())
                        .toString();

                // Log the response
                logger.info(responseLog);
            });
            return response;
        };
    }
}