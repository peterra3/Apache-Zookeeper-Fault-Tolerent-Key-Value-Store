#!/bin/bash

#
# Wojciech Golab, 2016-2020
#

SUBDIR=subs
FILTER="*.tar.gz"
FEEDDIR=`pwd`/feed
TMPDIR=`pwd`/tmp

rm -fr $TMPDIR
rm -fr $FEEDDIR/*
mkdir -p $FEEDDIR

CURDIR=$(pwd)

FILES=`pwd`/files.txt
rm $FILES 2> /dev/null
ls -rt $SUBDIR/$FILTER > $FILES

IFS=$'\n'       
set -f          
for f in $(cat $FILES)
do
    cd $CURDIR
    echo "Processing $f"
    while [ -e $TMPDIR ] ; do
	rm -fr $TMPDIR
    done
    mkdir -p $TMPDIR
    cp "$f" $TMPDIR
    cd $TMPDIR
    echo "$PWD"
    
    GRNUM=`echo "$f" | cut -d' ' -f5 | tr '/' '_'`
    FEEDBACK=$FEEDDIR/`basename "$f" .tar.gz`.txt
    rm -f $FEEDBACK
    echo timeout 3600 ../test_helper.sh "`basename "$f"`" "$FEEDBACK"
    timeout 3600 ../test_helper.sh "`basename "$f"`" "$FEEDBACK"
done
rm $FILES
rm -fr $TMPDIR

cd $CURDIR
