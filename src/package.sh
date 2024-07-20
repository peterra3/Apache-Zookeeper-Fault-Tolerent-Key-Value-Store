#!/bin/sh

FNAME=a3.tar.gz

tar -czf $FNAME *.java a3.thrift

echo Your tarball file name is: $FNAME
echo 
echo It contains the following files:
echo 
tar -tf $FNAME

echo
echo Good luck!
echo
