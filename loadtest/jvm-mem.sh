#!/bin/bash
# actuator 로 JVM 메모리 영역별 사용량(MiB)을 출력한다. 사용: loadtest/jvm-mem.sh
B=${BASE_URL:-http://localhost:8081}
v() { curl -s "$B/actuator/metrics/$1${2:+?tag=$2}" | python3 -c "import sys,json;print(round(json.load(sys.stdin)['measurements'][0]['value']/1048576,1))" 2>/dev/null || echo "-"; }
echo "heap used/committed      : $(v jvm.memory.used area:heap) / $(v jvm.memory.committed area:heap) MiB (max $(v jvm.memory.max area:heap))"
echo "metaspace used/committed : $(v jvm.memory.used id:Metaspace) / $(v jvm.memory.committed id:Metaspace) MiB (max $(v jvm.memory.max id:Metaspace))"
echo "class space used         : $(v jvm.memory.used id:Compressed%20Class%20Space) MiB"
echo "code cache used          : $(v jvm.memory.used id:CodeCache) MiB"
echo "direct buffers used      : $(v jvm.buffer.memory.used id:direct) MiB"
echo "threads live/peak        : $(curl -s $B/actuator/metrics/jvm.threads.live | python3 -c "import sys,json;print(int(json.load(sys.stdin)['measurements'][0]['value']))") / $(curl -s $B/actuator/metrics/jvm.threads.peak | python3 -c "import sys,json;print(int(json.load(sys.stdin)['measurements'][0]['value']))")"
echo "hikari active/max        : $(curl -s "$B/actuator/metrics/hikaricp.connections.active" | python3 -c "import sys,json;print(int(json.load(sys.stdin)['measurements'][0]['value']))") / $(curl -s "$B/actuator/metrics/hikaricp.connections.max" | python3 -c "import sys,json;print(int(json.load(sys.stdin)['measurements'][0]['value']))")"
echo "gc pause (count,total s) : $(curl -s "$B/actuator/metrics/jvm.gc.pause" | python3 -c "import sys,json;m={x['statistic']:x['value'] for x in json.load(sys.stdin)['measurements']};print(int(m['COUNT']), round(m['TOTAL_TIME'],2))")"
