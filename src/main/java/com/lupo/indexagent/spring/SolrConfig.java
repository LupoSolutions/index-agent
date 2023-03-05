package com.lupo.indexagent.spring;

import java.util.ArrayList;
import java.util.List;

import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SolrConfig {
    @Value("${SOLRCLOUD_BASE_URL}")
    private String solrCloudBaseUrl;

    @Bean
    public CloudSolrClient cloudSolrClient() {
        final List<String> solrUrls = new ArrayList<>();
        solrUrls.add(solrCloudBaseUrl);
        return new CloudSolrClient.Builder(solrUrls).build();
    }
}
