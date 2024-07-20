#!/bin/bash

#
# Wojciech Golab, 2016
#

source settings.sh

CODEPATH=`pwd`
if [ "$#" -ne 7 ]; then
    echo "Usage: $0 node_num host port zkstring zknode zkbarriernode failure_injection_interval"
    exit -1
fi

NODE=$1
SHOST=$2
SPORT=$3
ZKSTRING=$4
ZKNODE=$5
ZKNODE_BARRIER=$6
INTERVAL=$7

# Start the storage nodes

if [ "$NODE" -eq 0 ]; then
    echo --- Node $NODE started primary at `date`
    taskset -c 0-7 $JAVA_HOME/bin/java -cp .:gen-java:"lib/*" StorageNode $SHOST $SPORT $ZKSTRING $ZKNODE &>> output-$SHOST:$SPORT.txt &
    SNPID=$!
else
    # Start second so it becomes the backup
    VAR=0
    # Wait for primary to create its znode
    while [ $VAR -eq 0 ]; do
	echo --- Node $NODE querying ZK at `date`
    taskset -c 0-7 $JAVA_HOME/bin/java -cp .:gen-java:"lib/*" QueryZNode $ZKSTRING $ZKNODE #&> /dev/null
	VAR=$?
    done
    echo --- Node $NODE started backup at `date`
    taskset -c 0-7 $JAVA_HOME/bin/java -cp .:gen-java:"lib/*" StorageNode $SHOST $SPORT $ZKSTRING $ZKNODE &>> output-$SHOST:$SPORT.txt &
    SNPID=$!
fi

sleep $INTERVAL

# Barrier with benchmark client
echo --- Node $NODE reached barrier at `date`
taskset -c 0-7 $JAVA_HOME/bin/java -cp .:gen-java:"lib/*" GraderBarrier $ZKSTRING $ZKNODE_BARRIER 3

sleep $INTERVAL

# Begin failure injection
for i in `seq 1 25`;
do
    # Restart node 0, which is initially the primary
    if [ "$NODE" -eq 0 ]; then
	kill -9 $SNPID
	#sleep 1
	echo --- Node $NODE killed at `date`
	sleep $INTERVAL
	#WG killall -s 9 java
	#WG sleep 1
    taskset -c 0-7 $JAVA_HOME/bin/java -cp .:gen-java:"lib/*" StorageNode $SHOST $SPORT $ZKSTRING $ZKNODE &>> output-$SHOST:$SPORT.txt &
	SNPID=$!
	echo --- Node $NODE restarted at `date`
	sleep $INTERVAL
    fi
    # Synchronize
    echo --- Node $NODE reached barrier at `date`
    taskset -c 0-7 $JAVA_HOME/bin/java -cp .:gen-java:"lib/*" GraderBarrier $ZKSTRING $ZKNODE_BARRIER 2
    # Restart node 1, which is initially the backup
    if [ "$NODE" -eq 1 ]; then
	kill -9 $SNPID
	#sleep 1
	echo --- Node $NODE killed at `date`
	sleep $INTERVAL
	#WG killall -s 9 java
	#WG sleep 1
    taskset -c 0-7 $JAVA_HOME/bin/java -cp .:gen-java:"lib/*" StorageNode $SHOST $SPORT $ZKSTRING $ZKNODE &>> output-$SHOST:$SPORT.txt &
	SNPID=$!
	echo --- Node $NODE restarted at `date`
	sleep $INTERVAL
    fi
    # Synchronize
    echo --- Node $NODE reached barrier at `date`
    taskset -c 0-7 $JAVA_HOME/bin/java -cp .:gen-java:"lib/*" GraderBarrier $ZKSTRING $ZKNODE_BARRIER 2
done    


# finally kill the storage node
echo --- Node $NODE killing process $SNPID at `date`
kill -9 $SNPID
sleep 2
#WG killall -s 9 java
#WG sleep 1
