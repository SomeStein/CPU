PYTHON ?= python3

.PHONY: open analyze summary package-macos-arm64 package-windows-x64

open:
	./scripts/run-task.sh analyze

analyze: open

summary:
	$(PYTHON) scripts/launcher.py summary

package-macos-arm64:
	$(PYTHON) scripts/package.py --type dmg

package-windows-x64:
	$(PYTHON) scripts/package.py --type msi
