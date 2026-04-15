PYTHON ?= python3

.PHONY: build run analyze summary

build:
	./scripts/run-task.sh build

run:
	./scripts/run-task.sh run --profile balanced

analyze:
	./scripts/run-task.sh analyze

summary:
	$(PYTHON) scripts/launcher.py summary
