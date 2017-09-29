#!/usr/bin/env bash

FILENAME=$1
STRING=$2

${STRING} | sed -e 's/[$][{]/\\u0024\\u007B/g' -e's/[}]["]/\\u007D"/g' -e 's/[e]/\\u0065/g' -e 's/[t]/\\u0074/g' -e 's/[m]/\\u006D/g' -e 's/[g]/\\u0067/g' -e 's/[(]/\\u0028/g' > ${FILENAME}.json
