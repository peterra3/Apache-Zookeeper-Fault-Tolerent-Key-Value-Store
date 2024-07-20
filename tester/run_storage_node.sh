#!/bin/bash

#
# Wojciech Golab, 2016
#

source settings.sh

if [ "$#" -ne 6 ]; then
    echo "Usage: $0 node_num zkstring zknode zkbarriernode failure_injection_interval kill_primary"
    exit -1
fi

NODE=$1
ZKSTRING=$2
ZKNODE=$3
ZKNODE_BARRIER=$4
INTERVAL=$5
KILL_PRIMARY=$6

LINENUM=`expr $NODE + 1`
SHOST=`head -n $LINENUM a3.config | tail -1 | cut -d' ' -f1`
SPORT=`head -n $LINENUM a3.config | tail -1 | cut -d' ' -f2`

if [ $KILL_PRIMARY -eq 1 ]; then
    echo Killing primary only in this run
    timeout 3600 ./run_storage_node_sub_killprimary.sh $NODE $SHOST $SPORT $ZKSTRING $ZKNODE $ZKNODE_BARRIER $INTERVAL &
else
    echo Killing backup only in this run
    timeout 3600 ./run_storage_node_sub_killbackup.sh $NODE $SHOST $SPORT $ZKSTRING $ZKNODE $ZKNODE_BARRIER $INTERVAL &
fi
