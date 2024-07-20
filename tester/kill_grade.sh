#!/bin/bash

killall nohup grade.sh one_grade.sh timeout java
sleep 1
killall -s 9 nohup grade.sh one_grade.sh timeout java
sleep 1
./kill_storage_nodes.sh
sleep 1

fg

ps -u $USER
