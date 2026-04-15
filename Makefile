PYTHON ?= python3

.PHONY: open analyze summary

open:
	./scripts/run-task.sh analyze

analyze: open

summary:
	$(PYTHON) scripts/launcher.py summary
