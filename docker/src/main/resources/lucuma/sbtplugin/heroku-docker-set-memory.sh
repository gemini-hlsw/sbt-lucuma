# Only set heap if not already in JAVA_OPTS
if [[ "${JAVA_OPTS:-}" =~ -Xmx ]] || [[ "${JAVA_OPTS:-}" =~ -Xms ]] || [[ "${JAVA_OPTS:-}" =~ MaxRAMPercentage ]]; then
  echo "Custom heap settings detected in JAVA_OPTS, not overriding."
else
  # Detect cgroup version and read memory limit
  if [[ -f /sys/fs/cgroup/memory.max ]]; then
    limit_bytes=$(cat /sys/fs/cgroup/memory.max)
    echo "Detected cgroup v2: memory limit ${limit_bytes} bytes"
  elif [[ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]]; then
    limit_bytes=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
    echo "Detected cgroup v1: memory limit ${limit_bytes} bytes"
  else
    limit_bytes=$((DEFAULT_MAX_HEAP_MB * 1024 * 1024))
    echo "Cgroup memory limit not detected, using default ${DEFAULT_MAX_HEAP_MB} MB"
  fi

  if [[ "$limit_bytes" = "max" ]]; then
    echo "Dyno memory detected: MAX"

    echo "Using -XX:MaxRAMPercentage=${HEAP_PERCENT}.0 instead of Xmx/Xms - IGNORING lucumaDockerHeapSubtract SETTING"
    addJava "-XX:MaxRAMPercentage=${HEAP_PERCENT}.0"
  else
    limit_mb=$((limit_bytes / 1024 / 1024))

    echo "Dyno memory detected: ${limit_mb} MB"
    heap_mb=$((limit_mb * HEAP_PERCENT / 100))
    
    # Apply heap subtraction if configured
    if [[ $HEAP_SUBTRACT_MB -gt 0 ]]; then
      echo "Subtracting ${HEAP_SUBTRACT_MB} MB from heap size"
      heap_mb=$((heap_mb - HEAP_SUBTRACT_MB))
    fi

    # Enforce minimum cap
    if (( heap_mb < MIN_HEAP_MB )); then
      echo "Heap size below minimum, using ${MIN_HEAP_MB} MB instead"
      heap_mb=$MIN_HEAP_MB
    fi

    echo "Using -Xms${heap_mb}m -Xmx${heap_mb}m"

    addJava "-Xms${heap_mb}m"
    addJava "-Xmx${heap_mb}m"
  fi
fi