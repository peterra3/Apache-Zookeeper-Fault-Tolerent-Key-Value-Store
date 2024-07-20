#!/bin/bash

#
# Wojciech Golab, 2016
#

CMDS="run_storage_node.sh run_storage_node_sub_killprimary.sh run_storage_node_sub_killbackup.sh java"
killall -u $USER $CMDS
sleep 1
killall -s 9 -u $USER $CMDS
sleep 1