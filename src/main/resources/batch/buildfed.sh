#!/bin/sh
if [ "${AXWAY_HOME}" != "" ]
then
	JYTHON=${AXWAY_HOME}/apigateway/posix/bin/jython
	if [ -f "${JYTHON}" ]
	then
		CMD_HOME=`dirname $0`
		"${JYTHON}" "${CMD_HOME}/lib/buildfed.py" $*
	else
		echo "ERROR: Jython interpreter not found: ${JYTHON}"
	fi
else
	echo "ERROR: environment variable AXWAY_HOME not set"
fi
