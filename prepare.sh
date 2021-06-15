gzip -cd $1 | while read line; do echo $(echo $line | tr ' ' '\n' | uniq); done | gzip > specs/cur.gz
