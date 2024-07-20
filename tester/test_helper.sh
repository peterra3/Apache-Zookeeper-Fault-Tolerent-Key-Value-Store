#!/bin/bash

#
# Wojciech Golab, 2016
#


HEAPGB=12g

source ../settings.sh

JAVA_CC=$JAVA_HOME/bin/javac
JAVA_JVM=$JAVA_HOME/bin/java
unset JAVA_TOOL_OPTIONS

LOGS=logs_student
rm -fr $LOGS > /dev/null


echo --- Unpacking | tee -a $LOGS
tar -zxf "$1"
echo --- Found `ls *.java` | tee -a $LOGS

echo --- Cleaning | tee -a $LOGS
rm -f *.jar
rm -f *.class
rm -rf ./lib
cp -R ../lib .
cp ../*.properties .

echo --- Compiling | tee -a $LOGS
$THRIFT_CC --gen java:generated_annotations=suppress a3.thrift 2>&1 | tee -a $LOGS
$JAVA_CC gen-java/*.java -cp .:"lib/*" 2>&1 | tee -a $LOGS
$JAVA_CC *.java -cp .:gen-java/:"lib/*" 2>&1 | tee -a $LOGS

if [ $? -ne 0 ]; then
    echo Unable to build solution! | tee -a $LOGS
    exit
fi

# First build the test driver.
echo Building test driver | tee -a $LOGS

cp ../A3Client.java .
$JAVA_CC -cp .:gen-java/:"lib/*" A3Client.java 2>&1 | tee -a $LOGS

if [ $? -ne 0 ]; then
	echo Unable to compile client! | tee -a $LOGS
	exit
fi

echo Building barrier utility
cp ../GraderBarrier.java .
$JAVA_CC -cp .:gen-java/:"lib/*" GraderBarrier.java

echo Building node query utility
cp ../QueryZNode.java .
$JAVA_CC -cp .:gen-java/:"lib/*" QueryZNode.java

echo --- Running | tee -a $LOGS
OUTPUT=output_student
rm -fr $OUTPUT

echo > "$2"
echo rep,case,duration,num_threads,keyspace_size,throughput,latency,num_lin_viol_1,num_lin_viol_2,oneway >> "$2"

ZKSTRING=manta.uwaterloo.ca:2181
ZKNODE=/${USER}_a3student_znode
ZKNODE_BARRIER=${ZKNODE}_barrier

# Case 0: easy, kill primary
KILLPRIMARY[0]=1
INTERVAL[0]=5
DURATION[0]=90
THREADS[0]=4
KEYSPACE_SIZE[0]=100

# Case 1: easy, kill backup
KILLPRIMARY[1]=0
INTERVAL[1]=5
DURATION[1]=90
THREADS[1]=4
KEYSPACE_SIZE[1]=100

# Case 2: harder, kill primary
KILLPRIMARY[2]=1
INTERVAL[2]=4
DURATION[2]=90
THREADS[2]=4
KEYSPACE_SIZE[2]=1000

# Case 3: even harder, kill primary
KILLPRIMARY[3]=1
INTERVAL[3]=4
DURATION[3]=90
THREADS[3]=4
KEYSPACE_SIZE[3]=10000

# Case 4: harder still, kill primary
KILLPRIMARY[4]=1
INTERVAL[4]=4
DURATION[4]=90
THREADS[4]=4
KEYSPACE_SIZE[4]=100000


cp ../a3.config .
cp ../settings.sh .
cp ../run_storage_*.sh .
cp ../kill_storage_nodes.sh .

mkdir lintest_input
mkdir lintest_output

rm -f driver.logs > /dev/null

# check if group is using oneway RPCs
grep oneway a3.thrift
RET=$?
if [ $RET -eq 1 ]; then
    ONEWAY=0
else
    ONEWAY=1
fi

for CASE in `seq 0 4`; do
    echo --- Case: $CASE | tee -a $LOGS
    for REP in `seq 0 0`; do
	echo --- Repetition: $REP | tee -a $LOGS
    	echo Cleaning up last iteration  | tee -a $LOGS
	./kill_storage_nodes.sh       	
	
	echo Cleaning ZK node  | tee -a $LOGS
	$ZKPATH/bin/zkCli.sh -server $ZKSTRING rmr $ZKNODE > /dev/null
	$ZKPATH/bin/zkCli.sh -server $ZKSTRING rmr $ZKNODE_BARRIER > /dev/null
	sleep 1
	$ZKPATH/bin/zkCli.sh -server $ZKSTRING create $ZKNODE "" > /dev/null

	echo Starting storage node 0 | tee -a $LOGS
	./run_storage_node.sh 0 $ZKSTRING $ZKNODE $ZKNODE_BARRIER ${INTERVAL[$CASE]} ${KILLPRIMARY[$CASE]}
	echo Starting storage node 1 | tee -a $LOGS
	./run_storage_node.sh 1 $ZKSTRING $ZKNODE $ZKNODE_BARRIER ${INTERVAL[$CASE]} ${KILLPRIMARY[$CASE]}
	echo Done starting storage nodes, waiting on barrier  | tee -a $LOGS
	taskset -c 0-7 java -cp .:gen-java:"lib/*" GraderBarrier $ZKSTRING $ZKNODE_BARRIER 3 &> /dev/null

	echo Starting client at `date`  | tee -a $LOGS
	rm -f grader_execution.log
	rm -f grader_scores.log
	rm -f $OUTPUT
	CMD="A3Client $ZKSTRING $ZKNODE ${THREADS[$CASE]} ${DURATION[$CASE]} ${KEYSPACE_SIZE[$CASE]}"
	echo Command: $CMD
	TO=`expr ${DURATION[$CASE]} + 120`
	timeout $TO taskset -c 0-7 $JAVA_JVM -cp .:gen-java/:"lib/*" $CMD 2> $OUTPUT.errors > $OUTPUT

	echo Client\'s standard output:>> $LOGS
	cat $OUTPUT >> $LOGS
	echo Client\'s standard error: >> $LOGS
	cat $OUTPUT.errors >> $LOGS
	
	cat output-* >> $LOGS

	ERR=`cat $OUTPUT.errors | tr ',' ' '`
	echo Errors: $ERR
	echo Client done at `date`
	THROUGHPUT=`cat $OUTPUT | grep throughput | cut -d' ' -f3`
	LATENCY=`cat $OUTPUT | grep latency | cut -d' ' -f3`
	echo Measured throughput $THROUGHPUT and latency $LATENCY

	echo Killing storage nodes | tee -a $LOGS
	./kill_storage_nodes.sh

	echo Analyzing linearizability  | tee -a $LOGS
	rm -fr lintest_input/*
	rm -fr lintest_output/*
	cp grader_execution.log lintest_input/
	taskset -c 0-7 $JAVA_JVM -cp .:"lib/*" -Xmx${HEAPGB} ca.uwaterloo.watca.LinearizabilityTest lintest_input/ lintest_output/  > ${OUTPUT}_watca
	cat lintest_output/scores* > grader_scores.txt
	NUM_VIOL_1=`cat grader_scores.txt | grep 'Score = 1' | wc -l`
	NUM_VIOL_2=`cat grader_scores.txt | grep 'Score = 2' | wc -l`
	echo Num Score = 1: $NUM_VIOL_1  | tee -a $LOGS
	echo Num Score = 2: $NUM_VIOL_2  | tee -a $LOGS

	echo $REP,$CASE,${DURATION[$CASE]},${THREADS[$CASE]},${KEYSPACE_SIZE[$CASE]},$THROUGHPUT,$LATENCY,$NUM_VIOL_1,$NUM_VIOL_2,$ONEWAY >> "$2"
    done
done

echo
cat "$2"


echo
echo "Feedback file:"
cat "$2"

LOGFILEBASE=`basename "$2" .txt`
LOGFILE="$LOGFILEBASE.logs"

echo
echo "Log file name:" $LOGFILE

truncate -s 1M $LOGS

cp $LOGS `dirname "$2"`/"$LOGFILE"

echo
echo "Done grading $2"
echo