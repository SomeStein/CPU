PYTHON ?= python3

.PHONY: open analyze summary prepare-host readiness self-check package-portable

open:
	./scripts/run-task.sh analyze

analyze: open

summary:
	$(PYTHON) scripts/launcher.py summary

prepare-host:
	$(PYTHON) scripts/controller_api.py prepare-host --download-missing

readiness:
	$(PYTHON) scripts/controller_api.py readiness

self-check:
	$(PYTHON) scripts/controller_api.py self-check

package-portable:
	$(PYTHON) scripts/controller_api.py package-portable
