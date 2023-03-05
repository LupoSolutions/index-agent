#!/bin/bash

set -m

function createCollections() {
	echo "Creating collections"
	(
    solr create -c suggest -d configs/suggest
	)
}

function waitForSolr() {
    solrReady=1;
    timeToWaitForSolr=0;
    MAX_AMOUNT_OF_TIME_TO_WAIT=180

    while [[ $solrReady -ne 0 && $timeToWaitForSolr -lt $MAX_AMOUNT_OF_TIME_TO_WAIT ]]; do
        curl --fail -I http://localhost:8983/solr/#/cloud
        solrReady=$?
        sleep 1s
        echo "solr not ready after ${timeToWaitForSolr} seconds"
        timeToWaitForSolr=$((timeToWaitForSolr+1))

        if [[ "$timeToWaitForSolr" -gt $MAX_AMOUNT_OF_TIME_TO_WAIT ]]; then
            echo "Solr is not ready after $timeToWaitForSolr seconds so exiting with error code 1"
            exit 1;
        fi

    done
}

echo "starting solr"
solr start -cloud -f -s /opt/solr/server/solr -p 8983 &

waitForSolr
createCollections

fg %1

