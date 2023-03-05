#!/bin/bash

set -m

gcloud config set project ${PUBSUB_PROJECT_ID}

gcloud beta emulators pubsub start --host-port=${PUBSUB_LISTEN_ADDRESS} &

sleep 15s
#################
# Create Topics
#################
IFS=',' read -r -a topicsArray <<< "$TOPICS_TO_CREATE"

for element in "${topicsArray[@]}"
do
  echo "$element"
  curl -X PUT ${PUBSUB_LISTEN_ADDRESS}/v1/projects/${PUBSUB_PROJECT_ID}/topics/$element

done

########################
# Create Subscriptions
########################

IFS=',' read -r -a topicsAndSubs <<< "$SUBSCRIPTIONS_TO_CREATE"
for element in "${topicsAndSubs[@]}"
do
  IFS=':' read -r -a topicAndSub <<< "$element"
    echo "here is the value topic ${topicAndSub[0]}"
        echo "here is the value subscription ${topicAndSub[1]}"

    echo {\'topic\':\'projects/${PUBSUB_PROJECT_ID}/topics/${topicAndSub[0]}\'} > /tmp/data; curl -H 'content-type: application/json' -X PUT -d @/tmp/data 0.0.0.0:8432/v1/projects/${PUBSUB_PROJECT_ID}/subscriptions/${topicAndSub[1]}
done



fg %1
