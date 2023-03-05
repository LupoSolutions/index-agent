############################################
## Using zookeeper base image to get zkcli
############################################
FROM docker.io/zookeeper:3.8
FROM docker.io/solr:7.6
COPY --from=0 /apache-zookeeper-3.8.0-bin/ /apache-zookeeper-3.8.0-bin
USER root

#############################################################
## Installing jq to use to create collections in entrypoint
#############################################################
RUN apt-get update
RUN echo | apt-get install jq
ADD entrypoint.sh /entrypoint.sh
RUN chmod 777 /entrypoint.sh
RUN apt-get update && yes | apt-get install host
USER solr
ADD solr.xml /opt/solr/server/solr/solr.xml

####################################
## Setting up scp-config-suggest
####################################
RUN mkdir -p configs/suggest
ADD schema.xml configs/suggest
ADD solrconfig.xml configs/suggest


ENTRYPOINT ["/entrypoint.sh"]
