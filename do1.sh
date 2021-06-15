#!/bin/bash

rm execution
touch execution
rm errors
touch errors
#for len in `seq 18 20`; do
for len in `seq 20 20`; do
	for traces_count in `seq 10`; do
	#for traces_count in `seq 5 10`; do
		for count in `seq 1 4`; do
			printf -v file 'gen/traces_%02d_%02d_%02d.gz' "${traces_count}" "${len}" "${count}"
			printf -v file_smv 'gen_smv/traces_%02d_%02d_%02d.smvout' "${traces_count}" "${len}" "${count}"
			./do.sh $file
		done
	done
done
