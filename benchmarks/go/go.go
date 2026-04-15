package main

import (
	"bufio"
	"encoding/json"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"
)

type caseData struct {
	CaseID         string
	Iterations     int
	ParallelChains int
	PriorityMode   string
	AffinityMode   string
	Warmup         bool
	RepeatIndex    int
}

type resultPayload struct {
	SchemaVersion          int            `json:"schema_version"`
	Implementation         string         `json:"implementation"`
	Language               string         `json:"language"`
	Variant                string         `json:"variant"`
	CaseID                 string         `json:"case_id"`
	Warmup                 bool           `json:"warmup"`
	RepeatIndex            int            `json:"repeat_index"`
	Iterations             int            `json:"iterations"`
	ParallelChains         int            `json:"parallel_chains"`
	LoopTripCount          int            `json:"loop_trip_count"`
	Remainder              int            `json:"remainder"`
	TimerKind              string         `json:"timer_kind"`
	ElapsedNs              int64          `json:"elapsed_ns"`
	NsPerIteration         float64        `json:"ns_per_iteration"`
	NsPerAdd               float64        `json:"ns_per_add"`
	ResultChecksum         string         `json:"result_checksum"`
	HostOS                 string         `json:"host_os"`
	HostArch               string         `json:"host_arch"`
	Pid                    int            `json:"pid"`
	Tid                    int            `json:"tid"`
	RequestedPriorityMode  string         `json:"requested_priority_mode"`
	RequestedAffinityMode  string         `json:"requested_affinity_mode"`
	AppliedPriorityMode    string         `json:"applied_priority_mode"`
	AppliedAffinityMode    string         `json:"applied_affinity_mode"`
	SchedulerNotes         string         `json:"scheduler_notes"`
	RuntimeName            string         `json:"runtime_name"`
	PlatformExtras         map[string]any `json:"platform_extras"`
}

var seedPairs = [][2]uint64{
	{1, 1},
	{3, 5},
	{8, 13},
	{21, 34},
	{55, 89},
	{144, 233},
	{377, 610},
	{987, 1597},
}

func main() {
	data := loadCase(os.Args)
	loopTripCount := data.Iterations / data.ParallelChains
	remainder := data.Iterations % data.ParallelChains
	start := time.Now()
	checksum := runGeneric(loopTripCount, remainder, data.ParallelChains)
	if data.ParallelChains == 4 {
		checksum = runFour(loopTripCount, remainder)
	} else if data.ParallelChains == 8 {
		checksum = runEight(loopTripCount, remainder)
	}
	elapsed := time.Since(start).Nanoseconds()
	payload := buildResult("go_optimized", "optimized", data, loopTripCount, remainder, elapsed, checksum)
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetEscapeHTML(false)
	_ = encoder.Encode(payload)
}

func runGeneric(loopTripCount int, remainder int, parallelChains int) uint64 {
	states := extendSeedPairs(parallelChains)
	for outer := 0; outer < loopTripCount; outer++ {
		for index := range states {
			states[index][0] += states[index][1]
			states[index][1] += states[index][0]
		}
	}
	for index := 0; index < remainder; index++ {
		states[0][0] += states[0][1]
		states[0][1] += states[0][0]
	}
	var checksum uint64
	for _, state := range states {
		checksum ^= state[0] ^ state[1]
	}
	return checksum
}

func runFour(loopTripCount int, remainder int) uint64 {
	seeds := extendSeedPairs(4)
	a0, b0 := seeds[0][0], seeds[0][1]
	a1, b1 := seeds[1][0], seeds[1][1]
	a2, b2 := seeds[2][0], seeds[2][1]
	a3, b3 := seeds[3][0], seeds[3][1]
	for index := 0; index < loopTripCount; index++ {
		a0 += b0; b0 += a0
		a1 += b1; b1 += a1
		a2 += b2; b2 += a2
		a3 += b3; b3 += a3
	}
	for index := 0; index < remainder; index++ {
		a0 += b0
		b0 += a0
	}
	return a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3
}

func runEight(loopTripCount int, remainder int) uint64 {
	seeds := extendSeedPairs(8)
	values := make([]uint64, 16)
	for index := 0; index < 8; index++ {
		values[index*2] = seeds[index][0]
		values[index*2+1] = seeds[index][1]
	}
	for outer := 0; outer < loopTripCount; outer++ {
		for index := 0; index < len(values); index += 2 {
			values[index] += values[index+1]
			values[index+1] += values[index]
		}
	}
	for index := 0; index < remainder; index++ {
		values[0] += values[1]
		values[1] += values[0]
	}
	var checksum uint64
	for _, value := range values {
		checksum ^= value
	}
	return checksum
}

func loadCase(args []string) caseData {
	if len(args) != 3 || args[1] != "--case-file" {
		panic("Usage: <binary> --case-file <path>")
	}
	handle, err := os.Open(args[2])
	if err != nil {
		panic(err)
	}
	defer handle.Close()
	parsed := map[string]string{}
	scanner := bufio.NewScanner(handle)
	for scanner.Scan() {
		line := scanner.Text()
		if line == "" || strings.HasPrefix(line, "#") || !strings.Contains(line, "=") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		parsed[strings.TrimSpace(parts[0])] = strings.TrimSpace(parts[1])
	}
	return caseData{
		CaseID:         parsed["case_id"],
		Iterations:     mustInt(parsed["iterations"], 0),
		ParallelChains: mustInt(parsed["parallel_chains"], 1),
		PriorityMode:   valueOr(parsed["priority_mode"], "high"),
		AffinityMode:   valueOr(parsed["affinity_mode"], "single_core"),
		Warmup:         parsed["warmup"] == "true",
		RepeatIndex:    mustInt(parsed["repeat_index"], 1),
	}
}

func extendSeedPairs(count int) [][2]uint64 {
	values := make([][2]uint64, 0, count)
	values = append(values, seedPairs...)
	for len(values) < count {
		left := values[len(values)-2][0] + values[len(values)-1][1]
		right := left + values[len(values)-1][0]
		values = append(values, [2]uint64{left, right})
	}
	return values[:count]
}

func buildResult(implementation string, variant string, data caseData, loopTripCount int, remainder int, elapsed int64, checksum uint64) resultPayload {
	totalAdds := data.Iterations * 2
	hostOS := runtime.GOOS
	if hostOS == "darwin" {
		hostOS = "macos"
	}
	hostArch := runtime.GOARCH
	if hostArch == "amd64" {
		hostArch = "x64"
	}
	return resultPayload{
		SchemaVersion:         1,
		Implementation:        implementation,
		Language:              "go",
		Variant:               variant,
		CaseID:                data.CaseID,
		Warmup:                data.Warmup,
		RepeatIndex:           data.RepeatIndex,
		Iterations:            data.Iterations,
		ParallelChains:        data.ParallelChains,
		LoopTripCount:         loopTripCount,
		Remainder:             remainder,
		TimerKind:             "time_since_ns",
		ElapsedNs:             elapsed,
		NsPerIteration:        divide(elapsed, data.Iterations),
		NsPerAdd:              divide(elapsed, totalAdds),
		ResultChecksum:        strconv.FormatUint(checksum, 10),
		HostOS:                hostOS,
		HostArch:              hostArch,
		Pid:                   os.Getpid(),
		Tid:                   0,
		RequestedPriorityMode: data.PriorityMode,
		RequestedAffinityMode: data.AffinityMode,
		AppliedPriorityMode:   "unsupported",
		AppliedAffinityMode:   ternary(data.AffinityMode == "single_core", "unsupported", "unchanged"),
		SchedulerNotes:        "Go benchmark uses controller-side best effort scheduling only",
		RuntimeName:           "go",
		PlatformExtras:        map[string]any{},
	}
}

func divide(value int64, divisor int) float64 {
	if divisor == 0 {
		return 0
	}
	return float64(value) / float64(divisor)
}

func mustInt(value string, fallback int) int {
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func valueOr(value string, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

func ternary(condition bool, truthy string, falsy string) string {
	if condition {
		return truthy
	}
	return falsy
}
