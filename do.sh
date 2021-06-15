#!/bin/bash

for i in `seq 1 4`; do
	#mkdir -p out/$1/general/$i
	#rm -rf out/$1/general/$i/*
	#if [ "$(ls -A out/$1/general/$i)" ]; then
		#echo "Not empty directory: out/$1/general/$i" >> errors
	#else 
		#echo -n "timeout 10m ./fbsat infer cegis-min -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 -o out/$1/general/$i | tee out/$1/general/$i/logs; " | tee -a execution
	#fi
	#timeout 10m ./fbsat infer cegis-min -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 -o out/$1/general/$i | tee out/$1/general/$i/logs

	#mkdir -p out/$1/assumptions/$i
	#rm -rf out/$1/assumptions/$i/*
	#if [ "$(ls -A out/$1/assumptions/$i)" ]; then
		#echo "Not empty directory: out/$1/assumptions/$i" >> errors
	#else 
		#echo -n "timeout 10m ./fbsat infer cegis-min-assumptions -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/assumptions/$i | tee out/$1/assumptions/$i/logs; " | tee -a execution
	#fi
	#timeout 10m ./fbsat infer cegis-min-assumptions -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/assumptions/$i | tee out/$1/assumptions/$i/logs

	#mkdir -p out/$1/cut-tree/$i
	#rm -rf out/$1/cut-tree/$i/*
	#if [ "$(ls -A out/$1/cut-tree/$i)" ]; then
		#echo "Not empty directory: out/$1/cut-tree/$i" >> errors
	#else 
		#echo -n "timeout 10m ./fbsat infer cegis-min-cut-tree -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/cut-tree/$i --loopless-counter-examples | tee out/$1/cut-tree/$i/logs; " | tee -a execution
	#fi
	#timeout 10m ./fbsat infer cegis-min-cut-tree -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/cut-tree/$i | tee out/$1/cut-tree/$i/logs

	#mkdir -p out/$1/height/$i
	#rm -rf out/$1/height/$i/*
	#if [ "$(ls -A out/$1/height/$i)" ]; then
	#	echo "Not empty directory: out/$1/height/$i" >> errors
	#else 
	#	echo -n "timeout 10m ./fbsat infer cegis-min-height -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/height/$i --loopless-counter-examples | tee out/$1/height/$i/logs; " | tee -a execution
	#fi
	#timeout 10m ./fbsat infer cegis-min-height -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/height/$i | tee out/$1/height/$i/logs

	#mkdir -p out/$1/general1/$i
	#rm -rf out/$1/general1/$i/*
	#if [ "$(ls -A out/$1/general1/$i)" ]; then
	#	echo "Not empty directory: out/$1/general1/$i" >> errors
	#else 
	#	echo -n "timeout 10m ./fbsat infer cegis-min -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 -o out/$1/general1/$i | tee out/$1/general1/$i/logs; " | tee -a execution
	#fi
	#timeout 10m ./fbsat infer cegis-min -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 -o out/$1/general1/$i | tee out/$1/general1/$i/logs

	#mkdir -p out/$1/cegisic/$i
	#rm -rf out/$1/cegisic/$i/*
	#if [ "$(ls -A out/$1/cegisic/$i)" ]; then
	#	echo "Not empty directory: out/$1/cegisic/$i" >> errors
	#else 
	#	echo -n "timeout 10m ./fbsat infer cegisic-min -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/cegisic/$i --loopless-counter-examples | tee out/$1/cegisic/$i/logs; " | tee -a execution
	#fi
	#timeout 10m ./fbsat infer cegisic-min -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/cegisic/$i | tee out/$1/cegisic/$i/logs

	#mkdir -p out/$1/height1/$i
	#rm -rf out/$1/height1/$i/*
	#if [ "$(ls -A out/$1/height1/$i)" ]; then
	#	echo "Not empty directory: out/$1/height1/$i" >> errors
	#else 
	#	echo -n "timeout 10m ./fbsat infer cegis-min-height -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/height1/$i --loopless-counter-examples | tee out/$1/height1/$i/logs; " | tee -a execution
	#fi
	#timeout 10m ./fbsat infer cegis-min-height -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/height/$i | tee out/$1/height/$i/logs

	#mkdir -p out/$1/assumptions1/$i
	#rm -rf out/$1/assumptions1/$i/*
	#if [ "$(ls -A out/$1/assumptions1/$i)" ]; then
	#	echo "Not empty directory: out/$1/assumptions1/$i" >> errors
	#else 
	#	echo -n "timeout 10m ./fbsat infer cegis-min-assumptions -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/assumptions1/$i | tee out/$1/assumptions1/$i/logs; " | tee -a execution
	#fi
	#timeout 10m ./fbsat infer cegis-min-height -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/height/$i | tee out/$1/height/$i/logs

	mkdir -p out/$1/ass-bounded/$i
	rm -rf out/$1/ass-bounded/$i/*
	if [ "$(ls -A out/$1/ass-bounded/$i)" ]; then
		echo "Not empty directory: out/$1/ass-bounded/$i" >> errors
	else 
		echo -n "timeout 10m ./fbsat infer cegis-min-bounded -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/ass-bounded/$i --loopless-counter-examples | tee out/$1/ass-bounded/$i/logs; " | tee -a execution
	fi
	#timeout 10m ./fbsat infer cegis-min-bounded -i data/$1 --glucose --debug --solver-seed $i --solver-rnd-freq 0.1 --use-assumptions -o out/$1/ass-bounded/$i | tee out/$1/ass-bounded/$i/logs
done
