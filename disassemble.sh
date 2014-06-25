#!/bin/bash

# Usage: ./disassemble [class]

readonly JAVAP="javap"
readonly JAVAP_ARGS="-c -p -s -l -verbose"
readonly CLASS_PATH="build/testasm"
readonly DISASSEMBLED_PATH="dist/disassembled"

function disassemble {
  if [[ -n ${1} ]]; then
    local CMD=${JAVAP}" "${JAVAP_ARGS}" "${CLASS_PATH}/${1}

    if [[ ! -d "${DISASSEMBLED_PATH}" ]]; then
      mkdir -p ${DISASSEMBLED_PATH}
    fi

    echo "// "${CMD} > ${DISASSEMBLED_PATH}/${1}.disassembled
    ${CMD} >> ${DISASSEMBLED_PATH}/${1}.disassembled
  fi
}

if [[ -n "${1}" ]]; then
  disassemble ${1}
else
  readonly classes=("ParallelSqrt.class" "ParallelSqrtInner.class" "SequentialSqrt.class")
  for i in "${classes[@]}";
  do
    disassemble ${i}
  done
fi
