SHELL := cmd.exe
.SHELLFLAGS := /C

PYTHON := C:/Users/Aaron/AppData/Local/Programs/Python/Python312/python.exe
CC := C:/msys64/mingw64/bin/gcc.exe
MSYS_PATH := C:/msys64/mingw64/bin;C:/msys64/usr/bin
CFLAGS := -O3 -march=native -Wall -Wextra
TARGET := tsc_benchmark.exe
SOURCE := tsc_benchmark.c

.PHONY: all build run analyze clean

all: build

build: $(TARGET)

$(TARGET): $(SOURCE)
	set "PATH=$(MSYS_PATH);%PATH%" && "$(CC)" $(CFLAGS) -o "$(TARGET)" "$(SOURCE)"

run: build
	"$(PYTHON)" scripts\run_benchmarks.py

analyze:
	"$(PYTHON)" scripts\analyze_runs.py

clean:
	if exist "$(TARGET)" del /Q "$(TARGET)"
	if exist "__pycache__" rmdir /S /Q "__pycache__"
	if exist "scripts\__pycache__" rmdir /S /Q "scripts\__pycache__"
