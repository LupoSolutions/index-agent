package com.lupo.indexagent.spring;

import java.util.ArrayList;
import java.util.List;

import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

import com.google.gson.Gson;

@SpringBootApplication
@ComponentScan(basePackages = "com.lupo")
@PropertySources({@PropertySource("classpath:/config/default.properties")})
public class IndexAgentApplication extends SpringBootServletInitializer {

    @Value("${SOLRCLOUD_BASE_URL}")
    private String solrCloudBaseUrl;

    public static void main(String[] args) {
        SpringApplication.run(IndexAgentApplication.class,
                              args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(IndexAgentApplication.class);
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }

    @Bean
    public CloudSolrClient solrClient() {
        final List<String> solrUrls = new ArrayList<>();
        solrUrls.add(solrCloudBaseUrl);
        return new CloudSolrClient.Builder(solrUrls).build();
    }
}
